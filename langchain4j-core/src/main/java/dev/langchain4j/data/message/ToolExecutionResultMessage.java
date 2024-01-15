package dev.langchain4j.data.message;

import dev.langchain4j.agent.tool.ToolExecutionRequest;

import java.util.Objects;

import static dev.langchain4j.data.message.ChatMessageType.TOOL_EXECUTION_RESULT;
import static dev.langchain4j.internal.Utils.quoted;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

/**
 * Represents the result of a tool execution in response to a {@link ToolExecutionRequest}.
 * {@link ToolExecutionRequest}s come from a previous {@link AiMessage#toolExecutionRequests()}.
 */
public class ToolExecutionResultMessage implements ChatMessage {

    private final String id;
    private final String toolName;
    private final String text;

    public ToolExecutionResultMessage(String id, String toolName, String text) {
        this.id = id;
        this.toolName = toolName;
        this.text = ensureNotBlank(text, "text");
    }

    public String id() {
        return id;
    }

    public String toolName() {
        return toolName;
    }

    public String text() {
        return text;
    }

    @Override
    public ChatMessageType type() {
        return TOOL_EXECUTION_RESULT;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ToolExecutionResultMessage that = (ToolExecutionResultMessage) o;
        return Objects.equals(this.id, that.id)
                && Objects.equals(this.toolName, that.toolName)
                && Objects.equals(this.text, that.text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, toolName, text);
    }

    @Override
    public String toString() {
        return "ToolExecutionResultMessage {" +
                " id = " + quoted(id) +
                " toolName = " + quoted(toolName) +
                " text = " + quoted(text) +
                " }";
    }

    public static ToolExecutionResultMessage from(ToolExecutionRequest request, String toolExecutionResult) {
        return new ToolExecutionResultMessage(request.id(), request.name(), toolExecutionResult);
    }

    public static ToolExecutionResultMessage from(String id, String toolName, String toolExecutionResult) {
        return new ToolExecutionResultMessage(id, toolName, toolExecutionResult);
    }

    public static ToolExecutionResultMessage toolExecutionResultMessage(ToolExecutionRequest request, String toolExecutionResult) {
        return from(request, toolExecutionResult);
    }

    public static ToolExecutionResultMessage toolExecutionResultMessage(String id, String toolName, String toolExecutionResult) {
        return from(id, toolName, toolExecutionResult);
    }
}
