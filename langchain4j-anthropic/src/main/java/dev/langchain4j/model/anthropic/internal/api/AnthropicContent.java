package dev.langchain4j.model.anthropic.internal.api;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.Map;
import java.util.Objects;

/**
 * Represents content in an Anthropic API message.
 * <p>
 * This class supports multiple content types:
 * <ul>
 *   <li>{@code text} - Plain text content</li>
 *   <li>{@code tool_use} - Tool/function call requests</li>
 *   <li>{@code thinking} - Model's thinking/reasoning content</li>
 *   <li>{@code redacted_thinking} - Redacted thinking content</li>
 *   <li>{@code *_tool_result} - Results from server-side tool execution</li>
 * </ul>
 */
@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(SnakeCaseStrategy.class)
public class AnthropicContent {

    /**
     * The type of content (e.g., "text", "tool_use", "thinking").
     */
    public String type;

    /**
     * Text content when type is "text".
     */
    public String text;

    /**
     * Unique identifier when type is "tool_use".
     */
    public String id;

    /**
     * Tool name when type is "tool_use".
     */
    public String name;

    /**
     * Tool input parameters when type is "tool_use".
     */
    public Map<String, Object> input;

    /**
     * Thinking content when type is "thinking".
     */
    public String thinking;

    /**
     * Signature for thinking content when type is "thinking".
     */
    public String signature;

    /**
     * Data when type is "redacted_thinking".
     */
    public String data;

    /**
     * Tool use ID when type ends with "_tool_result".
     */
    public String toolUseId;

    /**
     * Raw content for tool results - structure varies by tool type.
     */
    public Object content;

    public AnthropicContent() {}

    private AnthropicContent(Builder builder) {
        this.type = builder.type;
        this.text = builder.text;
        this.id = builder.id;
        this.name = builder.name;
        this.input = builder.input;
        this.thinking = builder.thinking;
        this.signature = builder.signature;
        this.data = builder.data;
        this.toolUseId = builder.toolUseId;
        this.content = builder.content;
    }

    /**
     * Creates a new builder for constructing {@link AnthropicContent} instances.
     *
     * @return a new {@link Builder} instance
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, text, id, name, input, thinking, signature, data, toolUseId, content);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof final AnthropicContent that)) return false;
        return Objects.equals(type, that.type)
                && Objects.equals(text, that.text)
                && Objects.equals(id, that.id)
                && Objects.equals(name, that.name)
                && Objects.equals(input, that.input)
                && Objects.equals(thinking, that.thinking)
                && Objects.equals(signature, that.signature)
                && Objects.equals(data, that.data)
                && Objects.equals(toolUseId, that.toolUseId)
                && Objects.equals(content, that.content);
    }

    @Override
    public String toString() {
        return "AnthropicContent{" + "type='"
                + type + '\'' + ", text='"
                + text + '\'' + ", id='"
                + id + '\'' + ", name='"
                + name + '\'' + ", input="
                + input + ", thinking='"
                + thinking + '\'' + ", signature='"
                + signature + '\'' + ", data='"
                + data + '\'' + ", toolUseId='"
                + toolUseId + '\'' + ", content="
                + content + '}';
    }

    /**
     * Builder for constructing {@link AnthropicContent} instances.
     */
    public static class Builder {

        private String type;
        private String text;
        private String id;
        private String name;
        private Map<String, Object> input;
        private String thinking;
        private String signature;
        private String data;
        private String toolUseId;
        private Object content;

        /**
         * Sets the content type.
         *
         * @param type the content type (e.g., "text", "tool_use", "thinking")
         * @return this builder for chaining
         */
        public Builder type(String type) {
            this.type = type;
            return this;
        }

        /**
         * Sets the text content.
         *
         * @param text the text content
         * @return this builder for chaining
         */
        public Builder text(String text) {
            this.text = text;
            return this;
        }

        /**
         * Sets the unique identifier for tool use.
         *
         * @param id the tool use identifier
         * @return this builder for chaining
         */
        public Builder id(String id) {
            this.id = id;
            return this;
        }

        /**
         * Sets the tool name.
         *
         * @param name the tool name
         * @return this builder for chaining
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * Sets the tool input parameters.
         *
         * @param input the tool input parameters
         * @return this builder for chaining
         */
        public Builder input(Map<String, Object> input) {
            this.input = input;
            return this;
        }

        /**
         * Sets the thinking content.
         *
         * @param thinking the thinking content
         * @return this builder for chaining
         */
        public Builder thinking(String thinking) {
            this.thinking = thinking;
            return this;
        }

        /**
         * Sets the signature for thinking content.
         *
         * @param signature the thinking signature
         * @return this builder for chaining
         */
        public Builder signature(String signature) {
            this.signature = signature;
            return this;
        }

        /**
         * Sets the data for redacted thinking.
         *
         * @param data the redacted thinking data
         * @return this builder for chaining
         */
        public Builder data(String data) {
            this.data = data;
            return this;
        }

        /**
         * Sets the tool use ID for tool results.
         *
         * @param toolUseId the tool use ID
         * @return this builder for chaining
         */
        public Builder toolUseId(String toolUseId) {
            this.toolUseId = toolUseId;
            return this;
        }

        /**
         * Sets the raw content for tool results.
         *
         * @param content the raw content
         * @return this builder for chaining
         */
        public Builder content(Object content) {
            this.content = content;
            return this;
        }

        /**
         * Builds the {@link AnthropicContent} instance.
         *
         * @return a new {@link AnthropicContent} instance
         */
        public AnthropicContent build() {
            return new AnthropicContent(this);
        }
    }
}
