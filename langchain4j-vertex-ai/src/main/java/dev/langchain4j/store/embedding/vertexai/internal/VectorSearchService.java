package dev.langchain4j.store.embedding.vertexai.internal;

import com.google.api.gax.core.CredentialsProvider;
import com.google.cloud.aiplatform.v1.*;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Builder
public class VectorSearchService {

    @NonNull
    private final String endpoint;
    @NonNull
    private final String project;
    @NonNull
    private final String location;
    @NonNull
    private final String indexEndpointId;
    @NonNull
    private final String deployedIndexId;
    @NonNull
    private final String indexId;
    @NonNull
    private final GcpBlobService gcpBlobService;
    private final CredentialsProvider credentialsProvider;
    @Getter
    @Builder.Default
    private final boolean returnFullDatapoint = true;
    @Getter(lazy = true)
    private final MatchServiceSettings settings = initSettings();

    /**
     * Add a document to the index
     *
     * @param referenceEmbedding the embedding of the document
     * @param maxResults         the maximum number of results to return
     * @param minScore           the minimum score to return
     * @return the list of matching documents
     */
    public List<EmbeddingMatch<TextSegment>> findRelevant(Embedding referenceEmbedding, int maxResults, double minScore) {
        final IndexDatapoint datapoint = IndexDatapoint
                .newBuilder()
                .addAllFeatureVector(referenceEmbedding.vectorAsList())
                .build();
        final FindNeighborsRequest.Query query = FindNeighborsRequest.Query
                .newBuilder()
                .setDatapoint(datapoint)
                .setNeighborCount(maxResults)
                .build();
        final FindNeighborsRequest request = FindNeighborsRequest
                .newBuilder()
                .setIndexEndpoint(IndexEndpointName.of(project, location, indexEndpointId).toString())
                .setDeployedIndexId(deployedIndexId)
                .addQueries(query)
                .setReturnFullDatapoint(returnFullDatapoint)
                .build();

        try (MatchServiceClient client = MatchServiceClient.create(getSettings())) {
            final FindNeighborsResponse response = client.findNeighbors(request);
            return response.getNearestNeighborsList()
                    .stream()
                    .flatMap(neighbor -> neighbor.getNeighborsList().stream())
                    .filter(neighbor -> neighbor.getDistance() >= minScore)
                    .map(neighbor -> {
                        final String id = neighbor.getDatapoint().getDatapointId();
                        final String path = "documents/" + id;
                        final String content = gcpBlobService.download(path);
                        final VertexAiDocument document = VertexAiDocument.fromJson(content);
                        final IndexDatapoint resultDatapoint = neighbor.getDatapoint();
                        return new EmbeddingMatch<>(neighbor.getDistance(),
                                id,
                                Embedding.from(resultDatapoint.getFeatureVectorList()),
                                (document != null) ? document.toTextSegment() : null);
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Initialize the Vertex AI MatchServiceSettings
     *
     * @return the initialized MatchServiceSettings
     */
    private MatchServiceSettings initSettings() {
        try {
            final MatchServiceSettings.Builder settings = MatchServiceSettings
                    .newBuilder()
                    .setEndpoint(endpoint);
            if (credentialsProvider != null) {
                settings.setCredentialsProvider(credentialsProvider);
            }

            return settings.build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
