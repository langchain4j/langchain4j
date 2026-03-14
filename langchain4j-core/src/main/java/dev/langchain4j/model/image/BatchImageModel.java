package dev.langchain4j.model.image;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.model.batch.BatchId;
import dev.langchain4j.model.batch.BatchPage;
import dev.langchain4j.model.batch.BatchRequest;
import dev.langchain4j.model.batch.BatchResponse;
import dev.langchain4j.model.output.Response;
import org.jspecify.annotations.Nullable;

/**
 * A model interface for processing multiple image generation requests asynchronously in a batch.
 *
 * <p>Batch processing typically offers significant cost reductions compared to real-time requests
 * and is ideal for large-scale, non-urgent tasks.</p>
 *
 * @see BatchResponse
 * @see BatchId
 * @see BatchPage
 */
@Experimental
public interface BatchImageModel {

    /**
     * Creates a batch of image generation prompts and submits them for asynchronous processing.
     *
     * <p>The returned {@link BatchResponse} represents the status of the batch operation.
     * Use {@link #retrieve(BatchId)} to poll for completion and obtain the generated images.</p>
     *
     * @param request the list of image generation prompts or requests to process
     * @return a {@link BatchResponse} representing the initial state of the batch operation
     */
    BatchResponse<Response<Image>> submit(BatchRequest<String> request);

    /**
     * Retrieves the current state and results of an image generation batch operation.
     *
     * <p>The response indicates whether the batch is still processing, completed successfully,
     * or failed. Once completed, the response will contain the generated image data.</p>
     *
     * @param name the batch identifier obtained from {@link #submit(BatchRequest)}
     * @return a {@link BatchResponse} representing the current state of the image batch operation
     */
    BatchResponse<Response<Image>> retrieve(BatchId name);

    /**
     * Cancels an image generation batch operation that is currently pending or running.
     *
     * @param name the batch identifier to cancel
     */
    void cancel(BatchId name);

    /**
     * Lists image generation batch jobs with optional pagination.
     *
     * @param pageSize  the maximum number of batch jobs to return; if null, uses server default
     * @param pageToken token for retrieving a specific page; if null, returns the first page
     * @return a {@link BatchPage} containing image batch responses and pagination information
     */
    BatchPage<Response<Image>> list(@Nullable Integer pageSize, @Nullable String pageToken);
}
