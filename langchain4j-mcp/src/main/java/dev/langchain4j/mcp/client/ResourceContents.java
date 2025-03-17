package dev.langchain4j.mcp.client;

public sealed interface ResourceContents permits TextResourceContents, BlobResourceContents {

    Type type();

    TextResourceContents asText();

    BlobResourceContents asBlob();

    enum Type {
        TEXT,
        BLOB
    }
}
