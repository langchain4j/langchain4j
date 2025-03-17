package dev.langchain4j.mcp.client;

public record TextResourceContents(String uri, String text, String mimeType) implements ResourceContents {

    @Override
    public Type type() {
        return Type.TEXT;
    }

    @Override
    public TextResourceContents asText() {
        return this;
    }

    @Override
    public BlobResourceContents asBlob() {
        throw new IllegalArgumentException("Not a blob");
    }
}
