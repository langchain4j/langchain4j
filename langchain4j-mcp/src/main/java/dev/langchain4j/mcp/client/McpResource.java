package dev.langchain4j.mcp.client;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.mutableCopy;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.internal.Utils;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The 'Resource' object from the MCP protocol schema.
 */
public class McpResource {
    private final String uri;
    private final String name;
    private final String description;
    private final String mimeType;
    private final Map<String, Object> metadata;
    private final List<McpIcon> icons;

    public McpResource(
            @JsonProperty("uri") String uri,
            @JsonProperty("name") String name,
            @JsonProperty("description") String description,
            @JsonProperty("mimeType") String mimeType) {
        this(uri, name, description, mimeType, null, null);
    }

    @JsonCreator
    public McpResource(
            @JsonProperty("uri") String uri,
            @JsonProperty("name") String name,
            @JsonProperty("description") String description,
            @JsonProperty("mimeType") String mimeType,
            @JsonProperty("_meta") Map<String, Object> metadata,
            @JsonProperty("icons") List<McpIcon> icons) {
        this.uri = Utils.warnIfNullOrBlank(uri, "uri", McpResource.class);
        this.name = Utils.warnIfNullOrBlank(name, "name", McpResource.class);
        this.description = description;
        this.mimeType = mimeType;
        this.metadata = copy(mutableCopy(metadata));
        this.icons = icons == null ? List.of() : List.copyOf(icons);
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
        var that = (McpResource) obj;
        return Objects.equals(this.uri, that.uri)
                && Objects.equals(this.name, that.name)
                && Objects.equals(this.description, that.description)
                && Objects.equals(this.mimeType, that.mimeType)
                && Objects.equals(this.metadata, that.metadata)
                && Objects.equals(this.icons, that.icons);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uri, name, description, mimeType, metadata, icons);
    }

    @Override
    public String toString() {
        return "McpResource[" + "uri="
                + uri + ", " + "name="
                + name + ", " + "description="
                + description + ", " + "mimeType="
                + mimeType + ", " + "metadata="
                + metadata + ", " + "icons="
                + icons + ']';
    }
}
