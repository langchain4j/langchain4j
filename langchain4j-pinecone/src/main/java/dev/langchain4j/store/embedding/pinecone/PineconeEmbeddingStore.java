package dev.langchain4j.store.embedding.pinecone;

import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.CosineSimilarity;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.RelevanceScore;
import io.pinecone.clients.Index;
import io.pinecone.clients.Pinecone;
import io.pinecone.unsigned_indices_model.QueryResponseWithUnsignedIndices;
import io.pinecone.unsigned_indices_model.ScoredVectorWithUnsignedIndices;
import io.pinecone.unsigned_indices_model.VectorWithUnsignedIndices;
import org.openapitools.client.model.IndexList;
import org.openapitools.client.model.IndexModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.Utils.randomUUID;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.store.embedding.pinecone.PineconeHelper.metadataToStruct;
import static dev.langchain4j.store.embedding.pinecone.PineconeHelper.structToMetadata;
import static io.pinecone.commons.IndexInterface.buildUpsertVectorWithUnsignedIndices;
import static java.util.Collections.singletonList;
import static java.util.Comparator.comparingDouble;
import static java.util.stream.Collectors.toList;

/**
 * Represents a <a href="https://www.pinecone.io/">Pinecone</a> index as an embedding store.
 * <p>
 * Current implementation assumes the index uses the cosine distance metric.
 * <p>
 * <b>WARNING! There is a known <a href="https://github.com/langchain4j/langchain4j/issues/1948">bug</a>:
 * Pinecone stores all numbers as floating-point values,
 * which means {@link Integer} and {@link Long} values (e.g., 1746714878034235396) stored in {@link Metadata}
 * may be corrupted and returned as incorrect numbers!
 * Possible workaround: convert integer/double values to {@link String} before storing them in {@link Metadata}.
 * Please note that in this case metadata filtering might not work properly!</b>
 */
public class PineconeEmbeddingStore implements EmbeddingStore<TextSegment> {

    private static final Logger log = LoggerFactory.getLogger(PineconeEmbeddingStore.class);

    private static final String DEFAULT_NAMESPACE = "default"; // do not change, will break backward compatibility!
    private static final String DEFAULT_METADATA_TEXT_KEY = "text_segment"; // do not change, will break backward compatibility!

    private final Index index;
    private final String nameSpace;
    private final String metadataTextKey;

    /**
     * Creates an instance of PineconeEmbeddingStore.
     *
     * @param apiKey          The Pinecone API key.
     * @param index           The name of the index (e.g., "test").
     * @param nameSpace       (Optional) Namespace. If not provided, "default" will be used.
     * @param metadataTextKey (Optional) The key to find the text in the metadata. If not provided, "text_segment" will be used.
     * @param createIndex     (Optional) Configuration parameters to create an index, see {@link PineconeServerlessIndexConfig} and {@link PineconePodIndexConfig}
     * @param environment     (Deprecated) Please use @{@link Builder#createIndex(PineconeIndexConfig)}.
     * @param projectId       (Deprecated) Please use @{@link Builder#createIndex(PineconeIndexConfig)}.
     */
    public PineconeEmbeddingStore(String apiKey,
                                  String index,
                                  String nameSpace,
                                  String metadataTextKey,
                                  PineconeIndexConfig createIndex,
                                  String environment,
                                  String projectId) {
        Pinecone client = new Pinecone.Builder(apiKey).build();
        this.nameSpace = nameSpace == null ? DEFAULT_NAMESPACE : nameSpace;
        this.metadataTextKey = metadataTextKey == null ? DEFAULT_METADATA_TEXT_KEY : metadataTextKey;

        // create serverless index if not exist
        if (createIndex != null && !isIndexExist(client, index)) {
            createIndex.createIndex(client, index);
        }
        this.index = client.getIndexConnection(index);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String add(Embedding embedding) {
        String id = randomUUID();
        add(id, embedding);
        return id;
    }

    @Override
    public void add(String id, Embedding embedding) {
        addInternal(id, embedding, null);
    }

    @Override
    public String add(Embedding embedding, TextSegment textSegment) {
        String id = randomUUID();
        addInternal(id, embedding, textSegment);
        return id;
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings) {

        List<String> ids = embeddings.stream()
                .map(ignored -> randomUUID())
                .collect(toList());

        addAll(ids, embeddings, null);

        return ids;
    }

    @Override
    public void removeAll(Collection<String> ids) {
        ensureNotEmpty(ids, "ids");
        index.deleteByIds(new ArrayList<>(ids), nameSpace);
    }

    @Override
    public void removeAll() {
        index.deleteAll(nameSpace);
    }

    @Override
    public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request) {

        Embedding embedding = request.queryEmbedding();

        QueryResponseWithUnsignedIndices response;
        if (Objects.isNull(request.filter())) {
            response = index.queryByVector(request.maxResults(), embedding.vectorAsList(), nameSpace, true, true);
        } else {
            Struct metadataFilter = PineconeMetadataFilterMapper.map(request.filter());
            response = index.queryByVector(request.maxResults(), embedding.vectorAsList(), nameSpace, metadataFilter, true, true);
        }
        List<ScoredVectorWithUnsignedIndices> matchesList = response.getMatchesList();

        List<EmbeddingMatch<TextSegment>> matches = matchesList.stream()
                .map(indices -> toEmbeddingMatch(indices, embedding))
                .filter(match -> match.score() >= request.minScore())
                .sorted(comparingDouble(EmbeddingMatch::score))
                .collect(toList());

        Collections.reverse(matches);

        return new EmbeddingSearchResult<>(matches);

    }

