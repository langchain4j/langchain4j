package dev.langchain4j.agent.tool;

import dev.langchain4j.Experimental;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import java.util.Objects;

/**
 * TODO
 */
@Experimental
public class CompleteToolCall {
    // TODO location

    private final int index;
    private final ToolExecutionRequest request;

    public CompleteToolCall(int index, ToolExecutionRequest request) {
        this.index = index;
        this.request = ensureNotNull(request, "request");
    }

    /**
     * TODO
     */
    public int index() {
        return index;
    }

    /**
     * TODO
     */
    public ToolExecutionRequest request() {
        return request;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        CompleteToolCall that = (CompleteToolCall) object;
        return index == that.index
                && Objects.equals(request, that.request);
    }

    @Override
    public int hashCode() {
        return Objects.hash(index, request);
    }

    @Override
    public String toString() {
        return "CompleteToolCall{" +
                "index=" + index +
                ", request=" + request +
                '}';
    }
}
