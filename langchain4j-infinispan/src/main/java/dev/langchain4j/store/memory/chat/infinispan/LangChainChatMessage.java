package dev.langchain4j.store.memory.chat.infinispan;

import dev.langchain4j.data.message.ChatMessageType;
import java.util.List;

/**
 * Protobuf entity representing a single chat message stored in Infinispan.
 *
 * @param type                  the chat message type
 * @param text                  the text content (for SYSTEM, AI, simple USER, simple TOOL_EXECUTION_RESULT)
 * @param name                  the user name (for USER messages)
 * @param thinking              the thinking/reasoning text (for AI messages)
 * @param toolExecutionRequests the tool execution requests (for AI messages)
 * @param toolExecutionId       the tool execution id (for TOOL_EXECUTION_RESULT messages)
 * @param toolName              the tool name (for TOOL_EXECUTION_RESULT messages)
 * @param isError               whether the tool execution resulted in an error (for TOOL_EXECUTION_RESULT messages)
 */
public record LangChainChatMessage(
        ChatMessageType type,
        String text,
        String name,
        String thinking,
        List<LangChainToolExecutionRequest> toolExecutionRequests,
        String toolExecutionId,
        String toolName,
        Boolean isError) {}
