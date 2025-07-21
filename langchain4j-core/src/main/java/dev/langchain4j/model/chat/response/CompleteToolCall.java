package dev.langchain4j.model.chat.response;

import dev.langchain4j.Experimental;
import dev.langchain4j.agent.tool.ToolExecutionRequest;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import java.util.Objects;

/**
 * TODO
 * @since 1.2.0
 */
@Experimental
public class CompleteToolCall {

    private final int index;
    private final ToolExecutionRequest toolExecutionRequest;

    public CompleteToolCall(int index, ToolExecutionRequest toolExecutionRequest) {
        this.index = index;
        this.toolExecutionRequest = ensureNotNull(toolExecutionRequest, "toolExecutionRequest");
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
    public ToolExecutionRequest toolExecutionRequest() {
        return toolExecutionRequest;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        CompleteToolCall that = (CompleteToolCall) object;
        return index == that.index
                && Objects.equals(toolExecutionRequest, that.toolExecutionRequest);
    }

    @Override
    public int hashCode() {
        return Objects.hash(index, toolExecutionRequest);
    }

    @Override
    public String toString() {
        return "CompleteToolCall{" +
                "index=" + index +
                ", toolExecutionRequest=" + toolExecutionRequest +
                '}';
    }
}
