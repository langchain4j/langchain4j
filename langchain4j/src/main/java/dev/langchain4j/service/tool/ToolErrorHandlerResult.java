package dev.langchain4j.service.tool;

import static dev.langchain4j.internal.Utils.quoted;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import java.util.Objects;

/**
 * @since 1.4.0
 */
public class ToolErrorHandlerResult {

    private final String text;
    private final boolean propagateException;

    public ToolErrorHandlerResult(String text) {
        this.text = ensureNotNull(text, "text");
        this.propagateException = false;
    }

    private ToolErrorHandlerResult(String text, boolean propagateException) {
        this.text = text;
        this.propagateException = propagateException;
    }

    public String text() {
        return text;
    }

    /**
     * Returns {@code true} if the original exception should be re-thrown
     * instead of converting it to an error result.
     *
     * @since 1.17.0
     */
    public boolean shouldPropagateException() {
        return propagateException;
    }

    public static ToolErrorHandlerResult text(String text) {
        return new ToolErrorHandlerResult(text);
    }

    /**
     * Signals that the original exception should be re-thrown.
     *
     * @since 1.17.0
     */
    public static ToolErrorHandlerResult propagateException() {
        return new ToolErrorHandlerResult(null, true);
    }

    @Override
    public boolean equals(final Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        ToolErrorHandlerResult that = (ToolErrorHandlerResult) object;
        return propagateException == that.propagateException
                && Objects.equals(text, that.text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(text, propagateException);
    }

    @Override
    public String toString() {
        return "ToolErrorHandlerResult{" +
                "text=" + quoted(text) +
                ", propagateException=" + propagateException +
                '}';
    }
}
