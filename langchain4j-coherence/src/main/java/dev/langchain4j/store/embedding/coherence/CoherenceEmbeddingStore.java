package dev.langchain4j.store.embedding.coherence;

import com.oracle.coherence.ai.DocumentChunk;
import com.oracle.coherence.ai.Float32Vector;
import com.oracle.coherence.ai.QueryResult;
import com.oracle.coherence.ai.Vector;
import com.oracle.coherence.ai.VectorIndexExtractor;

import com.oracle.coherence.ai.search.SimilaritySearch;
import com.oracle.coherence.common.base.Logger;

import com.tangosol.internal.util.processor.CacheProcessors;
import com.tangosol.net.Coherence;
import com.tangosol.net.NamedMap;
import com.tangosol.net.Session;

import com.tangosol.util.Filter;
import com.tangosol.util.UUID;

import com.tangosol.util.ValueExtractor;
import dev.langchain4j.data.document.Metadata;

import dev.langchain4j.data.embedding.Embedding;

import dev.langchain4j.data.segment.TextSegment;

import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.RelevanceScore;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureTrue;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toSet;

/**
 * An {@link EmbeddingStore} backed by an Oracle Coherence {@link NamedMap}.
 */
public class CoherenceEmbeddingStore implements EmbeddingStore<TextSegment> {
    /**
     * The {@link ValueExtractor} to extract the float vector from a {@link DocumentChunk}.
     */
    private static final ValueExtractor<DocumentChunk, Vector<float[]>> EXTRACTOR =
        ValueExtractor.of(DocumentChunk::vector);
    
    /**
     * The default {@link NamedMap} name.
     */
    public static final String DEFAULT_MAP_NAME = "documentChunks";

    /**
     * The {@link NamedMap} used to store the {@link DocumentChunk document chunks}.
     */
    protected final NamedMap<DocumentChunk.Id, DocumentChunk> documentChunks;

    protected final boolean normalizeEmbeddings;

    /**
     * Create an {@link CoherenceEmbeddingStore}.
     * <p>
     * This method is protected, instances of {@link CoherenceEmbeddingStore}
     * are created using the builder.
     *
     * @param namedMap             the {@link NamedMap} to contain the {@link DocumentChunk document chunks}
     * @param normalizeEmbeddings  {@code true} if this {@link CoherenceEmbeddingStore} should call
     *                             {@link Embedding#normalize()} on embeddings when adding or searching
     */
    protected CoherenceEmbeddingStore(NamedMap<DocumentChunk.Id, DocumentChunk> namedMap,
                                      boolean normalizeEmbeddings) {
        this.documentChunks = namedMap;
        this.normalizeEmbeddings = normalizeEmbeddings;
    }

    @Override
    public String add(Embedding embedding) {
        DocumentChunk.Id id = DocumentChunk.id(new UUID().toString(), 0);
        addInternal(id, embedding, null);
        return id.toString();
    }

    @Override
    public void add(String id, Embedding embedding) {
        addInternal(DocumentChunk.Id.parse(id), embedding, null);
    }

    @Override
    public String add(Embedding embedding, TextSegment segment) {
        DocumentChunk.Id id = DocumentChunk.id(new UUID().toString(), 0);
        addInternal(id, embedding, segment);
        return id.toString();
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings) {
        return addAll(embeddings, null);
    }

    @Override
    public void remove(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id cannot be null or blank");
        }

