package dev.langchain4j.data.message;

import dev.langchain4j.agent.tool.ToolExecutionRequest;

import java.util.List;
import java.util.Objects;

import static dev.langchain4j.data.message.ChatMessageType.AI;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.Utils.quoted;
import static dev.langchain4j.internal.ValidationUtils.*;
import static java.util.Arrays.asList;

/**
 * Represents a response message from an AI (language model).
 * The message can contain either a textual response or a request to execute one/multiple tool(s).
 * In the case of tool execution, the response to this message should be one/multiple {@link ToolExecutionResultMessage}.
 */
public class AiMessage implements ChatMessage {

    private final String text;
    private final List<ToolExecutionRequest> toolExecutionRequests;

    /**
     * Create a new {@link AiMessage} with the given text.
     *
     * @param text the text of the message.
     */
    public AiMessage(String text) {
        this.text = ensureNotNull(text, "text");
        this.toolExecutionRequests = null;
    }

    /**
     * Create a new {@link AiMessage} with the given tool execution requests.
     *
     * @param toolExecutionRequests the tool execution requests of the message.
     */
    public AiMessage(List<ToolExecutionRequest> toolExecutionRequests) {
        this.text = null;
        this.toolExecutionRequests = ensureNotEmpty(toolExecutionRequests, "toolExecutionRequests");
    }

    /**
     * Create a new {@link AiMessage} with the given text and tool execution requests.
     *
     * @param text                  the text of the message.
     * @param toolExecutionRequests the tool execution requests of the message.
     */
    public AiMessage(String text, List<ToolExecutionRequest> toolExecutionRequests) {
        this.text = ensureNotBlank(text, "text");
        this.toolExecutionRequests = ensureNotEmpty(toolExecutionRequests, "toolExecutionRequests");
    }

    /**
     * Get the text of the message.
     *
     * @return the text of the message.
     */
    public String text() {
        return text;
    }

    /**
     * Get the tool execution requests of the message.
     *
     * @return the tool execution requests of the message.
     */
    public List<ToolExecutionRequest> toolExecutionRequests() {
        return toolExecutionRequests;
    }

    /**
     * Check if the message has ToolExecutionRequests.
     *
     * @return true if the message has ToolExecutionRequests, false otherwise.
     */
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

    /**
     * Create a new {@link AiMessage} with the given text.
     *
     * @param text the text of the message.
     * @return the new {@link AiMessage}.
     */
    public static AiMessage from(String text) {
        return new AiMessage(text);
    }

    /**
     * Create a new {@link AiMessage} with the given tool execution requests.
     *
     * @param toolExecutionRequests the tool execution requests of the message.
     * @return the new {@link AiMessage}.
     */
    public static AiMessage from(ToolExecutionRequest... toolExecutionRequests) {
        return from(asList(toolExecutionRequests));
    }

    /**
     * Create a new {@link AiMessage} with the given tool execution requests.
     *
     * @param toolExecutionRequests the tool execution requests of the message.
     * @return the new {@link AiMessage}.
     */
    public static AiMessage from(List<ToolExecutionRequest> toolExecutionRequests) {
        return new AiMessage(toolExecutionRequests);
    }

    /**
     * Create a new {@link AiMessage} with the given text and tool execution requests.
     *
     * @param text the text of the message.
     * @param toolExecutionRequests the tool execution requests of the message.
     * @return the new {@link AiMessage}.
     */
    public static AiMessage from(String text, List<ToolExecutionRequest> toolExecutionRequests) {
        return new AiMessage(text, toolExecutionRequests);
    }

    /**
     * Create a new {@link AiMessage} with the given text.
     *
     * @param text the text of the message.
     * @return the new {@link AiMessage}.
     */
    public static AiMessage aiMessage(String text) {
        return from(text);
    }

    /**
     * Create a new {@link AiMessage} with the given tool execution requests.
     *
     * @param toolExecutionRequests the tool execution requests of the message.
     * @return the new {@link AiMessage}.
     */
    public static AiMessage aiMessage(ToolExecutionRequest... toolExecutionRequests) {
        return aiMessage(asList(toolExecutionRequests));
    }

    /**
     * Create a new {@link AiMessage} with the given tool execution requests.
     *
     * @param toolExecutionRequests the tool execution requests of the message.
     * @return the new {@link AiMessage}.
     */
    public static AiMessage aiMessage(List<ToolExecutionRequest> toolExecutionRequests) {
        return from(toolExecutionRequests);
    }

    /**
     * Create a new {@link AiMessage} with the given text and tool execution requests.
     *
     * @param text the text of the message.
     * @param toolExecutionRequests the tool execution requests of the message.
     * @return the new {@link AiMessage}.
     */
    public static AiMessage aiMessage(String text, List<ToolExecutionRequest> toolExecutionRequests) {
        return from(text, toolExecutionRequests);
    }
}
