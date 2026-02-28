package dev.langchain4j.model.batch;

import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Represents an error status for a failed request within a batch operation.
 *
 * @param code    the error code (e.g., HTTP status code or provider-specific error code)
 * @param message a human-readable description of the error
 * @param details optional additional details about the error, typically containing
 *                provider-specific diagnostic information.
 */
public record BatchError(int code, String message, @Nullable List<Map<String, Object>> details) {}
