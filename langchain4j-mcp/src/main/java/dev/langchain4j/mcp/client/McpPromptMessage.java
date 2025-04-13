package dev.langchain4j.mcp.client;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;

/**
 * The 'PromptMessage' object from the MCP protocol schema.
 * This can be directly translated to a ChatMessage object from the LangChain4j API.
 */
public record McpPromptMessage(McpRole role, McpPromptContent content) {

    /**
     * Converts this MCP-specific PromptMessage representation to a
     * ChatMessage object from the core LangChain4j API, if possible.
     * This is currently not possible if the role is "assistant" AND
     * the content is something other than text.
     *
     * @throws UnsupportedOperationException if the role is 'assistant' and
     * the content is something other than text.
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
}
