package dev.langchain4j.mcp.client;

import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.TextContent;

/**
 * The 'TextContent' object from the MCP protocol schema.
 */
public record McpTextContent(String text) implements McpPromptContent {

    @Override
    public Type type() {
        return Type.TEXT;
    }

    @Override
    public Content toContent() {
        return TextContent.from(text);
    }
}
