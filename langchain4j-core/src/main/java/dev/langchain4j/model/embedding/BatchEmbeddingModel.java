package dev.langchain4j.model.embedding;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.batch.BatchId;
import dev.langchain4j.model.batch.BatchPage;
import dev.langchain4j.model.batch.BatchRequest;
import dev.langchain4j.model.batch.BatchResponse;
import org.jspecify.annotations.Nullable;

/**
 * Used for processing multiple embedding requests asynchronously in a batch.
 *
 * <p>Batch processing typically offers significant cost reductions compared to real-time requests
 * and is ideal for large-scale, non-urgent tasks.</p>
 *
 * @see BatchResponse
 * @see BatchId
 * @see BatchPage
 */
@Experimental
public interface BatchEmbeddingModel {

    /**
     * Creates a batch of text segments and submits them for asynchronous embedding processing.
     *
     * <p>The returned {@link BatchResponse} represents the status of the batch operation.
     * Use {@link #retrieve(BatchId)} to poll for completion and obtain the embeddings.</p>
     *
     * @param request the list of text segments to embed in the batch
     * @return a {@link BatchResponse} representing the initial state of the batch operation
     */
    BatchResponse<Embedding> submit(BatchRequest<TextSegment> request);

    /**
     * Retrieves the current state and results of an embedding batch operation.
     *
     * <p>The response indicates whether the batch is still processing, completed successfully,
     * or failed. Use this to retrieve the computed embeddings once the state is success.</p>
     *
     * @param name the batch identifier obtained from {@link #submit(BatchRequest)}
     * @return a {@link BatchResponse} representing the current state of the embedding batch operation
     */
    BatchResponse<Embedding> retrieve(BatchId name);

    /**
     * Cancels an embedding batch operation that is currently pending or running.
     *
     * @param name the batch identifier to cancel
     */
    void cancel(BatchId name);

    /**
     * Lists embedding batch jobs with optional pagination.
     *
     * @param pageSize  the maximum number of batch jobs to return; if null, uses server default
     * @param pageToken token for retrieving a specific page; if null, returns the first page
     * @return a {@link BatchPage} containing embedding batch responses and pagination information
     */
    BatchPage<Embedding> list(@Nullable Integer pageSize, @Nullable String pageToken);
}
