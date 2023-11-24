package dev.langchain4j.store.embedding.vertexai;

import com.google.api.gax.core.CredentialsProvider;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.vertexai.internal.*;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static dev.langchain4j.internal.Utils.*;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.internal.ValidationUtils.ensureTrue;
import static java.util.Collections.singletonList;

/**
 * Vector search support for Google Vertex AI.
 */
@SuperBuilder
@Slf4j
public class VertexAiVectorSearch implements EmbeddingStore<TextSegment> {

    @NonNull
    private final String endpoint;
    @NonNull
    private final String bucketName;
    @NonNull
    private final String project;
    @NonNull
    private final String location;
    private final String indexEndpointId;
    private final String deployedIndexId;
    private final String indexId;
    private final CredentialsProvider credentialsProvider;
    @Getter
    @Builder.Default
    private final boolean avoidDups = true;
    @Getter
    @Builder.Default
    private final boolean returnFullDatapoint = true;

    @Getter(lazy = true)
    private final VectorSearchService vectorSearchService = initMatchingService();
    @Getter(lazy = true)
    private final GcpBlobService gcpBlobService = initBlob();
    @Getter(lazy = true)
    private final IndexEndpointService indexEndpointService = initIndexEndpoint();

    /**
     * Adds the embedding to the index.
     *
     * @param embedding The embedding to be added to the store.
     * @return the id of the embedding
     */
    @Override
    public String add(Embedding embedding) {
        return addAll(singletonList(embedding)).get(0);
    }

    /**
     * Adds the embedding to the index.
     *
     * @param id        The id of the embedding.
     * @param embedding The embedding to be added to the store.
     */
    @Override
    public void add(String id, Embedding embedding) {
        addAll(singletonList(id), singletonList(embedding), null);
    }

    /**
     * Adds the embedding to the index.
     *
     * @param embedding   The embedding to be added to the store.
     * @param textSegment The text segment to be added to the store.
     * @return the id of the embedding
     */
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

    /**
     * Adds all the embeddings to the index.
     *
     * @param embeddings A list of embeddings to be added to the store.
     * @param embedded   A list of text segments to be added to the store.
     * @return the ids of the embeddings
     */
    @Override
    public List<String> addAll(List<Embedding> embeddings, List<TextSegment> embedded) {
        return addAll(null, embeddings, embedded);
    }

    /**
     * Adds all the embeddings to the index.
     *
     * @param ids        the ids of the embeddings
     * @param embeddings A list of embeddings to be added to the store.
     * @param embedded   A list of text segments to be added to the store.
     * @return the ids of the embeddings
     */
    public List<String> addAll(List<String> ids, List<Embedding> embeddings, List<TextSegment> embedded) {
        if (isCollectionEmpty(embeddings)) {
            log.info("Empty embeddings - no ops");
            return new ArrayList<>();
        }
        ensureTrue(embedded == null || embedded.size() == embeddings.size(), "embeddings size is not equal to embedded size");
        ensureTrue(ids == null || ids.size() == embeddings.size(), "ids size is not equal to embeddings size");

        final VertexAiEmbeddingIndex index = new VertexAiEmbeddingIndex();
        final List<String> insertedIds = new ArrayList<>();
        final String indexFileId = randomUUID();

        for (int embeddingIndex = 0; embeddingIndex < embeddings.size(); embeddingIndex++) {
            final Embedding embedding = embeddings.get(embeddingIndex);

            TextSegment textSegment;
            String id;
            if (embedded != null) {
                textSegment = embedded.get(embeddingIndex);
                id = (ids != null)
                        ? ids.get(embeddingIndex)
                        : (avoidDups ? generateUUIDFrom(textSegment.text()) : randomUUID());
            } else {
                textSegment = null;
                id = (ids != null)
                        ? ids.get(embeddingIndex)
                        : randomUUID();
            }

            // Add the embedding to the index
            index.addEmbedding(id, embedding);
            insertedIds.add(id);

            // Upload the document to GCS
            getGcpBlobService().upload("documents/" + id, new VertexAIDocument(indexFileId, textSegment));
        }

        final String filename = "indexes/" + indexFileId + ".json";
        log.info("Uploading {} index to GCS.", filename);

        // Upload the index to GCS
        getGcpBlobService().upload(filename, index.toString());

        // Update the index
        getIndexEndpointService().upsertEmbedding(index);

        log.info("Uploaded {} index to GCS.", filename);

        return insertedIds;
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
        return getVectorSearchService().findRelevant(referenceEmbedding, maxResults, minScore);
    }

    /**
     * Deletes the index.
     *
     * @param index the index
     */
    public void deleteIndex(final String index) {
        // Delete index and all documents
        final String path = "indexes/" + index + ".json";
        final String json = getGcpBlobService().download(path);
        if (json != null) {
            final VertexAiEmbeddingIndex embeddingIndex = VertexAiEmbeddingIndex.fromJson(json);
            final List<String> indicesToDelete = embeddingIndex
                    .getRecords()
                    .stream()
                    .map(VertexAiEmbeddingIndexRecord::getId)
                    .collect(Collectors.toList());

            // Delete documents
            indicesToDelete.forEach(id -> getGcpBlobService().delete("documents/" + id));

            // Delete index
            getIndexEndpointService().deleteIndices(indicesToDelete);
        }

        // Delete index file
        getGcpBlobService().delete(path);
    }

    /**
     * Get all indices.
     *
     * @return the list of indices
     */
    public List<String> allIndices() {
        final String prefix = "indexes/";

        return getGcpBlobService()
                .list()
                .filter(name -> name.startsWith(prefix))
                .map(name -> name.substring(prefix.length(), name.length() - 5))
                .collect(Collectors.toList());
    }

    /**
     * Initializes the storage.
     *
     * @return the storage
     */
    private GcpBlobService initBlob() {
        ensureNotNull(bucketName, "bucketName is null");
        ensureNotNull(project, "project is null");

        return GcpBlobService.builder()
                .bucketName(bucketName)
                .credentialsProvider(credentialsProvider)
                .project(project)
                .build();
    }

    /**
     * Initializes the matching service.
     *
     * @return the matching service
     */
    private VectorSearchService initMatchingService() {
        ensureNotNull(deployedIndexId, "deployedIndexId is null");
        ensureNotNull(indexId, "indexId is null");
        ensureNotNull(indexEndpointId, "indexEndpointId is null");
        ensureNotNull(project, "project is null");
        ensureNotNull(location, "location is null");

        return VectorSearchService.builder()
                .gcpBlobService(getGcpBlobService())
                .deployedIndexId(deployedIndexId)
                .credentialsProvider(credentialsProvider)
                .returnFullDatapoint(returnFullDatapoint)
                .indexEndpointId(indexEndpointId)
                .indexId(indexId)
                .project(project)
                .location(location)
                .endpoint(getIndexEndpointService().getPublicEndpoint())
                .build();
    }

    /**
     * Initializes the index endpoint.
     *
     * @return the index endpoint
     */
    private IndexEndpointService initIndexEndpoint() {
        ensureNotNull(endpoint, "endpoint is null");
        ensureNotNull(location, "location is null");
        ensureNotNull(project, "project is null");

        return IndexEndpointService
                .builder()
                .endpoint(endpoint)
                .location(location)
                .project(project)
                .indexId(indexId)
                .indexEndpointId(indexEndpointId)
                .build();
    }
}
