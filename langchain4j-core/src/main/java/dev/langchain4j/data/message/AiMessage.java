package dev.langchain4j.data.message;

import static dev.langchain4j.data.message.ChatMessageType.AI;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.Utils.quoted;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static java.util.Arrays.asList;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import java.util.List;
import java.util.Objects;

/**
 * Represents a response message from an AI (language model).
 * The message can contain either a textual response or a request to execute one/multiple tool(s).
 * In the case of tool execution, the response to this message should be one/multiple {@link ToolExecutionResultMessage}.
 */
public class AiMessage implements ChatMessage {

    private final String text;
    private final List<ToolExecutionRequest> toolExecutionRequests;
    private final String reasoningContent;

    /**
     * Create a new {@link AiMessage} with the given text.
     *
     * @param text the text of the message.
     */
    public AiMessage(String text) {
        this.text = ensureNotNull(text, "text");
        this.reasoningContent = null;
        this.toolExecutionRequests = null;
    }

    /**
     * Create a new {@link AiMessage} with the given tool execution requests.
     *
     * @param toolExecutionRequests the tool execution requests of the message.
     */
    public AiMessage(List<ToolExecutionRequest> toolExecutionRequests) {
        this.text = null;
        this.reasoningContent = null;
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
        this.reasoningContent = null;
        this.toolExecutionRequests = ensureNotEmpty(toolExecutionRequests, "toolExecutionRequests");
    }

    /**
     * Create a new {@link AiMessage} with the given text and reasoning content.
     *
     * @param text                  the text of the message.
     * @param reasoningContent      the reasoning content of the message.
     */
    public AiMessage(String text, String reasoningContent) {
        this.text = ensureNotNull(text, "text");
        this.reasoningContent = ensureNotNull(reasoningContent, "reasoningContent");
        this.toolExecutionRequests = null;
    }

    /**
     * Create a new {@link AiMessage} with the given text, reasoning content and tool execution requests.
     *
     * @param text                  the text of the message.
     * @param reasoningContent      the reasoning content of the message.
     * @param toolExecutionRequests the tool execution requests of the message.
     */
    public AiMessage(String text, String reasoningContent, List<ToolExecutionRequest> toolExecutionRequests) {
        this.text = ensureNotBlank(text, "text");
        this.reasoningContent = ensureNotBlank(reasoningContent, "reasoningContent");
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
     * Get the reasoning content of the message.
     *
     * @return the reasoning content of the message.
     */
    public String reasoningContent() {
        return reasoningContent;
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
                && Objects.equals(this.reasoningContent, that.reasoningContent)
                && Objects.equals(this.toolExecutionRequests, that.toolExecutionRequests);
    }

    @Override
    public int hashCode() {
        return Objects.hash(text, reasoningContent, toolExecutionRequests);
    }

    @Override
    public String toString() {
        return "AiMessage {" + " text = "
                + quoted(text) + " reasoningContent = "
                + quoted(reasoningContent) + " toolExecutionRequests = "
                + toolExecutionRequests + " }";
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
     * Create a new {@link AiMessage} with the given text and reasoning content.
     *
     * @param text                  the text of the message.
     * @param reasoningContent      the reasoning content of the message.
     * @return the new {@link AiMessage}.
     */
    public static AiMessage from(String text, String reasoningContent) {
        return new AiMessage(text, reasoningContent);
    }

    /**
     * Create a new {@link AiMessage} with the given text and tool execution requests.
     *
     * @param text                  the text of the message.
     * @param toolExecutionRequests the tool execution requests of the message.
     * @return the new {@link AiMessage}.
     */
    public static AiMessage from(String text, List<ToolExecutionRequest> toolExecutionRequests) {
        return new AiMessage(text, toolExecutionRequests);
    }

    /**
     * Create a new {@link AiMessage} with the given text, reasoning content and tool execution requests.
     *
     * @param text                  the text of the message.
     * @param reasoningContent      the reasoning content of the message.
     * @param toolExecutionRequests the tool execution requests of the message.
     * @return the new {@link AiMessage}.
     */
    public static AiMessage from(
            String text, String reasoningContent, List<ToolExecutionRequest> toolExecutionRequests) {
        return new AiMessage(text, reasoningContent, toolExecutionRequests);
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
     * Create a new {@link AiMessage} with the given text and reasoning content.
     *
     * @param text                  the text of the message.
     * @param reasoningContent      the reasoning content of the message.
     * @return the new {@link AiMessage}.
     */
    public static AiMessage aiMessage(String text, String reasoningContent) {
        return from(text, reasoningContent);
    }

    /**
     * Create a new {@link AiMessage} with the given text and tool execution requests.
     *
     * @param text                  the text of the message.
     * @param toolExecutionRequests the tool execution requests of the message.
     * @return the new {@link AiMessage}.
     */
    public static AiMessage aiMessage(String text, List<ToolExecutionRequest> toolExecutionRequests) {
        return from(text, toolExecutionRequests);
    }
}
