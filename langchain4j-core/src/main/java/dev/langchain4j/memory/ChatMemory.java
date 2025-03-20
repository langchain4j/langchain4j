package dev.langchain4j.memory;

import dev.langchain4j.data.message.ChatMessage;
import java.util.List;

/**
 * Represents the memory (history) of a chat conversation.
 * Since language models do not keep the state of the conversation, it is necessary to provide all previous messages
 * on every interaction with the language model.
 * {@link ChatMemory} helps with keeping track of the conversation and ensuring that messages fit within language model's context window.
 */
public interface ChatMemory {

    /**
     * The ID of the {@link ChatMemory}.
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
     * Add a list of messages to the chat memory.
     *
     * @param messages The list of {@link ChatMessage} objects to add.
     */
    void addAll(List<ChatMessage> messages);

    /**
     * Adds multiple messages to the chat memory.
     * This method allows adding a variable number of {@link ChatMessage} objects at once.
     *
     * @param massages The {@link ChatMessage} objects to add.
     */
    void addAll(ChatMessage... massages);

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
