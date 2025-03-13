package dev.langchain4j.mcp.client;

public record EmbeddedResource(ResourceContents resource) implements PromptContent {
    @Override
    public Type type() {
        return Type.RESOURCE;
    }

    @Override
    public PromptTextContent asText() {
        throw new IllegalArgumentException("Not textual content");
    }

    @Override
    public EmbeddedResource asResource() {
        return this;
    }

    @Override
    public PromptImageContent asImage() {
        throw new IllegalArgumentException("Not an image content");
    }
}
