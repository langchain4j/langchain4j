package dev.langchain4j.model.batch;

import dev.langchain4j.Experimental;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A unified interface for batch models that process multiple requests asynchronously.
 *
 * <p>Batch processing typically offers significant cost reductions compared to real-time requests
 * and is ideal for large-scale, non-urgent tasks.</p>
 *
 * @param <REQ> the type of input request (e.g., ChatRequest, TextSegment, ImageGenerationRequest)
 * @param <RES> the type of individual responses (e.g., ChatResponse, Embedding, Response&lt;Image&gt;)
 *
 * @see BatchResponse
 * @see BatchName
 * @see BatchList
 */
@Experimental
public interface BatchModel<REQ, RES> {

    /**
     * Creates a batch of requests and submits them for asynchronous processing.
     *
     * <p>The returned {@link BatchResponse} typically represents an incomplete state
     * (e.g., pending or running). Use {@link #retrieveResults(BatchName)} to poll
     * for completion and obtain results.</p>
     *
     * @param requests the list of requests to process in the batch
     * @return a {@link BatchResponse} representing the initial state of the batch operation
     */
    BatchResponse<RES> submit(List<REQ> requests);

    /**
     * Retrieves the current state and results of a batch operation.
     *
     * <p>The responses indicates whether the batch is still processing, completed successfully,
     * or failed. Clients should poll this method at intervals until the batch completes.</p>
     *
     * @param name the batch identifier obtained from {@link #submit(List)}
     * @return a {@link BatchResponse} representing the current state of the batch operation
     */
    BatchResponse<RES> retrieveResults(BatchName name);

    /**
     * Cancels a batch operation that is currently pending or running.
     *
     * <p>Cancellation may not be immediate; the batch may transition through intermediate
     * states before fully cancelling.</p>
     *
     * @param name the batch identifier to cancel
     */
    void cancelJob(BatchName name);

    /**
     * Lists batch jobs with optional pagination.
     *
     * @param pageSize  the maximum number of batch jobs to return; if null, uses server default
     * @param pageToken token for retrieving a specific page; if null, returns the first page
     * @return a {@link BatchList} containing batch responses and pagination information
     */
    BatchList<RES> listJobs(@Nullable Integer pageSize, @Nullable String pageToken);
}
