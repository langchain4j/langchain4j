package dev.langchain4j.store.embedding.pinecone;

import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.CosineSimilarity;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.RelevanceScore;
import io.pinecone.clients.Index;
import io.pinecone.clients.Pinecone;
import io.pinecone.unsigned_indices_model.QueryResponseWithUnsignedIndices;
import io.pinecone.unsigned_indices_model.ScoredVectorWithUnsignedIndices;
import io.pinecone.unsigned_indices_model.VectorWithUnsignedIndices;
import org.openapitools.client.model.IndexList;
import org.openapitools.client.model.IndexModel;

import java.util.*;

import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.Utils.randomUUID;
import static dev.langchain4j.store.embedding.pinecone.PineconeHelper.metadataToStruct;
import static dev.langchain4j.store.embedding.pinecone.PineconeHelper.structToMetadata;
import static io.pinecone.commons.IndexInterface.buildUpsertVectorWithUnsignedIndices;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Comparator.comparingDouble;
import static java.util.stream.Collectors.toList;

/**
 * Represents a <a href="https://www.pinecone.io/">Pinecone</a> index as an embedding store.
 * <p>Current implementation assumes the index uses the cosine distance metric.</p>
 */
public class PineconeEmbeddingStore implements EmbeddingStore<TextSegment> {

    private static final String DEFAULT_NAMESPACE = "default"; // do not change, will break backward compatibility!
    private static final String DEFAULT_METADATA_TEXT_KEY = "text_segment"; // do not change, will break backward compatibility!

    private final Index index;
    private final String nameSpace;
    private final String metadataTextKey;
    private final Map<String, Value.KindCase> metadataTypeMap;

