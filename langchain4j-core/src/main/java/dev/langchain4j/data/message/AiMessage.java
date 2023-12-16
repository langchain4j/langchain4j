package dev.langchain4j.data.message;

import dev.langchain4j.agent.tool.ToolExecutionRequest;

import java.util.List;
import java.util.Objects;

import static dev.langchain4j.data.message.ChatMessageType.AI;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.Utils.quoted;
import static java.util.Arrays.asList;

/**
 * Represents a response message from an AI (language model).
 * The message can contain either a textual response or a request to execute one/multiple tool(s).
 * In the case of tool execution, the response to this message should be one/multiple {@link ToolExecutionResultMessage}.
 */
public class AiMessage extends ChatMessage {

    private final List<ToolExecutionRequest> toolExecutionRequests;

    public AiMessage(String text) {
        this(text, null);
    }

    public AiMessage(String text, List<ToolExecutionRequest> toolExecutionRequests) {
        super(text);
        this.toolExecutionRequests = toolExecutionRequests;
    }

    public List<ToolExecutionRequest> toolExecutionRequests() {
        return toolExecutionRequests;
    }

    public boolean hasToolExecutionRequests() {
        return !isNullOrEmpty(toolExecutionRequests);
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
                && Objects.equals(this.toolExecutionRequests, that.toolExecutionRequests);
    }

    @Override
    public int hashCode() {
        return Objects.hash(text, toolExecutionRequests);
    }

    @Override
    public String toString() {
        return "AiMessage {" +
                " text = " + quoted(text) +
                " toolExecutionRequests = " + toolExecutionRequests +
                " }";
    }

    public static AiMessage from(String text) {
        return new AiMessage(text);
    }

    public static AiMessage from(ToolExecutionRequest... toolExecutionRequests) {
        return from(asList(toolExecutionRequests));
    }

    public static AiMessage from(List<ToolExecutionRequest> toolExecutionRequests) {
        return new AiMessage(null, toolExecutionRequests);
    }

    public static AiMessage aiMessage(String text) {
        return from(text);
    }

    public static AiMessage aiMessage(ToolExecutionRequest... toolExecutionRequests) {
        return aiMessage(asList(toolExecutionRequests));
    }

    public static AiMessage aiMessage(List<ToolExecutionRequest> toolExecutionRequests) {
        return from(toolExecutionRequests);
    }
}
