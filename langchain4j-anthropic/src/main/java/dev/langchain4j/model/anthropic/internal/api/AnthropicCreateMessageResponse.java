package dev.langchain4j.model.anthropic.internal.api;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;
import java.util.Objects;

/**
 * Response object from the Anthropic Create Message API.
 * <p>
 * Contains the model's response including generated content, usage statistics,
 * and metadata about the completion.
 */
@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(SnakeCaseStrategy.class)
public class AnthropicCreateMessageResponse {

    /**
     * Unique identifier for this message.
     */
    public String id;

    /**
     * The type of response (typically "message").
     */
    public String type;

    /**
     * The role of the responder (typically "assistant").
     */
    public String role;

    /**
     * The content blocks in the response.
     */
    public List<AnthropicContent> content;

    /**
     * The model that generated the response.
     */
    public String model;

    /**
     * The reason the model stopped generating (e.g., "end_turn", "max_tokens", "tool_use").
     */
    public String stopReason;

    /**
     * The stop sequence that caused generation to stop, if applicable.
     */
    public String stopSequence;

    /**
     * Token usage statistics for the request and response.
     */
    public AnthropicUsage usage;

    /**
     * Default constructor.
     */
    public AnthropicCreateMessageResponse() {}

    private AnthropicCreateMessageResponse(Builder builder) {
        this.id = builder.id;
        this.type = builder.type;
        this.role = builder.role;
        this.content = builder.content;
        this.model = builder.model;
        this.stopReason = builder.stopReason;
        this.stopSequence = builder.stopSequence;
        this.usage = builder.usage;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, type, role, content, model, stopReason, stopSequence, usage);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof AnthropicCreateMessageResponse)) return false;
        AnthropicCreateMessageResponse that = (AnthropicCreateMessageResponse) obj;
        return Objects.equals(id, that.id)
                && Objects.equals(type, that.type)
                && Objects.equals(role, that.role)
                && Objects.equals(content, that.content)
                && Objects.equals(model, that.model)
                && Objects.equals(stopReason, that.stopReason)
                && Objects.equals(stopSequence, that.stopSequence)
                && Objects.equals(usage, that.usage);
    }

    @Override
    public String toString() {
        return "AnthropicCreateMessageResponse{" + "id='"
                + id + '\'' + ", type='"
                + type + '\'' + ", role='"
                + role + '\'' + ", content="
                + content + ", model='"
                + model + '\'' + ", stopReason='"
                + stopReason + '\'' + ", stopSequence='"
                + stopSequence + '\'' + ", usage="
                + usage + '}';
    }

    /**
     * Creates a new builder for constructing {@link AnthropicCreateMessageResponse} instances.
     *
     * @return a new {@link Builder} instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for constructing {@link AnthropicCreateMessageResponse} instances.
     */
    public static class Builder {
        private String id;
        private String type;
        private String role;
        private List<AnthropicContent> content;
        private String model;
        private String stopReason;
        private String stopSequence;
        private AnthropicUsage usage;

        /**
         * Sets the unique identifier.
         *
         * @param id the message identifier
         * @return this builder for chaining
         */
        public Builder id(String id) {
            this.id = id;
            return this;
        }

        /**
         * Sets the response type.
         *
         * @param type the response type
         * @return this builder for chaining
         */
        public Builder type(String type) {
            this.type = type;
            return this;
        }

        /**
         * Sets the role.
         *
         * @param role the role
         * @return this builder for chaining
         */
        public Builder role(String role) {
            this.role = role;
            return this;
        }

        /**
         * Sets the content blocks.
         *
         * @param content the content blocks
         * @return this builder for chaining
         */
        public Builder content(List<AnthropicContent> content) {
            this.content = content;
            return this;
        }

        /**
         * Sets the model name.
         *
         * @param model the model name
         * @return this builder for chaining
         */
        public Builder model(String model) {
            this.model = model;
            return this;
        }

        /**
         * Sets the stop reason.
         *
         * @param stopReason the stop reason
         * @return this builder for chaining
         */
        public Builder stopReason(String stopReason) {
            this.stopReason = stopReason;
            return this;
        }

        /**
         * Sets the stop sequence.
         *
         * @param stopSequence the stop sequence
         * @return this builder for chaining
         */
        public Builder stopSequence(String stopSequence) {
            this.stopSequence = stopSequence;
            return this;
        }

        /**
         * Sets the usage statistics.
         *
         * @param usage the usage statistics
         * @return this builder for chaining
         */
        public Builder usage(AnthropicUsage usage) {
            this.usage = usage;
            return this;
        }

        /**
         * Builds the {@link AnthropicCreateMessageResponse} instance.
         *
         * @return a new {@link AnthropicCreateMessageResponse} instance
         */
        public AnthropicCreateMessageResponse build() {
            return new AnthropicCreateMessageResponse(this);
        }
    }
}
