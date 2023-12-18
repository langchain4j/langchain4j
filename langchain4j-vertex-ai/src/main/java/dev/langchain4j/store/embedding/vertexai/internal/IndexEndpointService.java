package dev.langchain4j.store.embedding.vertexai.internal;

import com.google.api.gax.core.CredentialsProvider;
import com.google.cloud.aiplatform.v1.*;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import static dev.langchain4j.internal.Utils.isNullOrBlank;

@Slf4j
@SuperBuilder
public class IndexEndpointService {

    @NonNull
    private final String endpoint;
    @NonNull
    private final String location;
    @NonNull
    private final String project;
    private final String indexEndpointId;
    private final String indexId;
    @Getter(lazy = true)
    private final IndexServiceSettings indexServiceSettings = initIndexServiceClientSettings();
    private final CredentialsProvider credentialsProvider;
    @Getter(lazy = true)
    private final String publicEndpoint = initPublicEndpoint();

    /**
     * Deletes the embedding index.
     *
     * @param embeddingIndices the embedding indices
     */
    public void deleteIndices(List<String> embeddingIndices) {
        if (isNullOrBlank(indexId)) {
            throw new IllegalArgumentException("Index is not specified.");
        }

        IndexName name = IndexName.of(project, location, indexId);

        final RemoveDatapointsRequest request = RemoveDatapointsRequest.newBuilder()
                .setIndex(name.toString())
                .addAllDatapointIds(embeddingIndices)
                .build();

        try (IndexServiceClient indexServiceClient = IndexServiceClient.create(getIndexServiceSettings())) {
            indexServiceClient.removeDatapoints(request);
        } catch (IOException exception) {
            log.error("Failed to create IndexServiceClient", exception);
            throw new RuntimeException("Failed to create IndexServiceClient", exception);
        }
    }

    /**
     * Upsert the embedding index.
     *
     * @param embeddingIndex the embedding index
     */
    public void upsertEmbedding(VertexAiEmbeddingIndex embeddingIndex) {
        if (isNullOrBlank(indexId)) {
            throw new IllegalArgumentException("Index is not specified.");
        }

        IndexName name = IndexName.of(project, location, indexId);

        final List<IndexDatapoint> dataPoints = embeddingIndex
                .getRecords()
                .stream()
                .map(record -> IndexDatapoint.newBuilder()
                        .setDatapointId(record.getId())
                        .addAllFeatureVector(record.getEmbedding())
                        .build())
                .collect(Collectors.toList());

        UpsertDatapointsRequest request = UpsertDatapointsRequest
                .newBuilder()
                .addAllDatapoints(dataPoints)
                .setIndex(name.toString())
                .build();

        try (IndexServiceClient indexServiceClient = IndexServiceClient.create(getIndexServiceSettings())) {
            indexServiceClient.upsertDatapoints(request);
        } catch (IOException exception) {
            log.error("Failed to create IndexServiceClient", exception);
            throw new RuntimeException("Failed to create IndexServiceClient", exception);
        }
    }

    /**
     * Gets the public endpoint.
     *
     * @return the public endpoint
     */
    private String initPublicEndpoint() {
        final IndexEndpointName indexEndpointName = IndexEndpointName.newBuilder()
                .setIndexEndpoint(indexEndpointId)
                .setProject(project)
                .setLocation(location)
                .build();

        final IndexEndpointServiceSettings.Builder serviceSettings = IndexEndpointServiceSettings
                .newBuilder()
                .setEndpoint(resolveEndpoint());
        if (credentialsProvider != null) {
            serviceSettings.setCredentialsProvider(credentialsProvider);
        }

        try (IndexEndpointServiceClient client = IndexEndpointServiceClient.create(serviceSettings.build())) {
            return client.getIndexEndpoint(indexEndpointName).getPublicEndpointDomainName() + ":443";
        } catch (IOException exception) {
            log.error("Failed to create IndexEndpointServiceClient", exception);
            throw new RuntimeException("Failed to create IndexEndpointServiceClient", exception);
        }
    }

    /**
     * Initializes the index service client settings.
     *
     * @return the client settings
     */
    private IndexServiceSettings initIndexServiceClientSettings() {
        try {
            IndexServiceSettings.Builder serviceSettings = IndexServiceSettings
                    .newBuilder()
                    .setEndpoint(resolveEndpoint());
            if (credentialsProvider != null) {
                serviceSettings.setCredentialsProvider(credentialsProvider);
            }

            return serviceSettings.build();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Resolves the endpoint.
     *
     * @return the endpoint
     */
    public String resolveEndpoint() {
        return (isNullOrBlank(endpoint))
                ? location + "-aiplatform.googleapis.com:443"
                : endpoint;
    }
}
