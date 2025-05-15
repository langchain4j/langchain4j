package dev.langchain4j.mcp.client;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * The 'Resource' object from the MCP protocol schema.
 */
public class McpResource {

    private final String uri;
    private final String name;
    private final String description;
    private final String mimeType;

    @JsonCreator
    public McpResource(
            @JsonProperty("uri") String uri,
            @JsonProperty("name") String name,
            @JsonProperty("description") String description,
            @JsonProperty("mimeType") String mimeType
    ) {
        this.uri = uri;
        this.name = name;
        this.description = description;
        this.mimeType = mimeType;
    }

    public String uri() {
        return uri;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public String mimeType() {
        return mimeType;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (McpResource) obj;
        return Objects.equals(this.uri, that.uri) &&
                Objects.equals(this.name, that.name) &&
                Objects.equals(this.description, that.description) &&
                Objects.equals(this.mimeType, that.mimeType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uri, name, description, mimeType);
    }

    @Override
    public String toString() {
        return "McpResource[" +
                "uri=" + uri + ", " +
                "name=" + name + ", " +
                "description=" + description + ", " +
                "mimeType=" + mimeType + ']';
    }
}
