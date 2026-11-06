package dev.langchain4j.store.memory.chat.infinispan;

import java.util.List;

/**
 * Protobuf entity representing a chat memory entry stored in Infinispan.
 *
 * @param memoryId the chat memory identifier
 * @param messages the list of chat messages
 */
public record LangChainChatMemoryItem(String memoryId, List<LangChainChatMessage> messages) {}
