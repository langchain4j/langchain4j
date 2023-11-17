package dev.langchain4j.model.vertexai.internal;

import com.google.api.gax.core.CredentialsProvider;
import com.google.cloud.aiplatform.v1.*;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@SuperBuilder
public class VertexAiIndexEndpoint {

    private final String endpoint;
    private final String location;
    private final String project;
    private final String indexEndpointId;
    private final String indexId;
    @Getter(lazy = true)
    private final IndexEndpointServiceClient client = initClient();
    @Getter(lazy = true)
    private final IndexEndpoint indexEndpoint = initEndpoint();
    @Getter(lazy = true)
    private final IndexEndpointName indexEndpointName = initIndexEndpointName();
    @Getter(lazy = true)
    private final IndexServiceClient indexServiceClient = initIndexServiceClient();
    private final CredentialsProvider credentialsProvider;

    /**
     * Gets the public endpoint.
     *
     * @return the public endpoint
     */
    public String getPublicEndpoint() {
        return getIndexEndpoint().getPublicEndpointDomainName() + ":443";
    }

    /**
     * Deletes the embedding index.
     *
     * @param embeddingIndices the embedding indices
     */
    public void deleteEmbedding(List<String> embeddingIndices) {
        IndexName name = IndexName.of(project, location, indexId);

        final RemoveDatapointsRequest request = RemoveDatapointsRequest.newBuilder()
                .setIndex(name.toString())
                .addAllDatapointIds(embeddingIndices)
                .build();

        getIndexServiceClient().removeDatapoints(request);
    }

    /**
     * Upsert the embedding index.
     *
     * @param embeddingIndex the embedding index
     */
    public void upsertEmbedding(VertexAiEmbeddingIndex embeddingIndex) {
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

        getIndexServiceClient().upsertDatapoints(request);
    }

    /**
     * Gets the embedding index.
     *
     * @return the embedding index
     */
    private IndexEndpointName initIndexEndpointName() {
        return IndexEndpointName.newBuilder()
                .setIndexEndpoint(indexEndpointId)
                .setProject(project)
                .setLocation(location)
                .build();
    }

    /**
     * Initializes the index service client.
     *
     * @return the index service client
     */
    private IndexServiceClient initIndexServiceClient() {
        try {
            IndexServiceSettings.Builder serviceSettings = IndexServiceSettings
                    .newBuilder()
                    .setEndpoint(resolveEndpoint());
            if (credentialsProvider != null) {
                serviceSettings.setCredentialsProvider(credentialsProvider);
            }

            return IndexServiceClient.create(serviceSettings.build());
        } catch (IOException exception) {
            log.error("Failed to create IndexServiceClient", exception);
            throw new RuntimeException("Failed to create IndexServiceClient", exception);
        }
    }

    /**
     * Initializes the endpoint.
     *
     * @return the endpoint
     */
    private IndexEndpoint initEndpoint() {
        return getClient().getIndexEndpoint(getIndexEndpointName());
    }

    /**
     * Initializes the client.
     *
     * @return the client
     */
    private IndexEndpointServiceClient initClient() {
        try {
            IndexEndpointServiceSettings.Builder serviceSettings = IndexEndpointServiceSettings
                    .newBuilder()
                    .setEndpoint(resolveEndpoint());
            if (credentialsProvider != null) {
                serviceSettings.setCredentialsProvider(credentialsProvider);
            }

            return IndexEndpointServiceClient.create(serviceSettings.build());
        } catch (IOException exception) {
            log.error("Failed to create IndexEndpointServiceClient", exception);
            throw new RuntimeException("Failed to create IndexEndpointServiceClient", exception);
        }
    }

    public String resolveEndpoint() {
        return (StringUtils.isEmpty(endpoint))
                ? location + "-aiplatform.googleapis.com:443"
                : endpoint;
    }
}
