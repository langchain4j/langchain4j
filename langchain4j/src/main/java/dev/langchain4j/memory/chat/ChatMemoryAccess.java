package dev.langchain4j.memory.chat;

import dev.langchain4j.memory.ChatMemory;

/**
 * Allow to access the {@link ChatMemory} of any AI service extending it.
 */
public interface ChatMemoryAccess {

    /**
     * Returns the {@link ChatMemory} with the given id for this AI service or null if such memory doesn't exist.
     *
     * @param memoryId The ID of the chat memory.
     * @return The {@link ChatMemory} with the given memoryId or null if such memory doesn't exist.
     */
    ChatMemory getChatMemory(Object memoryId);
}
