package dev.langchain4j.mcp.client;

import static dev.langchain4j.internal.Utils.copy;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
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
    private final Map<String, Object> metadata;
    private final List<McpIcon> icons;

    public McpPrompt(
            @JsonProperty("name") String name,
            @JsonProperty("description") String description,
            @JsonProperty("arguments") List<McpPromptArgument> arguments) {
        this(name, description, arguments, null, null);
    }

    @JsonCreator
    public McpPrompt(
            @JsonProperty("name") String name,
            @JsonProperty("description") String description,
            @JsonProperty("arguments") List<McpPromptArgument> arguments,
            @JsonProperty("_meta") Map<String, Object> metadata,
            @JsonProperty("icons") List<McpIcon> icons) {
        this.name = name;
        this.description = description;
        this.arguments = arguments;
        this.metadata = copy(metadata);
        this.icons = icons == null ? List.of() : List.copyOf(icons);
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
        var that = (McpPrompt) obj;
        return Objects.equals(this.name, that.name)
                && Objects.equals(this.description, that.description)
                && Objects.equals(this.arguments, that.arguments)
                && Objects.equals(this.metadata, that.metadata)
                && Objects.equals(this.icons, that.icons);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, description, arguments, metadata, icons);
    }

    @Override
    public String toString() {
        return "McpPrompt[" + "name="
                + name + ", " + "description="
                + description + ", " + "arguments="
                + arguments + ", " + "metadata="
                + metadata + ", " + "icons="
                + icons + ']';
    }
}
