package dev.langchain4j.mcp.client;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;

import java.util.Objects;

/**
 * The 'ImageContent' object from the MCP protocol schema.
 */
public final class McpImageContent implements McpPromptContent {

    private final String data;
    private final String mimeType;

    @JsonCreator
    public McpImageContent(
            @JsonProperty("data") String data,
            @JsonProperty("mimeType") String mimeType
    ) {
        this.data = data;
        this.mimeType = mimeType;
    }

    @Override
    public Type type() {
        return Type.IMAGE;
    }

    @Override
    public Content toContent() {
        return ImageContent.from(data, mimeType);
    }

    public String data() {
        return data;
    }

    public String mimeType() {
        return mimeType;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (McpImageContent) obj;
        return Objects.equals(this.data, that.data) &&
                Objects.equals(this.mimeType, that.mimeType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(data, mimeType);
    }

    @Override
    public String toString() {
        return "McpImageContent[" +
                "data=" + data + ", " +
                "mimeType=" + mimeType + ']';
    }
}
