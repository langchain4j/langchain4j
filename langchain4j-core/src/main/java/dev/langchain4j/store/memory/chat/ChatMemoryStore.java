package dev.langchain4j.store.memory.chat;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.memory.ChatMemory;

import java.util.List;

/**
 * Represents a store for the {@link ChatMemory} state.
 * Allows for flexibility in terms of where and how chat memory is stored.
 * Currently, the only implementation available is {@link InMemoryChatMemoryStore}. We are in the process of adding
 * ready implementations for popular stores like SQL DBs, document stores, etc.
 * In the meantime, you can implement this interface to connect to any storage of your choice.
 */
public interface ChatMemoryStore {

    /**
     * Retrieves messages for a specified user.
     *
     * @param userId The ID of the user.
     * @return List of messages for the specified user. Must not be null. Can be deserialized from JSON using {@link ChatMessageDeserializer}.
     */
    List<ChatMessage> getMessages(Object userId);

    /**
     * Updates messages for a specified user.
     *
     * @param userId   The ID of the user.
     * @param messages List of messages for the specified user, that represent the current state of the {@link ChatMemory}.
     *                 Can be serialized to JSON using {@link ChatMessageSerializer}.
     */
    void updateMessages(Object userId, List<ChatMessage> messages);

    /**
     * Deletes all messages for a specified user.
     *
     * @param userId The ID of the user.
     */
    void deleteMessages(Object userId);
}
