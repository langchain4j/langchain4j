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

import java.io.Serializable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import java.util.function.ToDoubleFunction;

import java.util.stream.Collectors;

import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureTrue;

/**
 * An {@link EmbeddingStore} backed by an Oracle Coherence {@link NamedMap}.
 */
public class CoherenceEmbeddingStore
        implements EmbeddingStore<TextSegment> {
    /**
     * A {@link Comparator} to sort an {@link EmbeddingMatch} based on score, where the
     * highest score is first.
     */
    private static final Comparator<EmbeddingMatch<?>> COMPARATOR = reverseComparingDouble(EmbeddingMatch::score);

    /**
     * The {@link ValueExtractor} to extract the float vector from a {@link DocumentChunk}.
     */
    private static final ValueExtractor<DocumentChunk, Vector<float[]>> EXTRACTOR = ValueExtractor.of(DocumentChunk::vector);
    
    /**
     * The default {@link NamedMap} name.
     */
    public static final String DEFAULT_MAP_NAME = "documentChunks";

    /**
     * The {@link NamedMap} used to store the {@link DocumentChunk document chunks}.
     */
    protected final NamedMap<DocumentChunk.Id, DocumentChunk> documentChunks;

    protected final boolean forceNormalize;

    /**
     * Create an {@link CoherenceEmbeddingStore}.
     * <p>
     * This method is protected, instances of {@link CoherenceEmbeddingStore}
     * are created using the builder.
     *
     * @param namedMap        the {@link NamedMap} to contain the {@link DocumentChunk document chunks}
     * @param forceNormalize  {@code true} if this {@link CoherenceEmbeddingStore} should force call
     *                        {@link Embedding#normalize()} on embeddings when adding or searching
     */
    protected CoherenceEmbeddingStore(NamedMap<DocumentChunk.Id, DocumentChunk> namedMap, boolean forceNormalize) {
        this.documentChunks = namedMap;
        this.forceNormalize = forceNormalize;
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
    public List<String> addAll(List<Embedding> embeddings, List<TextSegment> embedded) {
        List<DocumentChunk.Id> keys = generateKeys(embeddings.size());
        addAllInternal(keys, embeddings, embedded);
        return keys.stream().map(DocumentChunk.Id::toString).collect(Collectors.toList());
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

        documentChunks.keySet().removeAll(ids.stream().map(DocumentChunk.Id::parse).collect(Collectors.toSet()));
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
        if (forceNormalize) {
            queryEmbedding.normalize();
        }
        double minScore = request.minScore();
        boolean checkMin = minScore != 0.0f;
        Filter<DocumentChunk> filter = CoherenceMetadataFilterMapper.map(request.filter());
        Float32Vector vector = new Float32Vector(queryEmbedding.vector());

        SimilaritySearch<DocumentChunk.Id, DocumentChunk, float[]> aggregator = new SimilaritySearch<>(EXTRACTOR, vector, request.maxResults());
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

            DocumentChunk.Id id = result.getKey();
            DocumentChunk chunk = result.getValue();

            boolean fHighlyRelevant = score > 0.75f;
            if (fHighlyRelevant && id.index() > 0) {
                DocumentChunk.Id idPrev = new DocumentChunk.Id(id.docId(), id.index() - 1);
                DocumentChunk chunkPrev = documentChunks.get(idPrev);
                if (chunkPrev != null) {
                    matches.add(embeddingMatch(score, idPrev, chunkPrev));
                }
            }

            matches.add(embeddingMatch(score, id, chunk));

            if (fHighlyRelevant) {
                DocumentChunk.Id idNext = new DocumentChunk.Id(id.docId(), id.index() + 1);
                DocumentChunk chunkNext = documentChunks.get(idNext);
                if (chunkNext != null) {
                    matches.add(embeddingMatch(score, idNext, chunkNext));
                }
            }

        }
        matches.sort(COMPARATOR);
        return new EmbeddingSearchResult<>(matches.size() > request.maxResults()
                                           ? matches.subList(0, request.maxResults())
                                           : matches);
    }

    /**
     * Perform a KNN search.
     *
     * @param vector  the vector to find the nearest neighbours to
     * @param k       the maximum number of neighbours to find
     *
     * @return  the search results
     */
    public List<QueryResult<DocumentChunk.Id, DocumentChunk>> search(float[] vector, int k) {
         return documentChunks.aggregate(new SimilaritySearch<>(DocumentChunk::vector, new Float32Vector(vector), k));
     }

    /**
     * Obtain the {@link NamedMap} used to store embeddings.
     *
     * @return the {@link NamedMap} used to store embeddings
     */
    public NamedMap<DocumentChunk.Id, DocumentChunk> getDocumentChunks() {
        return documentChunks;
    }

    /**
     * Returns {@code true} if this {@link CoherenceEmbeddingStore} will force
     * call {@link Embedding#normalize()} on embeddings when adding or searching.
     *
     * @return {@code true} if this {@link CoherenceEmbeddingStore} will force
     *         call {@link Embedding#normalize()} on embeddings when adding or searching
     */
    public boolean isForceNormalize() {
        return forceNormalize;
    }

/**
     * Add an embedding to the repository.
     *
     * @param id         the id of the {@link Embedding}
     * @param embedding  the {@link Embedding} to add
     * @param segment    an optional {@link TextSegment} to add with the embedding
     */
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
    private void addAllInternal(List<DocumentChunk.Id> ids, List<Embedding> embeddings, List<TextSegment> segments) {
        if (isNullOrEmpty(ids) || isNullOrEmpty(embeddings)) {
            Logger.info("Skipped adding empty embeddings");
            return;
        }

        boolean hasEmbedded = segments != null && !segments.isEmpty();

        ensureTrue(ids.size() == embeddings.size(), "ids size is not equal to embeddings size");
        if (hasEmbedded) {
            ensureTrue(embeddings.size() == segments.size(), "embeddings size is not equal to embedded size");
        }

        Map<DocumentChunk.Id, DocumentChunk> map = new HashMap<>();
        for (int i = 0; i < embeddings.size(); i++) {
            Embedding embedding = embeddings.get(i);
            TextSegment segment = hasEmbedded ? segments.get(i) : null;
            map.put(ids.get(i), createChunk(embedding, segment));
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
    private EmbeddingMatch<TextSegment> embeddingMatch(double score, DocumentChunk.Id id, DocumentChunk chunk) {
        String key = id.toString();
        String text = chunk.text();
        TextSegment segment = text == null ? null : new TextSegment(chunk.text(), getMetadata(chunk.metadata()));
        Vector<float[]> vector = chunk.vector();
        Embedding embedding = vector == null ? null : new Embedding(vector.get());

        return new EmbeddingMatch<>(score, key, embedding, segment);
    }

    private static Metadata getMetadata(Map<String, Object> mapMetadata) {
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
        if (forceNormalize) {
            embedding.normalize();
        }
        Float32Vector vector = new Float32Vector(embedding.vector());
        chunk.setVector(vector);
        return chunk;
    }

    /**
     * Generate a number of {@link DocumentChunk.Id} instances.
     *
     * @param size  the number of {@link DocumentChunk.Id} instances to create
     *
     * @return a list of {@link DocumentChunk.Id} instances
     */
    private List<DocumentChunk.Id> generateKeys(int size) {
        List<DocumentChunk.Id> keys = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            keys.add(new DocumentChunk.Id(new UUID().toString(), 0));
        }
        return keys;
    }

    /**
     * Create a {@link Comparator} that sorts a double in reverse order.
     *
     * @param keyExtractor  the function to use to extract a double value
     * @param <T>           the type of value the function extracts from
     *
     * @return the result of comparing two extracted doubles in reverse order
     */
    public static<T> Comparator<T> reverseComparingDouble(ToDoubleFunction<? super T> keyExtractor) {
        Objects.requireNonNull(keyExtractor);
        return (Comparator<T> & Serializable)
            (c1, c2) -> Double.compare(keyExtractor.applyAsDouble(c2), keyExtractor.applyAsDouble(c1));
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
        private boolean forceNormalize = false;

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
        public Builder forceNormalize(boolean f) {
            forceNormalize = f;
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
            return new CoherenceEmbeddingStore(map, forceNormalize);
        }
    }
}
