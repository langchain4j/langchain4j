package dev.langchain4j.model.openaiofficial;

import dev.langchain4j.Experimental;
import java.util.Objects;

/**
 * Result emitted by an OpenAI Responses API server-side tool.
 *
 * <p>Content is stored as raw {@link Object} to remain compatible with provider-side schema changes.
 */
@Experimental
public class OpenAiOfficialServerToolResult {

    private final String type;
    private final String toolUseId;
    private final Object content;

    public OpenAiOfficialServerToolResult(Builder builder) {
        this.type = builder.type;
        this.toolUseId = builder.toolUseId;
        this.content = builder.content;
    }

    public String type() {
        return type;
    }

    /**
     * The identifier linking this result to the corresponding OpenAI server tool call.
     */
    public String toolUseId() {
        return toolUseId;
    }

    /**
     * The raw content returned by the OpenAI Responses API.
     */
    public Object content() {
        return content;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        OpenAiOfficialServerToolResult that = (OpenAiOfficialServerToolResult) o;
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
        return "OpenAiOfficialServerToolResult{" + "type='"
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

        public OpenAiOfficialServerToolResult build() {
            return new OpenAiOfficialServerToolResult(this);
        }
    }
}
