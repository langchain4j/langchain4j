package dev.langchain4j.mcp.client;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Base64;
import java.util.Objects;

/**
 * The 'BlobResourceContents' object from the MCP protocol schema.
 */
public final class McpBlobResourceContents implements McpResourceContents {

    private final String uri;
    private final String blob;
    private final String mimeType;

    public static McpBlobResourceContents create(String uri, String blob) {
        return new McpBlobResourceContents(uri, blob, null);
    }

    public static McpBlobResourceContents create(String uri, byte[] blob) {
        return new McpBlobResourceContents(uri, Base64.getMimeEncoder().encodeToString(blob), null);
    }

    @JsonCreator
    public McpBlobResourceContents(
            @JsonProperty("uri") String uri,
            @JsonProperty("blob") String blob,
            @JsonProperty("mimeType") String mimeType
    ) {
        if (uri == null) {
            throw new IllegalArgumentException("uri must not be null");
        }
        if (blob == null) {
            throw new IllegalArgumentException("blob must not be null");
        }
        this.uri = uri;
        this.blob = blob;
        this.mimeType = mimeType;
    }

    @Override
    public Type type() {
        return Type.BLOB;
    }

    public String uri() {
        return uri;
    }

    public String blob() {
        return blob;
    }

    public String mimeType() {
        return mimeType;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (McpBlobResourceContents) obj;
        return Objects.equals(this.uri, that.uri) &&
                Objects.equals(this.blob, that.blob) &&
                Objects.equals(this.mimeType, that.mimeType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uri, blob, mimeType);
    }

    @Override
    public String toString() {
        return "McpBlobResourceContents[" +
                "uri=" + uri + ", " +
                "blob=" + blob + ", " +
                "mimeType=" + mimeType + ']';
    }
}
