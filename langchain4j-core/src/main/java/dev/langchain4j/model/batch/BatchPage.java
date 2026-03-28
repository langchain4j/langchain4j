package dev.langchain4j.model.batch;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.chat.BatchChatModel;
import dev.langchain4j.model.embedding.BatchEmbeddingModel;
import dev.langchain4j.model.image.BatchImageModel;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Represents a set of batch jobs that is potentially paginated.
 *
 * <p>A {@code BatchPage} contains the batch operations for the current page. If more jobs are available,
 * the {@code nextPageToken} can be used to request the next page of results.</p>
 *
 * @param <T>           the type of the responses payload in each batch (e.g., {@code ChatResponse}, {@code Embedding})
 * @param batches       the list of batch responses for the current page
 * @param nextPageToken the token to pass to {@code listJobs} methods to retrieve the next page;
 *                      if present, it signifies more results are available. May be {@code null}
 *                      if no more pages exist.
 *
 * @see BatchChatModel#list(Integer, String)
 * @see BatchEmbeddingModel#list(Integer, String)
 * @see BatchImageModel#list(Integer, String)
 * @see BatchResponse
 */
@Experimental
public record BatchPage<T>(List<BatchResponse<T>> batches, @Nullable String nextPageToken) {}
