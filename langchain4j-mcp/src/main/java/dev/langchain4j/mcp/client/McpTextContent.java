package dev.langchain4j.mcp.client;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.TextContent;

import java.util.Objects;

/**
 * The 'TextContent' object from the MCP protocol schema.
 */
public final class McpTextContent implements McpPromptContent {

    private final String text;

    @JsonCreator
    public McpTextContent(@JsonProperty("text") String text) {
        this.text = text;
    }

    @Override
    public Type type() {
        return Type.TEXT;
    }

    @Override
    public Content toContent() {
        return TextContent.from(text);
    }

    public String text() {
        return text;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (McpTextContent) obj;
        return Objects.equals(this.text, that.text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(text);
    }

    @Override
    public String toString() {
        return "McpTextContent[" +
                "text=" + text + ']';
    }
}
