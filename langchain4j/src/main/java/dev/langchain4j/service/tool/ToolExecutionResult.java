package dev.langchain4j.service.tool;

import static dev.langchain4j.internal.Utils.quoted;

import java.util.Objects;

/**
 * @since 1.4.0
 */
public class ToolExecutionResult { // TODO name, location

    private final String text;

    public ToolExecutionResult(String text) {
        this.text = text; // TODO
    }

    public String text() {
        return text;
    }

    @Override
    public boolean equals(final Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        ToolExecutionResult that = (ToolExecutionResult) object;
        return Objects.equals(text, that.text);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(text);
    }

    @Override
    public String toString() {
        return "ToolExecutionResult {" + // TODO names
                " text = " + quoted(text) +
                " }";
    }

    public static ToolExecutionResult from(String text) {
        return new ToolExecutionResult(text);
    }
}
