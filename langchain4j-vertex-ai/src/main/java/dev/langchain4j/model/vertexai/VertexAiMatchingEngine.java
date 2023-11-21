package dev.langchain4j.model.vertexai;

import com.google.api.gax.core.CredentialsProvider;
import com.google.cloud.aiplatform.v1.*;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.common.collect.Lists;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.vertexai.internal.VertexAiEmbeddingIndex;
import dev.langchain4j.model.vertexai.internal.VertexAiIndexEndpoint;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static dev.langchain4j.internal.Utils.*;
import static dev.langchain4j.internal.ValidationUtils.ensureTrue;
import static java.util.Collections.singletonList;

/**
 * A matching engine that uses Vertex AI to store and match embeddings.
 */
@SuperBuilder
@Slf4j
public class VertexAiMatchingEngine implements EmbeddingStore<TextSegment> {

    private final String endpoint;
    @Getter(lazy = true)
    private final MatchServiceClient client = initMatchingClient();
    private final String bucketName;
    private final String project;
    private final String location;
    private final String indexEndpointId;
    private final String deployedIndexId;
    private final String indexId;
    @Getter(lazy = true)
    private final Bucket bucket = initBucket();
    @Getter(lazy = true)
    private final Storage storage = initStorage();
    @Getter(lazy = true)
    private final VertexAiIndexEndpoint indexEndpoint = initIndexEndpoint();
    private final CredentialsProvider credentialsProvider;
    @Builder.Default
    @Getter
    private final boolean avoidDups = true;

    @Override
    public String add(Embedding embedding) {
        return addAll(Lists.asList(embedding, new Embedding[]{})).get(0);
    }

    @Override
    public void add(String id, Embedding embedding) {
        addAll(Lists.asList(embedding, new Embedding[]{}));
    }

    @Override
    public String add(Embedding embedding, TextSegment textSegment) {
        return addAll(singletonList(embedding), singletonList(textSegment)).get(0);
    }

    /**
     * Adds all the embeddings to the index.
     *
     * @param embeddings A list of embeddings to be added to the store.
     * @return the ids of the embeddings
     */
    @Override
    public List<String> addAll(List<Embedding> embeddings) {
        return addAll(null, embeddings, null);
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings, List<TextSegment> embedded) {
        return addAll(null, embeddings, embedded);
    }

    public List<String> addAll(List<String> ids, List<Embedding> embeddings, List<TextSegment> embedded) {
        if (isCollectionEmpty(embeddings)) {
            log.info("Empty embeddings - no ops");
            return new ArrayList<>();
        }
        ensureTrue(embedded == null || embedded.size() == embeddings.size(), "embeddings size is not equal to embedded size");
        ensureTrue(ids == null || ids.size() == embeddings.size(), "ids size is not equal to embeddings size");

        final VertexAiEmbeddingIndex index = new VertexAiEmbeddingIndex();

        final String indexFileId = randomUUID();
        for (int embeddingIndex = 0; embeddingIndex < embeddings.size(); embeddingIndex++) {
            final Embedding embedding = embeddings.get(embeddingIndex);

            TextSegment textSegment;
            Metadata metadata;
            String id;
            if (embedded != null) {
                textSegment = embedded.get(embeddingIndex);
                metadata = textSegment.metadata();
                id = (ids != null)
                        ? ids.get(embeddingIndex)
                        : (avoidDups ? generateUUIDFrom(textSegment.text()) : randomUUID());
            } else {
                textSegment = null;
                metadata = null;
                id = (ids != null)
                        ? ids.get(embeddingIndex)
                        : randomUUID();
            }
            if (metadata == null) {
                metadata = Metadata.metadata("idxFileId", indexFileId);
            } else {
                metadata.add("idxFileId", indexFileId);
            }

            index.addEmbedding(id, embedding, metadata);

            if (textSegment != null) {
                upload(textSegment.text(), "documents/" + id);
            }
        }

        final String filename = "indexes/" + indexFileId + ".json";
        log.info("Uploading {} index to GCS.", filename);

        // Upload the index to GCS
        upload(index.toString(), filename);

        // Update the index
        getIndexEndpoint().upsertEmbedding(index);

        log.info("Uploaded {} index to GCS.", filename);

        return ids;
    }

    /**
     * Finds the most relevant text segments to the given text.
     *
     * @param referenceEmbedding The embedding used as a reference. Returned embeddings should be relevant (closest) to this one.
     * @param maxResults         The maximum number of embeddings to be returned.
     * @param minScore           The minimum relevance score, ranging from 0 to 1 (inclusive).
     *                           Only embeddings with a score of this value or higher will be returned.
     * @return the list of relevant text segments
     */
    @Override
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
                .build();

        final FindNeighborsResponse response = getClient().findNeighbors(request);
        return response.getNearestNeighborsList()
                .stream()
                .flatMap(neighbor -> neighbor.getNeighborsList().stream())
                .filter(neighbor -> neighbor.getDistance() > minScore)
                .map(neighbor -> {
                    final String id = neighbor.getDatapoint().getDatapointId();
                    final String path = "documents/" + id;
                    final Blob blob = getBucket().get(path);
                    final TextSegment segment = (blob.exists())
                            ? TextSegment.from(new String(blob.getContent(), StandardCharsets.UTF_8))
                            : null;
                    final IndexDatapoint resultDatapoint = neighbor.getDatapoint();
                    return new EmbeddingMatch<>(neighbor.getDistance(),
                            id,
                            Embedding.from(resultDatapoint.getFeatureVectorList()),
                            segment);
                })
                .collect(Collectors.toList());
    }

    /**
     * Uploads content to a specified path.
     *
     * @param content the content
     * @param path    the path
     */
    private void upload(final String content, final String path) {
        getBucket().create(path, content.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Initializes the storage.
     *
     * @return the storage
     */
    private Storage initStorage() {
        if (credentialsProvider != null) {
            try {
                return StorageOptions
                        .newBuilder()
                        .setCredentials(credentialsProvider.getCredentials())
                        .build()
                        .getService();
            } catch (IOException e) {
                log.error("Failed to create storage client.", e);
                throw new RuntimeException(e);
            }
        }

        return StorageOptions.getDefaultInstance().getService();
    }

    /**
     * Initializes the bucket.
     *
     * @return the bucket
     */
    private Bucket initBucket() {
        return getStorage().get(bucketName);
    }

    /**
     * Initializes the matching client.
     *
     * @return the matching client
     */
    private MatchServiceClient initMatchingClient() {
        try {
            final MatchServiceSettings.Builder settings = MatchServiceSettings
                    .newBuilder()
                    .setEndpoint(getIndexEndpoint().getPublicEndpoint());
            if (credentialsProvider != null) {
                settings.setCredentialsProvider(credentialsProvider);
            }

            return MatchServiceClient.create(settings.build());
        } catch (Exception e) {
            log.error("Failed to create matching client.", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Initializes the index endpoint.
     *
     * @return the index endpoint
     */
    private VertexAiIndexEndpoint initIndexEndpoint() {
        return VertexAiIndexEndpoint
                .builder()
                .endpoint(endpoint)
                .location(location)
                .project(project)
                .indexId(indexId)
                .indexEndpointId(indexEndpointId)
                .build();
    }
}
