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
import io.pinecone.proto.*;
import io.pinecone.proto.Vector;
import io.pinecone.unsigned_indices_model.QueryResponseWithUnsignedIndices;
import io.pinecone.unsigned_indices_model.ScoredVectorWithUnsignedIndices;
import io.pinecone.unsigned_indices_model.VectorWithUnsignedIndices;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static dev.langchain4j.internal.Utils.randomUUID;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Comparator.comparingDouble;
import static java.util.stream.Collectors.toList;

/**
 * Represents a <a href="https://www.pinecone.io/">Pinecone</a> index as an embedding store.
 * Current implementation assumes the index uses the cosine distance metric.
 * Does not support storing {@link dev.langchain4j.data.document.Metadata} yet.
 */
public class PineconeEmbeddingStore implements EmbeddingStore<TextSegment> {

    private static final String DEFAULT_NAMESPACE = "default"; // do not change, will break backward compatibility!
    private static final String DEFAULT_METADATA_TEXT_KEY = "text_segment"; // do not change, will break backward compatibility!

    private final String nameSpace;
    private final String metadataTextKey;
    private final Index pineconeIndex;
    private final Pinecone pinecone;
    private final Consumer<List<VectorWithUnsignedIndices>> afterUpsertAction;

    /**
     * Creates an instance of PineconeEmbeddingStore.
     *
     * @param apiKey            The Pinecone API key.
     * @param environment       The environment (e.g., "northamerica-northeast1-gcp").
     * @param projectId         The ID of the project (e.g., "19a129b"). This is <b>not</b> a project name.
     *                          The ID can be found in the Pinecone URL: <a href="https://app.pinecone.io/organizations/.../projects/">...</a>...:{projectId}/indexes.
     * @param index             The name of the index (e.g., "test").
     * @param nameSpace         (Optional) Namespace. If not provided, "default" will be used.
     * @param metadataTextKey   (Optional) The key to find the text in the metadata. If not provided, "text_segment" will be used.
     * @param afterUpsertAction
     */
    public PineconeEmbeddingStore(String apiKey,
                                  String environment,
                                  String projectId,
                                  String index,
                                  String nameSpace,
                                  String metadataTextKey,
                                  Consumer<List<VectorWithUnsignedIndices>> afterUpsertAction) {


        this.pinecone = new Pinecone.Builder(apiKey).build();
        this.pineconeIndex = pinecone.getIndexConnection(index);
        this.nameSpace = nameSpace == null ? DEFAULT_NAMESPACE : nameSpace;
        this.metadataTextKey = metadataTextKey == null ? DEFAULT_METADATA_TEXT_KEY : metadataTextKey;
        this.afterUpsertAction = afterUpsertAction;
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

    private void addInternal(String id, Embedding embedding, TextSegment textSegment) {
        addAllInternal(singletonList(id), singletonList(embedding), textSegment == null ? null : singletonList(textSegment));
    }

    private void addAllInternal(List<String> ids, List<Embedding> embeddings, List<TextSegment> textSegments) {

        List<VectorWithUnsignedIndices> vectorList = new ArrayList<>();
        for (int i = 0; i < embeddings.size(); i++) {
            String id = ids.get(i);
            Embedding embedding = embeddings.get(i);


            VectorWithUnsignedIndices vector = new VectorWithUnsignedIndices(id, embedding.vectorAsList());


            if (textSegments != null) {
                Struct.Builder metadataStructBuilder = Struct.newBuilder()
                        .putFields(metadataTextKey, Value.newBuilder()
                                .setStringValue(textSegments.get(i).text())
                                .build())
                        .putAllFields(textSegments.get(i).metadata().asMap().entrySet()
                                .stream()
                                .collect(Collectors.toMap(Map.Entry::getKey, e -> Value.newBuilder().setStringValue(e.getValue()).build())));
                vector.setMetadata(metadataStructBuilder.build());
            }

            vectorList.add(vector);
        }

        pineconeIndex.upsert(vectorList, nameSpace);
        Optional.ofNullable(afterUpsertAction).ifPresent(action -> action.accept(vectorList));
    }

    @Override
    public List<EmbeddingMatch<TextSegment>> findRelevant(Embedding referenceEmbedding, int maxResults, double minScore) {


        QueryResponseWithUnsignedIndices matchedVectorIds = pineconeIndex.queryByVector(maxResults, referenceEmbedding.vectorAsList(), nameSpace);

        if (matchedVectorIds.getMatchesList().isEmpty()) {
            return emptyList();
        }
        List<String> ids = matchedVectorIds.getMatchesList().stream()
                        .map(ScoredVectorWithUnsignedIndices::getId)
                                .collect(toList());

        FetchResponse fetchResponse = pineconeIndex.fetch(ids, nameSpace);

        List<EmbeddingMatch<TextSegment>> matches = fetchResponse.getVectorsMap().values().stream()
                .map(vector -> toEmbeddingMatch(vector, referenceEmbedding))
                .filter(match -> match.score() >= minScore)
                .sorted(comparingDouble(EmbeddingMatch::score))
                .collect(toList());

        Collections.reverse(matches);

        return matches;
    }


    private EmbeddingMatch<TextSegment> toEmbeddingMatch(Vector vector, Embedding referenceEmbedding) {
        Struct metadataStruct = vector.getMetadata();

        Value textSegmentValue = metadataStruct
                .getFieldsMap()
                .get(metadataTextKey);

        boolean filterOutMetadataTextKey = true;
        Map<String, String> metadataMap = structToMap(metadataStruct, filterOutMetadataTextKey);
        Metadata metadata = Metadata.from(metadataMap);

        Embedding embedding = Embedding.from(vector.getValuesList());
        double cosineSimilarity = CosineSimilarity.between(embedding, referenceEmbedding);

        return new EmbeddingMatch<>(
                RelevanceScore.fromCosineSimilarity(cosineSimilarity),
                vector.getId(),
                embedding,
                textSegmentValue == null ? null : TextSegment.from(textSegmentValue.getStringValue(), metadata)
        );
    }

    private Map<String, String> structToMap(Struct struct, boolean filterOutMetadataTextKey) {
        Map<String, String> result = new HashMap<>();
        Map<String, Value> fields = struct.getFieldsMap();

        for (Map.Entry<String, Value> entry : fields.entrySet()) {
            if (filterOutMetadataTextKey && isMetadataTextKey(entry.getKey())) {
                continue;
            }
            result.put(entry.getKey(), entry.getValue().getStringValue());
        }

        return result;
    }

    private boolean isMetadataTextKey(String key) {
        return metadataTextKey.equals(key);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String apiKey;
        private String environment;
        private String projectId;
        private String index;
        private String nameSpace;
        private String metadataTextKey;
        private Consumer<List<VectorWithUnsignedIndices>> afterUpsertAction;

        /**
         * @param apiKey The Pinecone API key.
         */
        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /**
         * @param environment The environment (e.g., "northamerica-northeast1-gcp").
         */
        public Builder environment(String environment) {
            this.environment = environment;
            return this;
        }

        /**
         * @param projectId The ID of the project (e.g., "19a129b"). This is <b>not</b> a project name.
         *                  The ID can be found in the Pinecone URL: <a href="https://app.pinecone.io/organizations/.../projects/">...</a>...:{projectId}/indexes.
         */
        public Builder projectId(String projectId) {
            this.projectId = projectId;
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
        @SuppressWarnings("unused")
        public Builder metadataTextKey(String metadataTextKey) {
            this.metadataTextKey = metadataTextKey;
            return this;
        }

        public PineconeEmbeddingStore build() {
            return new PineconeEmbeddingStore(apiKey, environment, projectId, index, nameSpace, metadataTextKey, afterUpsertAction);
        }

        @SuppressWarnings("unused")
        public Builder afterUpsertAction(Consumer<List<VectorWithUnsignedIndices>> onUpsertAction) {
            this.afterUpsertAction = onUpsertAction;
            return this;
        }
    }
}
