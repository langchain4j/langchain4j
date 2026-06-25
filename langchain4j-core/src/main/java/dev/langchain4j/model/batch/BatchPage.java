package dev.langchain4j.model.batch;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.chat.BatchChatModel;
import dev.langchain4j.model.embedding.BatchEmbeddingModel;
import dev.langchain4j.model.image.BatchImageModel;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

import static dev.langchain4j.internal.Utils.copy;

/**
 * Represents a set of batch jobs that is potentially paginated.
 *
 * <p>A {@code BatchPage} contains the batch operations for the current page. If more jobs are available,
 * the {@code nextPageToken} can be used to request the next page of results.</p>
 *
 * @param <T> the type of the responses payload in each batch (e.g., {@code ChatResponse}, {@code Embedding})
 * @see BatchChatModel#list(BatchPagination)
 * @see BatchEmbeddingModel#list(BatchPagination)
 * @see BatchImageModel#list(BatchPagination)
 * @see BatchResponse
 */
@Experimental
public class BatchPage<T> {

    private final List<BatchResponse<T>> batches;

    @Nullable
    private final String nextPageToken;

    /**
     * Creates a new {@code BatchPage}.
     *
     * @param batches       the list of batch responses for the current page
     * @param nextPageToken the token to pass to {@code list} methods to retrieve the next page;
     *                      if present, it signifies more results are available. May be {@code null}
     *                      if no more pages exist.
     */
    public BatchPage(List<BatchResponse<T>> batches, @Nullable String nextPageToken) {
        this.batches = copy(batches);
        this.nextPageToken = nextPageToken;
    }

    /**
     * Returns the list of batch responses for the current page.
     */
    public List<BatchResponse<T>> batches() {
        return batches;
    }

    /**
     * Returns the token to pass to {@code list} methods to retrieve the next page, or {@code null}
     * if no more pages exist.
     */
    @Nullable
    public String nextPageToken() {
        return nextPageToken;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        BatchPage<?> batchPage = (BatchPage<?>) o;
        return Objects.equals(batches, batchPage.batches) && Objects.equals(nextPageToken, batchPage.nextPageToken);
    }

    @Override
    public int hashCode() {
        return Objects.hash(batches, nextPageToken);
    }

    @Override
    public String toString() {
        return "BatchPage{" +
                "batches=" + batches +
                ", nextPageToken='" + nextPageToken + '\'' +
                '}';
    }
}
