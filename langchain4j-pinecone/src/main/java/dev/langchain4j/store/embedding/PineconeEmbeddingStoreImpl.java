package dev.langchain4j.store.embedding;

import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import io.pinecone.PineconeClient;
import io.pinecone.PineconeClientConfig;
import io.pinecone.PineconeConnection;
import io.pinecone.PineconeConnectionConfig;
import io.pinecone.proto.*;
import lombok.Builder;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

public class PineconeEmbeddingStoreImpl implements EmbeddingStore<TextSegment> {

    private static final String DEFAULT_NAMESPACE = "default"; // do not change, will break backward compatibility!
    private static final String METADATA_TEXT_SEGMENT = "text_segment"; // do not change, will break backward compatibility!

    private final PineconeConnection connection;
    private final String nameSpace;

    @Builder
    public PineconeEmbeddingStoreImpl(String apiKey, String environment, String projectName, String index, String nameSpace) {

        PineconeClientConfig configuration = new PineconeClientConfig()
                .withApiKey(apiKey)
                .withEnvironment(environment)
                .withProjectName(projectName);

        PineconeClient pineconeClient = new PineconeClient(configuration);

        PineconeConnectionConfig connectionConfig = new PineconeConnectionConfig()
                .withIndexName(index);

        this.connection = pineconeClient.connect(connectionConfig);
        this.nameSpace = nameSpace == null ? DEFAULT_NAMESPACE : nameSpace;
    }

    @Override
    public String add(Embedding embedding) {
        String id = generateRandomId(embedding);
        add(id, embedding);
        return id;
    }

    @Override
    public void add(String id, Embedding embedding) {
        addInternal(id, embedding, null);
    }

    @Override
    public String add(Embedding embedding, TextSegment textSegment) {
        String id = generateRandomId(embedding);
        addInternal(id, embedding, textSegment);
        return id;
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings) {

        List<String> ids = embeddings.stream()
                .map(PineconeEmbeddingStoreImpl::generateRandomId)
                .collect(toList());

        addAllInternal(ids, embeddings, null);

        return ids;
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings, List<TextSegment> textSegments) {

        List<String> ids = embeddings.stream()
                .map(PineconeEmbeddingStoreImpl::generateRandomId)
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
            TextSegment textSegment = textSegments.get(i);

            Struct vectorMetadata = Struct.newBuilder()
                    .putFields(METADATA_TEXT_SEGMENT, Value.newBuilder()
                            .setStringValue(textSegment.text())
                            .build())
                    .build();

            Vector vector = Vector.newBuilder()
                    .setId(id)
                    .addAllValues(embedding.vectorAsList())
                    .setMetadata(vectorMetadata)
                    .build();

            upsertRequestBuilder.addVectors(vector);
        }

        connection.getBlockingStub().upsert(upsertRequestBuilder.build());
    }

    @Override
    public List<EmbeddingMatch<TextSegment>> findRelevant(Embedding referenceEmbedding, int maxResults) {
        return findRelevant(referenceEmbedding, maxResults, -1); // TODO check -1
    }

    @Override
    public List<EmbeddingMatch<TextSegment>> findRelevant(Embedding referenceEmbedding, int maxResults, double minSimilarity) {

        QueryVector queryVector = QueryVector
                .newBuilder()
                .addAllValues(referenceEmbedding.vectorAsList())
                .setTopK(maxResults)
                .setNamespace(nameSpace)
                .build();

        QueryRequest queryRequest = QueryRequest
                .newBuilder()
                .addQueries(queryVector)
                .setTopK(maxResults)
                .build();

        List<String> matchedVectorIds = connection.getBlockingStub()
                .query(queryRequest)
                .getResultsList()
                .get(0)
                .getMatchesList()
                .stream()
                .map(ScoredVector::getId)
                .collect(toList());

        if (matchedVectorIds.isEmpty()) {
            return emptyList();
        }

        // TODO take minSimilarity into account
        Collection<Vector> matchedVectors = connection.getBlockingStub().fetch(FetchRequest.newBuilder()
                        .addAllIds(matchedVectorIds)
                        .setNamespace(nameSpace)
                        .build())
                .getVectorsMap()
                .values();

        return matchedVectors.stream()
                .map(PineconeEmbeddingStoreImpl::toEmbeddingMatch)
                .collect(toList());
    }

    private static EmbeddingMatch<TextSegment> toEmbeddingMatch(Vector vector) {
        Value textSegmentValue = vector.getMetadata()
                .getFieldsMap()
                .get(METADATA_TEXT_SEGMENT);

        return new EmbeddingMatch<>(
                vector.getId(),
                Embedding.from(vector.getValuesList()),
                createTextSegmentIfExists(textSegmentValue),
                null); // TODO
    }

    private static TextSegment createTextSegmentIfExists(Value textSegmentValue) {
        if (textSegmentValue == null) {
            return null;
        }

        return TextSegment.from(textSegmentValue.getStringValue());
    }

    private static String generateRandomId(Embedding embedding) {
        return UUID.randomUUID().toString();
    }
}
