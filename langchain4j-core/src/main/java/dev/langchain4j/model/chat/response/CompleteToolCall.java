package dev.langchain4j.model.chat.response;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNegative;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import java.util.Objects;

/**
 * Represents a complete tool call.
 * Includes the index, and complete {@link ToolExecutionRequest}.
 *
 * @see PartialToolCall
 * @since 1.2.0
 */
@Experimental
public class CompleteToolCall {

    private final int index;
    private final ToolExecutionRequest toolExecutionRequest;

    public CompleteToolCall(int index, ToolExecutionRequest toolExecutionRequest) {
        this.index = ensureNotNegative(index, "index");
        this.toolExecutionRequest = ensureNotNull(toolExecutionRequest, "toolExecutionRequest");
    }

    /**
     * The index of the tool call, starting from 0 and increasing by 1.
     * When the LLM initiates multiple tool calls, this index helps correlate
     * the different {@link PartialToolCall}s with each other and with the final {@link CompleteToolCall}.
     */
    public int index() {
        return index;
    }

    /**
     * A fully constructed {@link ToolExecutionRequest} that is ready for execution.
     */
    public ToolExecutionRequest toolExecutionRequest() {
        return toolExecutionRequest;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        CompleteToolCall that = (CompleteToolCall) object;
        return index == that.index && Objects.equals(toolExecutionRequest, that.toolExecutionRequest);
    }

    @Override
    public int hashCode() {
        return Objects.hash(index, toolExecutionRequest);
    }

    @Override
    public String toString() {
        return "CompleteToolCall{" + "index=" + index + ", toolExecutionRequest=" + toolExecutionRequest + '}';
    }
}
