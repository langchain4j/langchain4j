package dev.langchain4j.model.chat;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.batch.BatchId;
import dev.langchain4j.model.batch.BatchPage;
import dev.langchain4j.model.batch.BatchRequest;
import dev.langchain4j.model.batch.BatchResponse;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.jspecify.annotations.Nullable;

/**
 * A model interface for processing multiple chat requests asynchronously in a batch.
 *
 * <p>Batch processing typically offers significant cost reductions compared to real-time chat requests
 * and is ideal for large-scale, non-urgent conversational or instruction-following tasks.</p>
 *
 * @see BatchResponse
 * @see BatchId
 * @see BatchPage
 */
@Experimental
public interface BatchChatModel {

    /**
     * Creates a batch of chat requests and submits them for asynchronous processing.
     *
     * <p>The returned {@link BatchResponse} represents the status of the batch operation.
     * Use {@link #retrieve(BatchId)} to poll for completion and obtain the resulting chat responses.</p>
     *
     * @param request the list of chat requests to process in the batch
     * @return a {@link BatchResponse} representing the initial state of the batch operation
     */
    BatchResponse<ChatResponse> submit(BatchRequest<ChatRequest> request);

    /**
     * Retrieves the current state and results of a chat batch operation.
     *
     * <p>The response indicates whether the batch is still processing, completed successfully,
     * or failed. Clients should poll this method at intervals until the batch completes.</p>
     *
     * @param name the batch identifier obtained from {@link #submit(BatchRequest)}
     * @return a {@link BatchResponse} representing the current state of the chat batch operation
     */
    BatchResponse<ChatResponse> retrieve(BatchId name);

    /**
     * Cancels a chat batch operation that is currently pending or running.
     *
     * @param name the batch identifier to cancel
     */
    void cancel(BatchId name);

    /**
     * Lists chat batch jobs with optional pagination.
     *
     * @param pageSize  the maximum number of batch jobs to return; if null, uses server default
     * @param pageToken token for retrieving a specific page; if null, returns the first page
     * @return a {@link BatchPage} containing chat batch responses and pagination information
     */
    BatchPage<ChatResponse> list(@Nullable Integer pageSize, @Nullable String pageToken);
}
