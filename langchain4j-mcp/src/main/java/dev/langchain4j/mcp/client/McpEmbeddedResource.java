package dev.langchain4j.mcp.client;

import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.TextContent;

/**
 * The 'EmbeddedResource' object from the MCP protocol schema.
 */
public record McpEmbeddedResource(McpResourceContents resource) implements McpPromptContent {
    @Override
    public Type type() {
        return Type.RESOURCE;
    }

    @Override
    public Content toContent() {
        if (resource.type().equals(McpResourceContents.Type.TEXT)) {
            return TextContent.from(((McpTextResourceContents) resource).text());
        } else {
            throw new UnsupportedOperationException(
                    "Representing blob embedded resources as Content is currently not supported");
        }
    }
}
