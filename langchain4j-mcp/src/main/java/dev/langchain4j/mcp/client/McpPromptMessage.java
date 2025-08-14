package dev.langchain4j.mcp.client;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;

import java.util.Objects;

/**
 * The 'PromptMessage' object from the MCP protocol schema.
 * This can be directly translated to a ChatMessage object from the LangChain4j API.
 */
public class McpPromptMessage {

    private final McpRole role;
    private final McpPromptContent content;

    @JsonCreator
    public McpPromptMessage(
            @JsonProperty("role") McpRole role,
            @JsonProperty("content") McpPromptContent content
    ) {
        this.role = role;
        this.content = content;
    }

    /**
     * Converts this MCP-specific PromptMessage representation to a
     * ChatMessage object from the core LangChain4j API, if possible.
     * This is currently not possible if the role is "assistant" AND
     * the content is something other than text.
     *
     * @throws UnsupportedOperationException if the role is 'assistant' and
     *                                       the content is something other than text.
     */
    public ChatMessage toChatMessage() {
        if (role.equals(McpRole.USER)) {
            return UserMessage.userMessage(content.toContent());
        } else if (role.equals(McpRole.ASSISTANT)) {
            Content convertedContent = content.toContent();
            if (convertedContent instanceof TextContent convertedTextContent) {
                return AiMessage.aiMessage(convertedTextContent.text());
            } else {
                throw new UnsupportedOperationException("Cannot create an AiMessage with content" + " of type "
                        + convertedContent.getClass().getName());
            }
        } else {
            throw new UnsupportedOperationException("Unknown role: " + role);
        }
    }

    public McpRole role() {
        return role;
    }

    public McpPromptContent content() {
        return content;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (McpPromptMessage) obj;
        return Objects.equals(this.role, that.role) &&
                Objects.equals(this.content, that.content);
    }

    @Override
    public int hashCode() {
        return Objects.hash(role, content);
    }

    @Override
    public String toString() {
        return "McpPromptMessage[" +
                "role=" + role + ", " +
                "content=" + content + ']';
    }
}