    /**
     * Creates an instance of PineconeEmbeddingStore.
     *
     * @param apiKey          The Pinecone API key.
     * @param cloud           The cloud (e.g., "AWS").
     * @param region          The region (e.g., "us-east-1").
     * @param index           The name of the index (e.g., "test").
     * @param nameSpace       (Optional) Namespace. If not provided, "default" will be used.
     * @param metadataTextKey (Optional) The key to find the text in the metadata. If not provided, "text_segment" will be used.
     * @param metadataTypeMap (Optional) The remaining metadata type map to store metadata. If not provided, will not store metadata.
     * @param dimension       (Optional) The dimension of embedding aims to create index. If not provided, will not create index
     * @param createIndex     (Optional) Whether to create index or not. If not provided "false" will be used
     */
    public PineconeEmbeddingStore(String apiKey,
                                  String cloud,
                                  String region,
                                  String index,
                                  String nameSpace,
                                  String metadataTextKey,
                                  Map<String, Value.KindCase> metadataTypeMap,
                                  Integer dimension,
                                  Boolean createIndex) {
        Pinecone client = new Pinecone.Builder(apiKey).build();
        this.nameSpace = nameSpace == null ? DEFAULT_NAMESPACE : nameSpace;
        this.metadataTextKey = metadataTextKey == null ? DEFAULT_METADATA_TEXT_KEY : metadataTextKey;
        this.metadataTypeMap = metadataTypeMap;

        // create serverless index if not exist
        if (Boolean.TRUE.equals(createIndex) && index != null && !isIndexExist(client, index)) {
            client.createServerlessIndex(index, "cosine", dimension, cloud, region);
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

        addAllInternal(ids, embeddings, null);

        return ids;
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings, List<TextSegment> textSegments) {

        List<String> ids = embeddings.stream()
                .map(ignored -> randomUUID())
                .collect(toList());

        addAllInternal(ids, embeddings, textSegments);

        return ids;
    }

    @Override
    public void removeAll(Collection<String> ids) {
        index.deleteByIds(new ArrayList<>(ids), nameSpace);
    }

    @Override
    public void removeAll() {
        index.deleteAll(nameSpace);
    }

    private void addInternal(String id, Embedding embedding, TextSegment textSegment) {
        addAllInternal(singletonList(id), singletonList(embedding), textSegment == null ? null : singletonList(textSegment));
    }

    private void addAllInternal(List<String> ids, List<Embedding> embeddings, List<TextSegment> textSegments) {

        List<VectorWithUnsignedIndices> vectors = new ArrayList<>(embeddings.size());

        for (int i = 0; i < embeddings.size(); i++) {

            String id = ids.get(i);
            Embedding embedding = embeddings.get(i);

            Struct struct = null;
            if (textSegments != null) {
                TextSegment textSegment = textSegments.get(i);
                struct = metadataToStruct(textSegment, metadataTypeMap, metadataTextKey);
            }

            vectors.add(buildUpsertVectorWithUnsignedIndices(id, embedding.vectorAsList(), null, null, struct));
        }

        index.upsert(vectors, nameSpace);
    }

    @Override
    public List<EmbeddingMatch<TextSegment>> findRelevant(Embedding referenceEmbedding, int maxResults, double minScore) {

        QueryResponseWithUnsignedIndices response = index.queryByVector(maxResults, referenceEmbedding.vectorAsList(), nameSpace, true, true);
        List<ScoredVectorWithUnsignedIndices> matchesList = response.getMatchesList();

        if (matchesList.isEmpty()) {
            return emptyList();
        }

        List<EmbeddingMatch<TextSegment>> matches = matchesList.stream()
                .map(indices -> toEmbeddingMatch(indices, referenceEmbedding))
                .filter(match -> match.score() >= minScore)
                .sorted(comparingDouble(EmbeddingMatch::score))
                .collect(toList());

        Collections.reverse(matches);

        return matches;
    }

    private boolean isIndexExist(Pinecone client, String index) {
        IndexList indexList = client.listIndexes();
        List<IndexModel> indexModels = indexList.getIndexes();

        return !isNullOrEmpty(indexModels) && indexModels.stream().anyMatch(indexModel -> indexModel.getName().equals(index));
    }

    private EmbeddingMatch<TextSegment> toEmbeddingMatch(ScoredVectorWithUnsignedIndices indices, Embedding referenceEmbedding) {
        Map<String, Value> filedsMap = indices.getMetadata().getFieldsMap();

        Value textSegmentValue = filedsMap.get(metadataTextKey);
        Metadata metadata = new Metadata();
        if (metadataTypeMap != null && !metadataTypeMap.isEmpty()) {
            Map<String, Object> metadataMap = structToMetadata(metadataTypeMap, filedsMap);
            metadata = Metadata.from(metadataMap);
        }

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
        private String cloud;
        private String region;
        private String index;
        private String nameSpace;
        private String metadataTextKey;
        private Map<String, Value.KindCase> metadataTypeMap;
        private Integer dimension;
        private Boolean createIndex;

        /**
         * @param apiKey The Pinecone API key.
         */
        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /**
         * @param cloud The cloud (e.g., "AWS").
         */
        public Builder cloud(String cloud) {
            this.cloud = cloud;
            return this;
        }

        /**
         * @param region The region (e.g. "us-east-1")
         */
        public Builder region(String region) {
            this.region = region;
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
         * @param metadataTypeMap (Optional) The key to find the text in the metadata. If not provided, "text_segment" will be used.
         */
        public Builder metadataTypeMap(Map<String, Value.KindCase> metadataTypeMap) {
            this.metadataTypeMap = metadataTypeMap;
            return this;
        }

        /**
         * @param dimension (Optional) The key to find the text in the metadata. If not provided, "text_segment" will be used.
         */
        public Builder dimension(Integer dimension) {
            this.dimension = dimension;
            return this;
        }

        /**
         * @param createIndex (Optional) The key to find the text in the metadata. If not provided, "text_segment" will be used.
         */
        public Builder createIndex(Boolean createIndex) {
            this.createIndex = createIndex;
            return this;
        }

        public PineconeEmbeddingStore build() {
            return new PineconeEmbeddingStore(apiKey, cloud, region, index, nameSpace, metadataTextKey, metadataTypeMap, dimension, createIndex);
        }
    }
}
