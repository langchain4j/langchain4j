package dev.langchain4j.mcp.client;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.TextContent;

import java.util.Objects;

/**
 * The 'EmbeddedResource' object from the MCP protocol schema.
 */
public final class McpEmbeddedResource implements McpPromptContent {

    private final McpResourceContents resource;

    @JsonCreator
    public McpEmbeddedResource(@JsonProperty("resource") McpResourceContents resource) {
        this.resource = resource;
    }

    @Override
    public Type type() {
        return Type.RESOURCE;
    }

    @Override
    public Content toContent() {
        if (resource.type().equals(McpResourceContents.Type.TEXT)) {
            return TextContent.from(((McpTextResourceContents) resource).text());
        } else {
            throw new UnsupportedOperationException(
                    "Representing blob embedded resources as Content is currently not supported");
        }
    }

    public McpResourceContents resource() {
        return resource;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (McpEmbeddedResource) obj;
        return Objects.equals(this.resource, that.resource);
    }

    @Override
    public int hashCode() {
        return Objects.hash(resource);
    }

    @Override
    public String toString() {
        return "McpEmbeddedResource[" +
                "resource=" + resource + ']';
    }
}
