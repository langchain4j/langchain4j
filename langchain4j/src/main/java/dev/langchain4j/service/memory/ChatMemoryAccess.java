package dev.langchain4j.service.memory;

import dev.langchain4j.memory.ChatMemory;

/**
 * Allow to access the {@link ChatMemory} of any AI service extending it.
 */
public interface ChatMemoryAccess {

    /**
     * Returns the {@link ChatMemory} with the given id for this AI service or null if such memory doesn't exist.
     *
     * @param memoryId The id of the chat memory.
     * @return The {@link ChatMemory} with the given memoryId or null if such memory doesn't exist.
     */
    ChatMemory getChatMemory(Object memoryId);

    /**
     * Evicts the {@link ChatMemory} with the given id.
     *
     * @param memoryId The id of the chat memory to be evicted.
     * @return true if {@link ChatMemory} with the given id existed, and it was successfully evicted, false otherwise.
     * @throws UnsupportedOperationException if this AI service has a single chat memory, configured with
     *         {@code chatMemory(...)} and shared by all the memory ids: it cannot be evicted, only a chat memory
     *         created for a memory id by a {@code chatMemoryProvider(...)} can.
     */
    boolean evictChatMemory(Object memoryId);
}
