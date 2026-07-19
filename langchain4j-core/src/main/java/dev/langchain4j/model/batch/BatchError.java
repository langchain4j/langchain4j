package dev.langchain4j.model.batch;

import dev.langchain4j.Experimental;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static dev.langchain4j.internal.Utils.copy;

/**
 * Represents an error status for a failed request within a batch operation.
 */
@Experimental
public final class BatchError {

    private final int code;
    private final String message;
    private final @Nullable List<Map<String, Object>> details;

    public BatchError(int code, String message, @Nullable List<Map<String, Object>> details) {
        this.code = code;
        this.message = message;
        this.details = copy(details);
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
        if (o == null || getClass() != o.getClass()) return false;
        BatchError that = (BatchError) o;
        return code == that.code && Objects.equals(message, that.message) && Objects.equals(details, that.details);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code, message, details);
    }

    @Override
    public String toString() {
        return "BatchError{" +
                "code=" + code +
                ", message='" + message + '\'' +
                ", details=" + details +
                '}';
    }
}
