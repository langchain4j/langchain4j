package dev.langchain4j.store.embedding.pinecone;

import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.pinecone.PineconeClient;
import io.pinecone.PineconeClientConfig;
import io.pinecone.PineconeConnection;
import io.pinecone.PineconeConnectionConfig;
import io.pinecone.proto.*;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

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

    private final PineconeConnection connection;
    private final String nameSpace;
    private final String metadataTextKey;

    /**
     * Creates an instance of PineconeEmbeddingStore.
     *
     * @param apiKey          The Pinecone API key.
     * @param environment     The environment (e.g., "northamerica-northeast1-gcp").
     * @param projectId       The ID of the project (e.g., "19a129b"). This is <b>not</b> a project name.
     *                        The ID can be found in the Pinecone URL: https://app.pinecone.io/organizations/.../projects/...:{projectId}/indexes.
     * @param index           The name of the index (e.g., "test").
     * @param nameSpace       (Optional) Namespace. If not provided, "default" will be used.
     * @param metadataTextKey (Optional) The key to find the text in the metadata. If not provided, "text_segment" will be used.
     */
    public PineconeEmbeddingStore(String apiKey,
                                  String environment,
                                  String projectId,
                                  String index,
                                  String nameSpace,
                                  String metadataTextKey) {

        PineconeClientConfig configuration = new PineconeClientConfig()
                .withApiKey(apiKey)
                .withEnvironment(environment)
                .withProjectName(projectId);

        PineconeClient pineconeClient = new PineconeClient(configuration);

        PineconeConnectionConfig connectionConfig = new PineconeConnectionConfig()
                .withIndexName(index);

        this.connection = pineconeClient.connect(connectionConfig);
        this.nameSpace = nameSpace == null ? DEFAULT_NAMESPACE : nameSpace;
        this.metadataTextKey = metadataTextKey == null ? DEFAULT_METADATA_TEXT_KEY : metadataTextKey;
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

        UpsertRequest.Builder upsertRequestBuilder = UpsertRequest.newBuilder()
                .setNamespace(nameSpace);

        for (int i = 0; i < embeddings.size(); i++) {

            String id = ids.get(i);
            Embedding embedding = embeddings.get(i);

            Vector.Builder vectorBuilder = Vector.newBuilder()
                    .setId(id)
                    .addAllValues(embedding.vectorAsList());

            if (textSegments != null) {
                vectorBuilder.setMetadata(Struct.newBuilder()
                        .putFields(metadataTextKey, Value.newBuilder()
                                .setStringValue(textSegments.get(i).text())
                                .build()));
            }

            upsertRequestBuilder.addVectors(vectorBuilder.build());
        }

        connection.getBlockingStub().upsert(upsertRequestBuilder.build());
    }

    @Override
    public List<EmbeddingMatch<TextSegment>> findRelevant(Embedding referenceEmbedding, int maxResults, double minScore) {

        QueryRequest queryRequest = QueryRequest
                .newBuilder()
                .addAllVector(referenceEmbedding.vectorAsList())
                .setNamespace(nameSpace)
                .setTopK(maxResults)
                .build();

        List<String> matchedVectorIds = connection.getBlockingStub()
                .query(queryRequest)
                .getMatchesList()
                .stream()
                .map(ScoredVector::getId)
                .collect(toList());

        if (matchedVectorIds.isEmpty()) {
            return emptyList();
        }

        Collection<Vector> matchedVectors = connection.getBlockingStub().fetch(FetchRequest.newBuilder()
                        .addAllIds(matchedVectorIds)
                        .setNamespace(nameSpace)
                        .build())
                .getVectorsMap()
                .values();

        List<EmbeddingMatch<TextSegment>> matches = matchedVectors.stream()
                .map(vector -> toEmbeddingMatch(vector, referenceEmbedding))
                .filter(match -> match.score() >= minScore)
                .sorted(comparingDouble(EmbeddingMatch::score))
                .collect(toList());

        Collections.reverse(matches);

        return matches;
    }

    private EmbeddingMatch<TextSegment> toEmbeddingMatch(Vector vector, Embedding referenceEmbedding) {
        Value textSegmentValue = vector.getMetadata()
                .getFieldsMap()
                .get(metadataTextKey);

        Embedding embedding = Embedding.from(vector.getValuesList());

        return new EmbeddingMatch<>(
                (double) embedding.relevanceScore(referenceEmbedding),
                vector.getId(),
                embedding,
                textSegmentValue == null ? null : TextSegment.from(textSegmentValue.getStringValue())
        );
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
         *                  The ID can be found in the Pinecone URL: https://app.pinecone.io/organizations/.../projects/...:{projectId}/indexes.
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
        public Builder metadataTextKey(String metadataTextKey) {
            this.metadataTextKey = metadataTextKey;
            return this;
        }

        public PineconeEmbeddingStore build() {
            return new PineconeEmbeddingStore(apiKey, environment, projectId, index, nameSpace, metadataTextKey);
        }
    }
}
