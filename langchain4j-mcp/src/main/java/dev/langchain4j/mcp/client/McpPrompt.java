package dev.langchain4j.mcp.client;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

/**
 * The 'Prompt' object from the MCP protocol schema.
 * It describes a declaration of a prompt, not its actual contents.
 * It contains a name, description and a list of arguments relevant for rendering an instance of the prompt.
 */
public class McpPrompt {

    private final String name;
    private final String description;
    private final List<McpPromptArgument> arguments;

    @JsonCreator
    public McpPrompt(
            @JsonProperty("name") String name,
            @JsonProperty("description") String description,
            @JsonProperty("arguments") List<McpPromptArgument> arguments
    ) {
        this.name = name;
        this.description = description;
        this.arguments = arguments;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public List<McpPromptArgument> arguments() {
        return arguments;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (McpPrompt) obj;
        return Objects.equals(this.name, that.name) &&
                Objects.equals(this.description, that.description) &&
                Objects.equals(this.arguments, that.arguments);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, description, arguments);
    }

    @Override
    public String toString() {
        return "McpPrompt[" +
                "name=" + name + ", " +
                "description=" + description + ", " +
                "arguments=" + arguments + ']';
    }
}
