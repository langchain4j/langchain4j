package dev.langchain4j.data.message;

import dev.langchain4j.agent.tool.ToolExecutionRequest;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static dev.langchain4j.data.message.ChatMessageType.AI;
import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.Utils.quoted;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static java.util.Arrays.asList;

/**
 * Represents a response message from an AI (language model).
 * The message can contain either a textual response or a request to execute one/multiple tool(s).
 * In the case of tool execution, the response to this message should be one/multiple {@link ToolExecutionResultMessage}.
 */
public class AiMessage implements ChatMessage {

    private final String text;
    private final String thinking; // TODO name: reasoning? thoughts?
    private final List<ToolExecutionRequest> toolExecutionRequests;
    private final Map<String, Object> metadata; // TODO types: test ser/deser TODO name: extra? custom? does not sound like metadata

    /**
     * Create a new {@link AiMessage} with the given text.
     *
     * @param text the text of the message.
     */
    public AiMessage(String text) {
        this.text = ensureNotNull(text, "text");
        this.thinking = null;
        this.toolExecutionRequests = List.of();
        this.metadata = Map.of();
    }

    /**
     * Create a new {@link AiMessage} with the given tool execution requests.
     *
     * @param toolExecutionRequests the tool execution requests of the message.
     */
    public AiMessage(List<ToolExecutionRequest> toolExecutionRequests) {
        this.text = null;
        this.thinking = null;
        this.toolExecutionRequests = ensureNotEmpty(toolExecutionRequests, "toolExecutionRequests");
        this.metadata = Map.of();
    }

    /**
     * Create a new {@link AiMessage} with the given text and tool execution requests.
     *
     * @param text                  the text of the message.
     * @param toolExecutionRequests the tool execution requests of the message.
     */
    public AiMessage(String text, List<ToolExecutionRequest> toolExecutionRequests) {
        this.text = text;
        this.thinking = null;
        this.toolExecutionRequests = copy(toolExecutionRequests);
        this.metadata = Map.of();
    }

    /**
     * Create a new {@link AiMessage} with the given TODO
     *
     * @param builder TODO
     */
    public AiMessage(Builder builder) {
        this.text = builder.text;
        this.thinking = builder.thinking;
        this.toolExecutionRequests = copy(builder.toolExecutionRequests);
        this.metadata = copy(builder.metadata);
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
     * TODO
     * @return
     */
    public String thinking() { // TODO name
        return thinking;
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

    public Map<String, Object> metadata() { // TODO names
        return metadata;
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
                && Objects.equals(this.thinking, that.thinking)
                && Objects.equals(this.toolExecutionRequests, that.toolExecutionRequests)
                && Objects.equals(this.metadata, that.metadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(text, thinking, toolExecutionRequests, metadata);
    }

    @Override
    public String toString() {
        return "AiMessage {" +
                " text = " + quoted(text) +
                ", thinking = " + quoted(thinking) + // TODO name
                ", toolExecutionRequests = " + toolExecutionRequests +
                ", metadata = " + metadata + // TODO name
                " }";
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String text;
        private String thinking;
        private List<ToolExecutionRequest> toolExecutionRequests;
        private Map<String, Object> metadata;

        public Builder text(String text) {
            this.text = text;
            return this;
        }

        public Builder thinking(String thinking) { // TODO names
            this.thinking = thinking;
            return this;
        }

        public Builder toolExecutionRequests(List<ToolExecutionRequest> toolExecutionRequests) {
            this.toolExecutionRequests = toolExecutionRequests;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) { // TODO names
            this.metadata = metadata;
            return this;
        }

        public AiMessage build() {
            return new AiMessage(this); // TODO ser/deser
        }
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
     * @param text                  the text of the message.
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
     * @param text                  the text of the message.
     * @param toolExecutionRequests the tool execution requests of the message.
     * @return the new {@link AiMessage}.
     */
    public static AiMessage aiMessage(String text, List<ToolExecutionRequest> toolExecutionRequests) {
        return from(text, toolExecutionRequests);
    }
}
