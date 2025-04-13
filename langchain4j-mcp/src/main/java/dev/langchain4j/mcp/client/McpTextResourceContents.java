package dev.langchain4j.mcp.client;

/**
 * The 'TextResourceContents' object from the MCP protocol schema.
 */
public record McpTextResourceContents(String uri, String text, String mimeType) implements McpResourceContents {

    @Override
    public Type type() {
        return Type.TEXT;
    }
}
