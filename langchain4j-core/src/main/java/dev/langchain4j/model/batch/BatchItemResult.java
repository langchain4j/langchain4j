package dev.langchain4j.model.batch;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import org.jspecify.annotations.Nullable;

/**
 * Represents the outcome of a single request within a batch operation.
 *
 * <p>Each batch request is processed independently and either succeeds (producing a response) or
 * fails (producing a {@link BatchError}). The results of a batch are returned in the same order as
 * the input requests, so the i-th {@code BatchItemResult} corresponds to the i-th submitted request,
 * allowing the caller to correlate every outcome with its originating request even when only some
 * requests fail.</p>
 *
 * @param <T> the type of a successful response payload (e.g., {@code ChatResponse}, {@code Embedding})
 * @see BatchResponse#results()
 */
@Experimental
public sealed interface BatchItemResult<T> permits BatchItemResult.Success, BatchItemResult.Failure {

    /**
     * Returns {@code true} if this request succeeded.
     */
    boolean isSuccess();

    /**
     * Returns the successful response, or {@code null} if this request failed.
     */
    @Nullable
    T response();

    /**
     * Returns the error, or {@code null} if this request succeeded.
     */
    @Nullable
    BatchError error();

    /**
     * Creates a successful {@link BatchItemResult}.
     */
    static <T> BatchItemResult<T> success(T response) {
        return new Success<>(response);
    }

    /**
     * Creates a failed {@link BatchItemResult}.
     */
    static <T> BatchItemResult<T> failure(BatchError error) {
        return new Failure<>(error);
    }

    /**
     * The successful outcome of a single batch request.
     *
     * @param <T>      the type of the response payload
     * @param response the successful response, never {@code null}
     */
    record Success<T>(T response) implements BatchItemResult<T> {
        public Success {
            ensureNotNull(response, "response");
        }

        @Override
        public boolean isSuccess() {
            return true;
        }

        @Override
        public @Nullable BatchError error() {
            return null;
        }
    }

    /**
     * The failed outcome of a single batch request.
     *
     * @param <T>   the type of the response payload that would have been produced on success
     * @param error the error describing the failure, never {@code null}
     */
    record Failure<T>(BatchError error) implements BatchItemResult<T> {
        public Failure {
            ensureNotNull(error, "error");
        }

        @Override
        public boolean isSuccess() {
            return false;
        }

        @Override
        public @Nullable T response() {
            return null;
        }
    }
}
