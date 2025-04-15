package dev.langchain4j.mcp.client;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * The 'ResourceTemplate' object from the MCP protocol schema.
 */
public class McpResourceTemplate {

    private final String uriTemplate;
    private final String name;
    private final String description;
    private final String mimeType;

    @JsonCreator
    public McpResourceTemplate(
            @JsonProperty("uriTemplate") String uriTemplate,
            @JsonProperty("name") String name,
            @JsonProperty("description") String description,
            @JsonProperty("mimeType") String mimeType
    ) {
        this.uriTemplate = uriTemplate;
        this.name = name;
        this.description = description;
        this.mimeType = mimeType;
    }

    public String uriTemplate() {
        return uriTemplate;
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
        var that = (McpResourceTemplate) obj;
        return Objects.equals(this.uriTemplate, that.uriTemplate) &&
                Objects.equals(this.name, that.name) &&
                Objects.equals(this.description, that.description) &&
                Objects.equals(this.mimeType, that.mimeType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uriTemplate, name, description, mimeType);
    }

    @Override
    public String toString() {
        return "McpResourceTemplate[" +
                "uriTemplate=" + uriTemplate + ", " +
                "name=" + name + ", " +
                "description=" + description + ", " +
                "mimeType=" + mimeType + ']';
    }
}
