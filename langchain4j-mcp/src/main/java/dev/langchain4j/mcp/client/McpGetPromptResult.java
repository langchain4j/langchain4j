package dev.langchain4j.mcp.client;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

/**
 * The 'GetPromptResult' object from the MCP protocol schema.
 */
public class McpGetPromptResult {

    private final String description;
    private final List<McpPromptMessage> messages;

    @JsonCreator
    public McpGetPromptResult(
            @JsonProperty("description") String description,
            @JsonProperty("messages") List<McpPromptMessage> messages
    ) {
        this.description = description;
        this.messages = messages;
    }

    public String description() {
        return description;
    }

    public List<McpPromptMessage> messages() {
        return messages;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (McpGetPromptResult) obj;
        return Objects.equals(this.description, that.description) &&
                Objects.equals(this.messages, that.messages);
    }

    @Override
    public int hashCode() {
        return Objects.hash(description, messages);
    }

    @Override
    public String toString() {
        return "McpGetPromptResult[" +
                "description=" + description + ", " +
                "messages=" + messages + ']';
    }
}
