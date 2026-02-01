package dev.langchain4j.model.batch;

import dev.langchain4j.Experimental;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Represents a paginated list of batch operations.
 *
 * <p>This record is returned by {@link BatchModel#listBatchJobs(Integer, String)} and contains
 * the batch operations for the current page along with pagination information for retrieving
 * additional pages.</p>
 *
 * @param <T> the type of the response payload in each batch (e.g., {@code List<ChatResponse>})
 * @param batches the list of batch responses for the current page
 * @param nextPageToken token to pass to {@link BatchModel#listBatchJobs(Integer, String)} to
 *                      retrieve the next page; may be {@code null} if no more pages exist
 *
 * @see BatchModel#listBatchJobs(Integer, String)
 * @see BatchResponse
 */
@Experimental
public record BatchList<T>(
        List<? extends BatchResponse<T>> batches,
        @Nullable String nextPageToken
) {
}
