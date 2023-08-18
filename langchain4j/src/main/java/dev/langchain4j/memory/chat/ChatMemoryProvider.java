package dev.langchain4j.memory.chat;

import dev.langchain4j.memory.ChatMemory;

/**
 * Provides instances of {@link ChatMemory}.
 * Intended to be used with {@link dev.langchain4j.service.AiServices}.
 */
@FunctionalInterface
public interface ChatMemoryProvider {

    /**
     * Provides an instance of {@link ChatMemory} associated with the given user ID.
     * This method is called each time an AI Service method (with a parameter annotated with {@link dev.langchain4j.service.UserId})
     * is called with a previously unseen userId.
     * Once the {@link ChatMemory} instance is returned, it's retained in memory and managed by {@link dev.langchain4j.service.AiServices}.
     *
     * @param userId The ID of the user.
     * @return A {@link ChatMemory} instance belonging to the specified user.
     * @see dev.langchain4j.service.UserId
     */
    ChatMemory chatMemoryOf(Object userId);
}
