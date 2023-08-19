package dev.langchain4j.memory;

import dev.langchain4j.data.message.ChatMessage;

import java.util.List;

/**
 * Represents the memory (history) of a chat conversation.
 * Since LLMs are stateless, it's necessary to send previous messages to provide the LLM with the conversation context.
 * {@link ChatMemory} helps with retaining messages in the conversation and ensuring they fit within LLM's context window.
 */
public interface ChatMemory {

    /**
     * @return The ID of the {@link ChatMemory}.
     */
    Object id();

    /**
     * Adds a message to the chat memory.
     *
     * @param message The {@link ChatMessage} to add.
     */
    void add(ChatMessage message);

    /**
     * Retrieves messages from the chat memory.
     * Depending on the implementation, it may not return all previously added messages,
     * but rather a subset, a summary, or a combination thereof.
     *
     * @return A list of {@link ChatMessage} objects that represent the current state of the chat memory.
     */
    List<ChatMessage> messages();

    /**
     * Clears the chat memory.
     */
    void clear();
}
