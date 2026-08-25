package dev.langchain4j.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import dev.langchain4j.Internal;

/**
 * Corresponds to the {@code Implementation} type from the MCP schema.
 */
@Internal
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class McpImplementation {

    private String name;
    private String version;
    private String title;

    public McpImplementation() {}

    public McpImplementation(String name, String version) {
        this(name, version, null);
    }

    public McpImplementation(String name, String version, String title) {
        this.name = name;
        this.version = version;
        this.title = title;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}
