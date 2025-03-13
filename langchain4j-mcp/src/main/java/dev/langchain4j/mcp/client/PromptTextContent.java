package dev.langchain4j.mcp.client;

public record PromptTextContent(String text) implements PromptContent {

    @Override
    public Type type() {
        return Type.TEXT;
    }

    @Override
    public PromptTextContent asText() {
        return this;
    }

    @Override
    public EmbeddedResource asResource() {
        throw new IllegalArgumentException("Not a resource");
    }

    @Override
    public PromptImageContent asImage() {
        throw new IllegalArgumentException("Not an image content");
    }
}
