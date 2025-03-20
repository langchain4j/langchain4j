package dev.langchain4j.mcp.client;

import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;

/**
 * The 'ImageContent' object from the MCP protocol schema.
 */
public record McpImageContent(String data, String mimeType) implements McpPromptContent {

    @Override
    public Type type() {
        return Type.IMAGE;
    }

    @Override
    public Content toContent() {
        return ImageContent.from(data, mimeType);
    }
}
