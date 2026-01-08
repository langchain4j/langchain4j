package dev.langchain4j.model.anthropic;

import java.util.List;

/**
 * Paginated list of batch jobs.
 *
 * @param batches list of batch responses (without full results)
 * @param hasMore true if there are more pages available
 * @param nextPageToken token to use for fetching the next page, or null if no more pages
 * @param <T> the type of individual results
 */
public record AnthropicBatchList<T>(List<AnthropicBatchResponse<T>> batches, boolean hasMore, String nextPageToken) {}
