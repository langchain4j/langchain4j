package dev.langchain4j.mcp.client;

import dev.langchain4j.data.message.ImageContent;

public record PromptImageContent(String data, String mimeType) implements PromptContent {

    @Override
    public Type type() {
        return Type.IMAGE;
    }

    @Override
    public PromptTextContent asText() {
        throw new IllegalArgumentException("Not textual content");
    }

    @Override
    public EmbeddedResource asResource() {
        throw new IllegalArgumentException("Not a resource");
    }

    @Override
    public PromptImageContent asImage() {
        return this;
    }

    /**
     * Transforms this MCP-specific representation of ImageContent to a regular ImageContent from
     * langchain4j core APIs.
     */
    public ImageContent asImageContent() {
        return new ImageContent(data, mimeType);
    }
}
