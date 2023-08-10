package dev.langchain4j.memory;

import dev.langchain4j.data.message.ChatMessage;

import java.util.List;

/**
 * Represents the memory of a chat (chat history).
 * As LLMs are inherently stateless, this interface is useful for tracking the conversation.
 */
public interface ChatMemory {

    /**
     * Adds a message to the chat memory.
     *
     * @param message The ChatMessage to add.
     */
    void add(ChatMessage message);

    /**
     * Retrieves messages from the chat memory.
     * Depending on the implementation, it may not return all previously added messages,
     * but rather a subset, a summary, or a combination thereof, etc.
     *
     * @return A list of ChatMessage objects representing the portion of the chat memory that is currently retained.
     */
    List<ChatMessage> messages();

    /**
     * Clears the chat memory.
     */
    void clear();
}
