package dev.langchain4j.data.message;

import static dev.langchain4j.data.message.ChatMessageType.TOOL_EXECUTION_RESULT;
import static dev.langchain4j.internal.Utils.quoted;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import java.util.Objects;

/**
 * Represents the result of a tool execution in response to a {@link ToolExecutionRequest}.
 * {@link ToolExecutionRequest}s come from a previous {@link AiMessage#toolExecutionRequests()}.
 */
public class ToolExecutionResultMessage implements ChatMessage {

    private final String id;
    private final String toolName;
    private final String text;
    private final Boolean isError;

    /**
     * Creates a {@link ToolExecutionResultMessage} from a builder.
     */
    public ToolExecutionResultMessage(Builder builder) {
        this.id = builder.id;
        this.toolName = builder.toolName;
        this.text = ensureNotNull(builder.text, "text");
        this.isError = builder.isError;
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
        this.text = ensureNotNull(text, "text");
        this.isError = null;
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
     * Returns the result of the tool execution.
     * @return the result of the tool execution.
     */
    public String text() {
        return text;
    }

    /**
     * Returns whether the tool execution resulted in an error.
     * @return true if the tool execution resulted in an error, false if it did not, null if unknown.
     */
    public Boolean isError() {
        return isError;
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
                && Objects.equals(this.text, that.text)
                && Objects.equals(this.isError, that.isError);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, toolName, text, isError);
    }

    @Override
    public String toString() {
        return "ToolExecutionResultMessage {" + " id = "
                + quoted(id) + " toolName = "
                + quoted(toolName) + " text = "
                + quoted(text) + " isError = "
                + isError + " }";
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
        private Boolean isError;

        /**
         * Sets the id of the tool.
         * @param id the id of the tool.
         * @return the builder.
         */
        @JsonProperty("id")
        public Builder id(String id) {
            this.id = id;
            return this;
        }

        /**
         * Sets the name of the tool.
         * @param toolName the name of the tool.
         * @return the builder.
         */
        @JsonProperty("toolName")
        public Builder toolName(String toolName) {
            this.toolName = toolName;
            return this;
        }

        /**
         * Sets the result text of the tool execution.
         * @param text the result of the tool execution.
         * @return the builder.
         */
        @JsonProperty("text")
        public Builder text(String text) {
            this.text = text;
            return this;
        }

        /**
         * Sets whether the tool execution resulted in an error.
         * @param isError whether the tool execution resulted in an error.
         * @return the builder.
         */
        @JsonProperty("isError")
        public Builder isError(Boolean isError) {
            this.isError = isError;
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
