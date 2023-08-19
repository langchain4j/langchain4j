package dev.langchain4j.memory.chat;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.service.MemoryId;

/**
 * Provides instances of {@link ChatMemory}.
 * Intended to be used with {@link dev.langchain4j.service.AiServices}.
 */
@FunctionalInterface
public interface ChatMemoryProvider {

    /**
     * Provides an instance of {@link ChatMemory}.
     * This method is called each time an AI Service method (having a parameter annotated with {@link MemoryId})
     * is called with a previously unseen memory ID.
     * Once the {@link ChatMemory} instance is returned, it's retained in memory and managed by {@link dev.langchain4j.service.AiServices}.
     *
     * @param memoryId The ID of the chat memory.
     * @return A {@link ChatMemory} instance.
     * @see MemoryId
     */
    ChatMemory get(Object memoryId);
}
