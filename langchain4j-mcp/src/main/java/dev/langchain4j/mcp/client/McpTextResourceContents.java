package dev.langchain4j.mcp.client;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * The 'TextResourceContents' object from the MCP protocol schema.
 */
public final class McpTextResourceContents implements McpResourceContents {

    private final String uri;
    private final String text;
    private final String mimeType;

    @JsonCreator
    public McpTextResourceContents(
            @JsonProperty("uri") String uri,
            @JsonProperty("text") String text,
            @JsonProperty("mimeType") String mimeType
    ) {
        this.uri = uri;
        this.text = text;
        this.mimeType = mimeType;
    }

    @Override
    public Type type() {
        return Type.TEXT;
    }

    public String uri() {
        return uri;
    }

    public String text() {
        return text;
    }

    public String mimeType() {
        return mimeType;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (McpTextResourceContents) obj;
        return Objects.equals(this.uri, that.uri) &&
                Objects.equals(this.text, that.text) &&
                Objects.equals(this.mimeType, that.mimeType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uri, text, mimeType);
    }

    @Override
    public String toString() {
        return "McpTextResourceContents[" +
                "uri=" + uri + ", " +
                "text=" + text + ", " +
                "mimeType=" + mimeType + ']';
    }
}