        documentChunks.remove(DocumentChunk.Id.parse(id));
    }

    @Override
    public void removeAll(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("ids cannot be null or empty");
        }

        Set<DocumentChunk.Id> chunkIds = ids.stream()
            .map(DocumentChunk.Id::parse)
            .collect(toSet());

        documentChunks.keySet().removeAll(chunkIds);
    }

    @Override
    public void removeAll(dev.langchain4j.store.embedding.filter.Filter filter) {
        if (filter == null) {
            throw new IllegalArgumentException("filter cannot be null");
        }
        
        Filter<DocumentChunk> chunkFilter = CoherenceMetadataFilterMapper.map(filter);
        documentChunks.invokeAll(chunkFilter, CacheProcessors.removeBlind());
    }

    @Override
    public void removeAll() {
        documentChunks.truncate();
    }

    @Override
    public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request) {
        Embedding queryEmbedding = request.queryEmbedding();
        if (normalizeEmbeddings) {
            queryEmbedding.normalize();
        }
        double minScore = request.minScore();
        boolean checkMin = minScore != 0.0f;
        Filter<DocumentChunk> filter = CoherenceMetadataFilterMapper.map(request.filter());
        Float32Vector vector = new Float32Vector(queryEmbedding.vector());

        SimilaritySearch<DocumentChunk.Id, DocumentChunk, float[]> aggregator =
            new SimilaritySearch<>(EXTRACTOR, vector, request.maxResults());
        if (filter != null) {
            aggregator = aggregator.filter(filter);
        }
            
        List<QueryResult<DocumentChunk.Id, DocumentChunk>> results = documentChunks.aggregate(aggregator);
        List<EmbeddingMatch<TextSegment>> matches = new ArrayList<>();

        for (QueryResult<DocumentChunk.Id, DocumentChunk> result : results) {
            double score = RelevanceScore.fromCosineSimilarity(1.0f - result.getDistance());
            if (checkMin && score < minScore) {
                continue;
            }

            matches.add(embeddingMatch(score, result.getKey(), result.getValue()));
        }

        return new EmbeddingSearchResult<>(matches);
    }

    private void addInternal(DocumentChunk.Id id, Embedding embedding, TextSegment segment) {
        Map<DocumentChunk.Id, DocumentChunk> map = new HashMap<>();
        map.put(id, createChunk(embedding, segment));
        documentChunks.putAll(map);
    }

    /**
     * Add multiple {@link Embedding} instances to the repository.
     *
     * @param ids         the list of identifiers to use for each of the {@link Embedding}
     * @param embeddings  the {@link Embedding} to add
     * @param segments    an optional list of {@link TextSegment} to add with the embeddings
     */
    @Override
    public void addAll(List<String> ids, List<Embedding> embeddings,
                               List<TextSegment> segments) {
        if (isNullOrEmpty(ids) || isNullOrEmpty(embeddings)) {
            Logger.info("Skipped adding empty embeddings");
            return;
        }

        boolean hasEmbedded = segments != null && !segments.isEmpty();

        ensureTrue(ids.size() == embeddings.size(),
            "ids size is not equal to embeddings size");
        if (hasEmbedded) {
            ensureTrue(embeddings.size() == segments.size(),
                "embeddings size is not equal to embedded size");
        }

        Map<DocumentChunk.Id, DocumentChunk> map = new HashMap<>();
        for (int i = 0; i < embeddings.size(); i++) {
            Embedding embedding = embeddings.get(i);
            TextSegment segment = hasEmbedded ? segments.get(i) : null;
            final String id = ids.get(i);
            map.put(new DocumentChunk.Id(id, 0), createChunk(embedding, segment));
        }
        documentChunks.putAll(map);
    }

    /**
     * Convert a {@link QueryResult} into an {@link EmbeddingMatch}.
     *
     * @param score  the relevance score
     * @param id     the chunk ID
     * @param chunk  the matched chunk
     *
     * @return an {@link EmbeddingMatch} created from the {@link QueryResult}
     */
    private EmbeddingMatch<TextSegment> embeddingMatch(double score, DocumentChunk.Id id,
                                                       DocumentChunk chunk) {
        String key = id.toString();
        String text = chunk.text();
        TextSegment segment = text == null ? null : new TextSegment(chunk.text(), mapToMetadata(chunk.metadata()));
        Vector<float[]> vector = chunk.vector();
        Embedding embedding = vector == null ? null : new Embedding(vector.get());

        return new EmbeddingMatch<>(score, key, embedding, segment);
    }

    /**
     * Convert a {@code Map<String, Object>} of metadata attributes into a {@link Metadata} instance.
     *
     * @param mapMetadata  the map to convert
     *
     * @return a {@link Metadata} instance containing all non-null attributes from the specified Map
     */
    private static Metadata mapToMetadata(Map<String, Object> mapMetadata) {
        mapMetadata.entrySet().removeIf(entry -> entry.getValue() == null);
        return Metadata.from(mapMetadata);
    }

    /**
     * Create a {@link DocumentChunk} from an {@link Embedding} and
     * optional {@link TextSegment}.
     *
     * @param embedding  the {@link Embedding}
     * @param segment    the optional {@link TextSegment}
     *
     * @return a {@link DocumentChunk}
     */
    private DocumentChunk createChunk(Embedding embedding, TextSegment segment) {
        String text = segment == null ? null : segment.text();
        Map<String, Object> metadata = segment == null ? Collections.emptyMap() : segment.metadata().toMap();
        DocumentChunk chunk = new DocumentChunk(text, metadata);
        if (normalizeEmbeddings) {
            embedding.normalize();
        }
        Float32Vector vector = new Float32Vector(embedding.vector());
        chunk.setVector(vector);
        return chunk;
    }

    /**
     * Create a default {@link CoherenceEmbeddingStore}.
     *
     * @return a default {@link CoherenceEmbeddingStore}
     */
    public static CoherenceEmbeddingStore create() {
        return builder().build();
    }

    /**
     * Create a {@link CoherenceEmbeddingStore} that uses the
     * specified Coherence {@link NamedMap} name.
     *
     * @param name  the name of the Coherence {@link NamedMap} used to store documents
     *
     * @return a {@link CoherenceEmbeddingStore}
     */
    public static CoherenceEmbeddingStore create(String name) {
        return builder().name(name).build();
    }

    /**
     * Create a {@link CoherenceEmbeddingStore} that uses the
     * specified Coherence {@link NamedMap} name.
     *
     * @param map  the {@link NamedMap} used to store documents
     *
     * @return a {@link CoherenceEmbeddingStore}
     */
    public static CoherenceEmbeddingStore create(NamedMap<DocumentChunk.Id, DocumentChunk> map) {
        return new CoherenceEmbeddingStore(map, false);
    }

    /**
     * Return a {@link Builder} to use to build a {@link CoherenceEmbeddingStore}.
     *
     * @return  a {@link Builder} to use to build a {@link CoherenceEmbeddingStore}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * A builder to create {@link CoherenceEmbeddingStore} instances.
     */
    public static class Builder {
        /**
         * The name of the {@link NamedMap} to contain the {@link DocumentChunk document chunks}.
         */
        private String name = DEFAULT_MAP_NAME;

        /**
         * The name of the {@link Session} to use to obtain the {@link NamedMap}.
         */
        private String sessionName;

        /**
         * The {@link Session} to use to obtain the {@link NamedMap}.
         */
        private Session session;

        /**
         * The {@link VectorIndexExtractor} to use to create a vector index used to query the {@link NamedMap}.
         */
        private VectorIndexExtractor<DocumentChunk, Vector<?>> extractor;

        /**
         * A flag that when {@code true} forces normalization of embeddings on adding and searching
         */
        private boolean normalizeEmbeddings = false;

        /**
         * Create a {@link CoherenceEmbeddingStore.Builder}.
         */
        protected Builder() {
        }

        /**
         * Set the name of the {@link NamedMap} that will hold the
         * {@link DocumentChunk document chunks}.
         *
         * @param name  the name of the {@link NamedMap} that will hold
         *              the {@link DocumentChunk document chunks}
         *
         * @return this builder for fluent method calls
         */
        public Builder name(String name) {
            this.name = name == null || name.isEmpty() ? DEFAULT_MAP_NAME : name;
            return this;
        }

        /**
         * Set the name of the {@link Session} to use to obtain the
         * document chunk {@link NamedMap}.
         *
         * @param sessionName  the session name
         *
         * @return this builder for fluent method calls
         */
        public Builder session(String sessionName) {
            this.sessionName = sessionName;
            this.session = null;
            return this;
        }

        /**
         * Set the {@link Session} to use to obtain the
         * document chunk {@link NamedMap}.
         *
         * @param session  the {@link Session} to use
         *
         * @return this builder for fluent method calls
         */
        public Builder session(Session session) {
            this.session = session;
            this.sessionName = null;
            return this;
        }

        /**
         * Set the vector index to add to the underlying {@link NamedMap}.
         *
         * @param extractor  the {@link VectorIndexExtractor} to use to create the index
         *
         * @return this builder for fluent method calls
         */
        public Builder index(VectorIndexExtractor<DocumentChunk, Vector<?>> extractor) {
            this.extractor = extractor;
            return this;
        }

        /**
         * Set whether to force normalization of vectors on adding and searching.
         *
         * @param f  {@code true} if the {@link CoherenceEmbeddingStore} should call force
         *           {@link Embedding#normalize()} on embeddings when adding or searching
         *
         * @return this builder for fluent method calls
         */
        public Builder normalizeEmbeddings(boolean f) {
            normalizeEmbeddings = f;
            return this;
        }

        /**
         * Build a {@link CoherenceEmbeddingStore} from the state in this builder.
         *
         * @return a new instance of a {@link CoherenceEmbeddingStore}
         */
        public CoherenceEmbeddingStore build() {
            Session session = this.session;
            if (session == null) {
                if (sessionName != null) {
                    session = Coherence.getInstance().getSession(sessionName);
                }
                else {
                    session = Coherence.getInstance().getSession();
                }
            }
            NamedMap<DocumentChunk.Id, DocumentChunk> map = session.getMap(name);
            if (extractor != null) {
                map.addIndex(extractor);
            }
            return new CoherenceEmbeddingStore(map, normalizeEmbeddings);
        }
    }
}
