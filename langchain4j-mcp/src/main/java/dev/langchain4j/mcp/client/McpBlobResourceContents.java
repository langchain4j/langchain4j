package dev.langchain4j.mcp.client;

import java.util.Base64;

/**
 * The 'BlobResourceContents' object from the MCP protocol schema.
 */
public record McpBlobResourceContents(String uri, String blob, String mimeType) implements McpResourceContents {

    public static McpBlobResourceContents create(String uri, String blob) {
        return new McpBlobResourceContents(uri, blob, null);
    }

    public static McpBlobResourceContents create(String uri, byte[] blob) {
        return new McpBlobResourceContents(uri, Base64.getMimeEncoder().encodeToString(blob), null);
    }

    public McpBlobResourceContents {
        if (uri == null) {
            throw new IllegalArgumentException("uri must not be null");
        }
        if (blob == null) {
            throw new IllegalArgumentException("blob must not be null");
        }
    }

    @Override
    public Type type() {
        return Type.BLOB;
    }
}
