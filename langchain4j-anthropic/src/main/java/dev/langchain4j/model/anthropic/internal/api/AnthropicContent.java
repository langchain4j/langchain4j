package dev.langchain4j.model.anthropic.internal.api;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Represents a content block in Anthropic API requests and responses.
 *
 * <p>This class supports multiple content types, each using a different subset of fields:</p>
 * <ul>
 *   <li>{@code text} - Text content with the {@link #text()} field</li>
 *   <li>{@code tool_use} - Tool invocation with {@link #id()}, {@link #name()}, and {@link #input()}</li>
 *   <li>{@code thinking} - Model thinking output with {@link #thinking()} and {@link #signature()}</li>
 *   <li>{@code redacted_thinking} - Redacted thinking with {@link #data()}</li>
 *   <li>{@code *_tool_result} - Server tool results (e.g., {@code web_search_tool_result}) with
 *       {@link #toolUseId()} and {@link #content()}</li>
 * </ul>
 *
 * @see AnthropicContent.Builder
 */
@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(SnakeCaseStrategy.class)
public record AnthropicContent(
        String type,

        // when type = "text"
        @Nullable String text,

        // when type = "tool_use"
        @Nullable String id,
        @Nullable String name,
        @Nullable Map<String, Object> input,

        // when type = "thinking"
        @Nullable String thinking,
        @Nullable String signature,

        // when type = "redacted_thinking"
        @Nullable String data,

        // when type ends with "_tool_result" (e.g., web_search_tool_result, code_execution_tool_result)
        @Nullable String toolUseId,

        // Raw content - structure varies by tool type
        Object content) {

    /**
     * Creates a new builder for constructing {@link AnthropicContent} instances.
     *
     * @return a new {@link Builder} instance
     */
    public static Builder builder() {
        return new Builder();
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

        private Builder() {}

        /**
         * Sets the content type.
         *
         * <p>Common types include:</p>
         * <ul>
         *   <li>{@code "text"} - Plain text content</li>
         *   <li>{@code "tool_use"} - Tool invocation request</li>
         *   <li>{@code "thinking"} - Model thinking output</li>
         *   <li>{@code "redacted_thinking"} - Redacted thinking content</li>
         *   <li>{@code "*_tool_result"} - Server tool results (e.g., {@code web_search_tool_result})</li>
         * </ul>
         *
         * @param type the content type identifier
         * @return this builder for method chaining
         */
        public Builder type(String type) {
            this.type = type;
            return this;
        }

        /**
         * Sets the text content.
         *
         * <p>Used when {@code type} is {@code "text"}.</p>
         *
         * @param text the text content
         * @return this builder for method chaining
         */
        public Builder text(String text) {
            this.text = text;
            return this;
        }

        /**
         * Sets the tool use ID.
         *
         * <p>Used when {@code type} is {@code "tool_use"}. This is a unique identifier
         * for the tool invocation.</p>
         *
         * @param id the tool use identifier
         * @return this builder for method chaining
         */
        public Builder id(String id) {
            this.id = id;
            return this;
        }

        /**
         * Sets the tool name.
         *
         * <p>Used when {@code type} is {@code "tool_use"}.</p>
         *
         * @param name the name of the tool being invoked
         * @return this builder for method chaining
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * Sets the tool input parameters.
         *
         * <p>Used when {@code type} is {@code "tool_use"}. Contains the arguments
         * to pass to the tool.</p>
         *
         * @param input a map of input parameter names to values
         * @return this builder for method chaining
         */
        public Builder input(Map<String, Object> input) {
            this.input = input;
            return this;
        }

        /**
         * Sets the thinking content.
         *
         * <p>Used when {@code type} is {@code "thinking"}. Contains the model's
         * internal reasoning process.</p>
         *
         * @param thinking the thinking text
         * @return this builder for method chaining
         */
        public Builder thinking(String thinking) {
            this.thinking = thinking;
            return this;
        }

        /**
         * Sets the thinking signature.
         *
         * <p>Used when {@code type} is {@code "thinking"}. A cryptographic signature
         * that can be used to verify the thinking content.</p>
         *
         * @param signature the thinking signature
         * @return this builder for method chaining
         */
        public Builder signature(String signature) {
            this.signature = signature;
            return this;
        }

        /**
         * Sets the redacted thinking data.
         *
         * <p>Used when {@code type} is {@code "redacted_thinking"}. Contains
         * opaque data representing redacted thinking content.</p>
         *
         * @param data the redacted thinking data
         * @return this builder for method chaining
         */
        public Builder data(String data) {
            this.data = data;
            return this;
        }

        /**
         * Sets the tool use ID for server tool results.
         *
         * <p>Used when {@code type} ends with {@code "_tool_result"} (e.g.,
         * {@code web_search_tool_result}, {@code code_execution_tool_result}).
         * References the original tool invocation.</p>
         *
         * @param toolUseId the ID of the tool invocation this result corresponds to
         * @return this builder for method chaining
         */
        public Builder toolUseId(String toolUseId) {
            this.toolUseId = toolUseId;
            return this;
        }

        /**
         * Sets the raw content.
         *
         * <p>Used for server tool results where the structure varies by tool type.
         * The actual structure depends on the specific tool.</p>
         *
         * @param content the raw content object
         * @return this builder for method chaining
         */
        public Builder content(Object content) {
            this.content = content;
            return this;
        }

        /**
         * Builds a new {@link AnthropicContent} instance with the configured values.
         *
         * @return a new {@link AnthropicContent} instance
         */
        public AnthropicContent build() {
            return new AnthropicContent(type, text, id, name, input, thinking, signature, data, toolUseId, content);
        }
    }
}
