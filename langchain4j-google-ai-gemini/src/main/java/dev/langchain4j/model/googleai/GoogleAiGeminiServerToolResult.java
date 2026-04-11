package dev.langchain4j.model.googleai;

import dev.langchain4j.Experimental;
import java.util.Objects;

/**
 * Represents a result from a Gemini server-executed tool.
 *
 * @since 1.10.0
 */
@Experimental
public class GoogleAiGeminiServerToolResult {

    private final String type;
    private final String toolUseId;
    private final Object content;

    public GoogleAiGeminiServerToolResult(Builder builder) {
        this.type = builder.type;
        this.toolUseId = builder.toolUseId;
        this.content = builder.content;
    }

    public String type() {
        return type;
    }

    public String toolUseId() {
        return toolUseId;
    }

    public Object content() {
        return content;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        GoogleAiGeminiServerToolResult that = (GoogleAiGeminiServerToolResult) o;
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
        return "GoogleAiGeminiServerToolResult{"
                + "type='" + type + '\''
                + ", toolUseId='" + toolUseId + '\''
                + ", content=" + content
                + '}';
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

        public GoogleAiGeminiServerToolResult build() {
            return new GoogleAiGeminiServerToolResult(this);
        }
    }
}
