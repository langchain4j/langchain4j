package dev.langchain4j.model.batch;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Contains the extracted results from a batch operation, separating successful responses from errors.
 *
 * <p>This record is used internally by batch processors to return both successful responses
 * and any errors that occurred during batch processing. Each batch request may succeed or fail
 * independently, so a single batch operation can produce both responses and errors.</p>
 *
 * @param <T>       the type of successful response (e.g., {@code ChatResponse}, {@code Embedding})
 * @param responses the list of successful responses from the batch operation
 * @param errors    the list of errors that occurred for individual requests in the batch
 */
public record ExtractedBatchResults<T>(List<T> responses, List<Status> errors) {

    /**
     * Represents an error status for a failed request within a batch operation.
     *
     * @param code    the error code (e.g., HTTP status code or provider-specific error code)
     * @param message a human-readable description of the error
     * @param details optional additional details about the error, typically containing
     *                provider-specific diagnostic information
     */
    public record Status(int code, String message, @Nullable List<Map<String, Object>> details) {
    }
}
