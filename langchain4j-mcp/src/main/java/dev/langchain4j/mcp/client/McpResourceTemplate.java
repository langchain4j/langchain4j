package dev.langchain4j.mcp.client;

import static dev.langchain4j.internal.Utils.copy;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.internal.Utils;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The 'ResourceTemplate' object from the MCP protocol schema.
 */
public class McpResourceTemplate {

    private final String uriTemplate;
    private final String name;
    private final String description;
    private final String mimeType;
    private final Map<String, Object> metadata;
    private final List<McpIcon> icons;

    public McpResourceTemplate(
            @JsonProperty("uriTemplate") String uriTemplate,
            @JsonProperty("name") String name,
            @JsonProperty("description") String description,
            @JsonProperty("mimeType") String mimeType) {
        this(uriTemplate, name, description, mimeType, null, null);
    }

    @JsonCreator
    public McpResourceTemplate(
            @JsonProperty("uriTemplate") String uriTemplate,
            @JsonProperty("name") String name,
            @JsonProperty("description") String description,
            @JsonProperty("mimeType") String mimeType,
            @JsonProperty("_meta") Map<String, Object> metadata,
            @JsonProperty("icons") List<McpIcon> icons) {
        this.uriTemplate = Utils.warnIfNullOrBlank(uriTemplate, "uriTemplate", McpResourceTemplate.class);
        this.name = Utils.warnIfNullOrBlank(name, "name", McpResourceTemplate.class);
        this.description = description;
        this.mimeType = mimeType;
        this.metadata = copy(metadata);
        this.icons = icons == null ? List.of() : List.copyOf(icons);
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

    public Map<String, Object> metadata() {
        return metadata;
    }

    public List<McpIcon> icons() {
        return icons;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (McpResourceTemplate) obj;
        return Objects.equals(this.uriTemplate, that.uriTemplate)
                && Objects.equals(this.name, that.name)
                && Objects.equals(this.description, that.description)
                && Objects.equals(this.mimeType, that.mimeType)
                && Objects.equals(this.metadata, that.metadata)
                && Objects.equals(this.icons, that.icons);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uriTemplate, name, description, mimeType, metadata, icons);
    }

    @Override
    public String toString() {
        return "McpResourceTemplate[" + "uriTemplate="
                + uriTemplate + ", " + "name="
                + name + ", " + "description="
                + description + ", " + "mimeType="
                + mimeType + ", " + "metadata="
                + metadata + ", " + "icons="
                + icons + ']';
    }
}
