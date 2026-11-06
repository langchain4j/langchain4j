package dev.langchain4j.agentic.scope;

import dev.langchain4j.service.memory.ChatMemoryAccess;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;

/**
 * Allow to access the {@link AgenticScope} of any agent extending it.
 */
public interface AgenticScopeAccess {

    /**
     * Returns the {@link AgenticScope} with the given id for this AI service or null if such memory doesn't exist.
     *
     * @param memoryId The id of the {@link AgenticScope}.
     * @return The {@link AgenticScope} with the given memoryId or null if such memory doesn't exist.
     */
    AgenticScope getAgenticScope(Object memoryId);

    /**
     * Evicts the {@link AgenticScope} with the given id, together with the chat memories that the agents of this
     * agentic system hold for that id, with the meaning of {@link ChatMemoryAccess#evictChatMemory}: the chat memory
     * objects are forgotten, the messages they stored stay in their {@link ChatMemoryStore}. Use
     * {@link #deleteAgenticScope} to delete the messages too. An AI agent configured with a single chat memory,
     * shared by all the memory ids, has nothing of the scope to evict and is left alone by both methods.
     *
     * @param memoryId The id of the {@link AgenticScope} to be evicted.
     * @return true if {@link AgenticScope} with the given id existed, and it was successfully evicted, false otherwise.
     */
    boolean evictAgenticScope(Object memoryId);

    /**
     * Evicts the {@link AgenticScope} with the given id like {@link #evictAgenticScope}, and deletes the chat
     * memories that the agents of this agentic system hold for that id: each one is cleared, which deletes its
     * messages from its {@link ChatMemoryStore}, and then evicted. The listeners are notified of the destruction of
     * the scope before the chat memories are touched, and the scope is evicted even when the deletion of a chat
     * memory fails: the failure is rethrown after the other chat memories have been deleted.
     * <p>
     * Only the chat memories created in this JVM for that id are reached: the messages stored by another node, or
     * before a restart, have to be deleted through their store.
     *
     * @param memoryId The id of the {@link AgenticScope} to be deleted.
     * @return true if {@link AgenticScope} with the given id existed, and it was successfully evicted, false otherwise.
     */
    boolean deleteAgenticScope(Object memoryId);
}
