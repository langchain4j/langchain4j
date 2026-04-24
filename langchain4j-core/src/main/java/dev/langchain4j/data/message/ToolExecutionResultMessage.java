package dev.langchain4j.data.message;

import static dev.langchain4j.data.message.ChatMessageType.TOOL_EXECUTION_RESULT;
import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static java.util.Arrays.asList;

import dev.langchain4j.Experimental;
import dev.langchain4j.agent.tool.ToolExecutionRequest;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Represents the result of a tool execution in response to a {@link ToolExecutionRequest}.
 * {@link ToolExecutionRequest}s come from a previous {@link AiMessage#toolExecutionRequests()}.
 * <br>
 * <br>
 * The result is stored as a list of {@link Content}s. Typically, the result is a single {@link TextContent},
 * but some LLM providers also support {@link ImageContent} in tool results.
 */
public class ToolExecutionResultMessage implements ChatMessage {

    private final String id;
    private final String toolName;
    private final List<Content> contents;
    private final Boolean isError;
    private final Map<String, Object> attributes;

    /**
     * Creates a {@link ToolExecutionResultMessage} from a builder.
     *
     * @since 1.11.0
     */
    public ToolExecutionResultMessage(Builder builder) {
        this.id = builder.id;
        this.toolName = builder.toolName;

        boolean hasText = builder.text != null;
        boolean hasContents = builder.contents != null && !builder.contents.isEmpty();
        if (hasText && hasContents) {
            throw new IllegalArgumentException("Either text or contents must be provided, not both");
        } else if (hasText) {
            this.contents = List.of(TextContent.from(builder.text));
        } else if (hasContents) {
            this.contents = copy(builder.contents);
        } else {
            throw new IllegalArgumentException("Either text or contents must be provided");
        }

        this.isError = builder.isError;
        this.attributes = copy(builder.attributes);
    }

    /**
     * Creates a {@link ToolExecutionResultMessage}.
     * @param id the id of the tool.
     * @param toolName the name of the tool.
     * @param text the result of the tool execution.
     */
    public ToolExecutionResultMessage(String id, String toolName, String text) {
        this.id = id;
        this.toolName = toolName;
        this.contents = List.of(TextContent.from(ensureNotNull(text, "text")));
        this.isError = null;
        this.attributes = Map.of();
    }

    /**
     * Returns the id of the tool.
     * @return the id of the tool.
     */
    public String id() {
        return id;
    }

    /**
     * Returns the name of the tool.
     * @return the name of the tool.
     */
    public String toolName() {
        return toolName;
    }

    /**
     * Returns the result of the tool execution as text.
     *
     * @return the text result of the tool execution.
     * @throws IllegalStateException if contents contains non-text or multiple content elements.
     *                               Use {@link #contents()} instead.
     */
    public String text() {
        if (contents.size() == 1 && contents.get(0) instanceof TextContent textContent) {
            return textContent.text();
        }
        throw new IllegalStateException(
                "text() cannot be called when contents contains non-text or multiple content elements. "
                        + "Use contents() instead.");
    }

    /**
     * Returns the {@link Content}s of the tool execution result.
     *
     * @return the contents.
     * @since 1.13.0
     */
    @Experimental
    public List<Content> contents() {
        return contents;
    }

    /**
     * Whether this message contains a single {@link TextContent}.
     *
     * @return {@code true} if this message contains exactly one element which is a {@link TextContent}.
     * @since 1.13.0
     * @see #text()
     */
    @Experimental
    public boolean hasSingleText() {
        return contents.size() == 1 && contents.get(0) instanceof TextContent;
    }

    /**
     * Returns whether the tool execution resulted in an error.
     * @return true if the tool execution resulted in an error, false if it did not, null if unknown.
     */
    public Boolean isError() {
        return isError;
    }

    /**
     * Returns attributes of a message.
     * These attributes are not sent to the LLM, but can be persisted in a {@link dev.langchain4j.memory.ChatMemory}.
     *
     * @since 1.12.0
     */
    public Map<String, Object> attributes() {
        return attributes;
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
                && Objects.equals(this.contents, that.contents)
                && Objects.equals(this.isError, that.isError)
                && Objects.equals(this.attributes, that.attributes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, toolName, contents, isError, attributes);
    }

    @Override
    public String toString() {
        return "ToolExecutionResultMessage{" +
                "id='" + id + '\'' +
                ", toolName='" + toolName + '\'' +
                ", contents=" + contents +
                ", isError=" + isError +
                ", attributes=" + attributes +
                '}';
    }

    /**
     * Creates a builder for {@link ToolExecutionResultMessage}.
     * @return the builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String id;
        private String toolName;
        private String text;
        private List<Content> contents;
        private Boolean isError;
        private Map<String, Object> attributes;

        /**
         * Sets the id of the tool.
         * @param id the id of the tool.
         * @return the builder.
         */
        public Builder id(String id) {
            this.id = id;
            return this;
        }

        /**
         * Sets the name of the tool.
         * @param toolName the name of the tool.
         * @return the builder.
         */
        public Builder toolName(String toolName) {
            this.toolName = toolName;
            return this;
        }

        /**
         * Sets the result text of the tool execution.
         * The text will be wrapped into a single {@link TextContent}.
         * Mutually exclusive with {@link #contents(List)}.
         *
         * @param text the result of the tool execution.
         * @return the builder.
         */
        public Builder text(String text) {
            this.text = text;
            return this;
        }

        /**
         * Sets the contents of the tool execution result.
         * Mutually exclusive with {@link #text(String)}.
         *
         * @param contents the contents.
         * @return the builder.
         * @since 1.13.0
         */
        @Experimental
        public Builder contents(List<Content> contents) {
            this.contents = contents;
            return this;
        }

        /**
         * Sets the contents of the tool execution result.
         * Mutually exclusive with {@link #text(String)}.
         *
         * @param contents the contents.
         * @return the builder.
         * @since 1.13.0
         */
        @Experimental
        public Builder contents(Content... contents) {
            return contents(asList(contents));
        }

        /**
         * Sets whether the tool execution resulted in an error.
         * @param isError whether the tool execution resulted in an error.
         * @return the builder.
         */
        public Builder isError(Boolean isError) {
            this.isError = isError;
            return this;
        }

        public Builder attributes(Map<String, Object> attributes) {
            this.attributes = attributes;
            return this;
        }

        /**
         * Builds the {@link ToolExecutionResultMessage}.
         * @return the {@link ToolExecutionResultMessage}.
         */
        public ToolExecutionResultMessage build() {
            return new ToolExecutionResultMessage(this);
        }
    }

    /**
     * Creates a {@link ToolExecutionResultMessage} from a {@link ToolExecutionRequest} and the result of the tool execution.
     * @param request the request.
     * @param toolExecutionResult the result of the tool execution.
     * @return the {@link ToolExecutionResultMessage}.
     */
    public static ToolExecutionResultMessage from(ToolExecutionRequest request, String toolExecutionResult) {
        return new ToolExecutionResultMessage(request.id(), request.name(), toolExecutionResult);
    }

    /**
     * Creates a {@link ToolExecutionResultMessage} from a {@link ToolExecutionRequest} and the result of the tool execution.
     * @param id the id of the tool.
     * @param toolName the name of the tool.
     * @param toolExecutionResult the result of the tool execution.
     * @return the {@link ToolExecutionResultMessage}.
     */
    public static ToolExecutionResultMessage from(String id, String toolName, String toolExecutionResult) {
        return new ToolExecutionResultMessage(id, toolName, toolExecutionResult);
    }

    /**
     * Creates a {@link ToolExecutionResultMessage} from a {@link ToolExecutionRequest} and the result of the tool execution.
     * @param request the request.
     * @param toolExecutionResult the result of the tool execution.
     * @return the {@link ToolExecutionResultMessage}.
     */
    public static ToolExecutionResultMessage toolExecutionResultMessage(
            ToolExecutionRequest request, String toolExecutionResult) {
        return from(request, toolExecutionResult);
    }

    /**
     * Creates a {@link ToolExecutionResultMessage} from a {@link ToolExecutionRequest} and the result of the tool execution.
     * @param id the id of the tool.
     * @param toolName the name of the tool.
     * @param toolExecutionResult the result of the tool execution.
     * @return the {@link ToolExecutionResultMessage}.
     */
    public static ToolExecutionResultMessage toolExecutionResultMessage(
            String id, String toolName, String toolExecutionResult) {
        return from(id, toolName, toolExecutionResult);
    }
}
