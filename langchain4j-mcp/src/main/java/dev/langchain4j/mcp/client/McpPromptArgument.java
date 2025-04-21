package dev.langchain4j.mcp.client;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * The 'PromptArgument' object from the MCP protocol schema.
 */
public class McpPromptArgument {

    private final String name;
    private final String description;
    private final boolean required;

    @JsonCreator
    public McpPromptArgument(
            @JsonProperty("name") String name,
            @JsonProperty("description") String description,
            @JsonProperty("required") boolean required
    ) {
        this.name = name;
        this.description = description;
        this.required = required;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public boolean required() {
        return required;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (McpPromptArgument) obj;
        return Objects.equals(this.name, that.name) &&
                Objects.equals(this.description, that.description) &&
                this.required == that.required;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, description, required);
    }

    @Override
    public String toString() {
        return "McpPromptArgument[" +
                "name=" + name + ", " +
                "description=" + description + ", " +
                "required=" + required + ']';
    }
}