    private void addInternal(String id, Embedding embedding, TextSegment textSegment) {
        addAll(singletonList(id), singletonList(embedding), textSegment == null ? null : singletonList(textSegment));
    }

    @Override
    public void addAll(List<String> ids, List<Embedding> embeddings, List<TextSegment> textSegments) {
        if (isNullOrEmpty(ids) || isNullOrEmpty(embeddings)) {
            log.info("Empty embeddings - no ops");
            return;
        }
        List<VectorWithUnsignedIndices> vectors = new ArrayList<>(embeddings.size());

        for (int i = 0; i < embeddings.size(); i++) {

            String id = ids.get(i);
            Embedding embedding = embeddings.get(i);

            Struct struct = null;
            if (textSegments != null) {
                TextSegment textSegment = textSegments.get(i);
                struct = metadataToStruct(textSegment, metadataTextKey);
            }

            vectors.add(buildUpsertVectorWithUnsignedIndices(id, embedding.vectorAsList(), null, null, struct));
        }

        index.upsert(vectors, nameSpace);
    }

    private boolean isIndexExist(Pinecone client, String index) {
        IndexList indexList = client.listIndexes();
        List<IndexModel> indexModels = indexList.getIndexes();

        return !isNullOrEmpty(indexModels) && indexModels.stream().anyMatch(indexModel -> indexModel.getName().equals(index));
    }

    private EmbeddingMatch<TextSegment> toEmbeddingMatch(ScoredVectorWithUnsignedIndices indices, Embedding referenceEmbedding) {
        Map<String, Value> filedsMap = indices.getMetadata().getFieldsMap();

        Value textSegmentValue = filedsMap.get(metadataTextKey);
        Metadata metadata = Metadata.from(structToMetadata(filedsMap, metadataTextKey));

        Embedding embedding = Embedding.from(indices.getValuesList());
        double cosineSimilarity = CosineSimilarity.between(embedding, referenceEmbedding);

        return new EmbeddingMatch<>(
                RelevanceScore.fromCosineSimilarity(cosineSimilarity),
                indices.getId(),
                embedding,
                textSegmentValue == null ? null : TextSegment.from(textSegmentValue.getStringValue(), metadata)
        );
    }

    public static class Builder {

        private String apiKey;
        private String index;
        private String nameSpace;
        private String metadataTextKey;
        private PineconeIndexConfig createIndex;
        @Deprecated(forRemoval = true)
        private String environment;
        @Deprecated(forRemoval = true)
        private String projectId;

        /**
         * @param apiKey The Pinecone API key.
         */
        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /**
         * @param index The name of the index (e.g., "test").
         */
        public Builder index(String index) {
            this.index = index;
            return this;
        }

        /**
         * @param nameSpace (Optional) Namespace. If not provided, "default" will be used.
         */
        public Builder nameSpace(String nameSpace) {
            this.nameSpace = nameSpace;
            return this;
        }

        /**
         * @param metadataTextKey (Optional) The key to find the text in the metadata. If not provided, "text_segment" will be used.
         */
        public Builder metadataTextKey(String metadataTextKey) {
            this.metadataTextKey = metadataTextKey;
            return this;
        }

        /**
         * @param createIndex (Optional) The key to find the text in the metadata. If not provided, "text_segment" will be used.
         */
        public Builder createIndex(PineconeIndexConfig createIndex) {
            this.createIndex = createIndex;
            return this;
        }

        /**
         * @param environment The environment (e.g., "northamerica-northeast1-gcp").
         * @deprecated Please use {@link Builder#createIndex(PineconeIndexConfig)}
         */
        @Deprecated(forRemoval = true)
        public Builder environment(String environment) {
            this.environment = environment;
            return this;
        }

        /**
         * @param projectId The ID of the project (e.g., "19a129b"). This is <b>not</b> a project name.
         *                  The ID can be found in the Pinecone URL: https://app.pinecone.io/organizations/.../projects/...:{projectId}/indexes.
         * @deprecated Please use {@link Builder#createIndex(PineconeIndexConfig)}
         */
        @Deprecated(forRemoval = true)
        public Builder projectId(String projectId) {
            this.projectId = projectId;
            return this;
        }

        public PineconeEmbeddingStore build() {
            return new PineconeEmbeddingStore(apiKey, index, nameSpace, metadataTextKey, createIndex, environment, projectId);
        }
    }
}
