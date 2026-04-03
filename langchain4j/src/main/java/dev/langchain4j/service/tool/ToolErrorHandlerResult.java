package dev.langchain4j.service.tool;

import static dev.langchain4j.internal.Utils.quoted;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import java.util.Objects;

/**
 * @since 1.4.0
 */
public class ToolErrorHandlerResult {

    private final String text;

    public ToolErrorHandlerResult(String text) {
        this.text = ensureNotNull(text, "text");
    }

    public String text() {
        return text;
    }

    public static ToolErrorHandlerResult text(String text) {
        return new ToolErrorHandlerResult(text);
    }

    @Override
    public boolean equals(final Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        ToolErrorHandlerResult that = (ToolErrorHandlerResult) object;
        return Objects.equals(text, that.text);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(text);
    }

    @Override
    public String toString() {
        return "ToolErrorHandlerResult{" +
                "text=" + quoted(text) +
                '}';
    }
}
