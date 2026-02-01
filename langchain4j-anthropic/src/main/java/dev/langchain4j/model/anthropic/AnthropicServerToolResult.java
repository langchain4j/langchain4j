package dev.langchain4j.model.anthropic;

import dev.langchain4j.Experimental;
import java.util.Objects;

/**
 * Represents a result from an Anthropic server-executed tool (e.g., web_search, code_execution).
 * <p>
 * Content is stored as raw {@link Object} to be flexible and survive API changes.
 * Users can cast to {@code Map<String, Object>} or {@code List<Map<String, Object>>} as needed.
 *
 * @since 1.10.0
 */
@Experimental
public class AnthropicServerToolResult {

    private final String type;
    private final String toolUseId;
    private final Object content;

    public AnthropicServerToolResult(Builder builder) {
        this.type = builder.type;
        this.toolUseId = builder.toolUseId;
        this.content = builder.content;
    }

    /**
     * The type of server tool result (e.g., "web_search_tool_result", "code_execution_tool_result").
     */
    public String type() {
        return type;
    }

    /**
     * The ID linking this result to the corresponding server_tool_use block.
     */
    public String toolUseId() {
        return toolUseId;
    }

    /**
     * The raw content from the API responses.
     * For web_search: typically a {@code List<Map<String, Object>>} with search results.
     * For code_execution: typically a {@code Map<String, Object>} with stdout, stderr, return_code, etc.
     */
    public Object content() {
        return content;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        AnthropicServerToolResult that = (AnthropicServerToolResult) o;
        return Objects.equals(type, that.type)
                && Objects.equals(toolUseId, that.toolUseId)
                && Objects.equals(content, that.content);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, toolUseId, content);
    }

    @Override
    public String toString() {
        return "AnthropicServerToolResult{" + "type='"
                + type + '\'' + ", toolUseId='"
                + toolUseId + '\'' + ", content="
                + content + '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String type;
        private String toolUseId;
        private Object content;

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder toolUseId(String toolUseId) {
            this.toolUseId = toolUseId;
            return this;
        }

        public Builder content(Object content) {
            this.content = content;
            return this;
        }

        public AnthropicServerToolResult build() {
            return new AnthropicServerToolResult(this);
        }
    }
}
