package dev.langchain4j.data.message;

import dev.langchain4j.agent.tool.ToolExecutionRequest;

import java.util.Objects;

import static dev.langchain4j.data.message.ChatMessageType.AI;
import static dev.langchain4j.internal.Utils.quoted;

/**
 * Represents a response message from an AI (language model).
 * The message can contain either a textual response or a request to execute a tool.
 * In the case of tool execution, the response to this message should be a {@link ToolExecutionResultMessage}.
 */
public class AiMessage extends ChatMessage {

    private final ToolExecutionRequest toolExecutionRequest;

    public AiMessage(String text) {
        this(text, null);
    }

    public AiMessage(String text, ToolExecutionRequest toolExecutionRequest) {
        super(text);
        this.toolExecutionRequest = toolExecutionRequest;
    }

    public ToolExecutionRequest toolExecutionRequest() {
        return toolExecutionRequest;
    }

    @Override
    public ChatMessageType type() {
        return AI;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AiMessage that = (AiMessage) o;
        return Objects.equals(this.text, that.text)
                && Objects.equals(this.toolExecutionRequest, that.toolExecutionRequest);
    }

    @Override
    public int hashCode() {
        return Objects.hash(text, toolExecutionRequest);
    }

    @Override
    public String toString() {
        return "AiMessage {" +
                " text = " + quoted(text) +
                " toolExecutionRequest = " + toolExecutionRequest +
                " }";
    }

    public static AiMessage from(String text) {
        return new AiMessage(text);
    }

    public static AiMessage from(ToolExecutionRequest toolExecutionRequest) {
        return new AiMessage(null, toolExecutionRequest);
    }

    public static AiMessage aiMessage(String text) {
        return from(text);
    }

    public static AiMessage aiMessage(ToolExecutionRequest toolExecutionRequest) {
        return from(toolExecutionRequest);
    }
}
