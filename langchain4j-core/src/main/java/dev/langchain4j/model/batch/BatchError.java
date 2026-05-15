package dev.langchain4j.model.batch;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Represents an error status for a failed request within a batch operation.
 */
public final class BatchError {

    private final int code;
    private final String message;
    private final @Nullable List<Map<String, Object>> details;

    public BatchError(int code, String message, @Nullable List<Map<String, Object>> details) {
        this.code = code;
        this.message = message;
        this.details = details;
    }

    public int code() {
        return code;
    }

    public String message() {
        return message;
    }

    public @Nullable List<Map<String, Object>> details() {
        return details;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BatchError other)) return false;
        return code == other.code && Objects.equals(message, other.message) && Objects.equals(details, other.details);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code, message, details);
    }

    @Override
    public String toString() {
        return "BatchError{" + "code=" + code + ", message='" + message + '\'' + ", details=" + details + '}';
    }
}
