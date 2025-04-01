package dev.langchain4j.store.memory.chat;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.memory.ChatMemory;

import java.util.List;

/**
 * Represents a store for the {@link ChatMemory} state.
 * Allows for flexibility in terms of where and how chat memory is stored.
 * <br>
 * <br>
 * Currently, the only implementation available is {@link InMemoryChatMemoryStore}.
 * Over time, out-of-the-box implementations will be added for popular stores like SQL databases, document stores, etc.
 * In the meantime, you can implement this interface to connect to any storage of your choice.
 * <br>
 * <br>
 * More documentation can be found <a href="https://docs.langchain4j.dev/tutorials/chat-memory">here</a>.
 */
public interface ChatMemoryStore {

    /**
     * Retrieves messages for a specified chat memory.
     *
     * @param memoryId The ID of the chat memory.
     * @return List of messages for the specified chat memory. Must not be null. Can be deserialized from JSON using {@link ChatMessageDeserializer}.
     */
    List<ChatMessage> getMessages(Object memoryId);

    /**
     * Updates messages for a specified chat memory.
     *
     * @param memoryId The ID of the chat memory.
     * @param messages List of messages for the specified chat memory, that represent the current state of the {@link ChatMemory}.
     *                 Can be serialized to JSON using {@link ChatMessageSerializer}.
     */
    void updateMessages(Object memoryId, List<ChatMessage> messages);

    /**
     * Deletes all messages for a specified chat memory.
     *
     * @param memoryId The ID of the chat memory.
     */
    void deleteMessages(Object memoryId);
}
