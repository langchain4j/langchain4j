package dev.langchain4j.store.memory.chat;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.memory.ChatMemory;

import java.util.List;
import java.util.concurrent.CompletionStage;

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

    /**
     * Non-blocking counterpart of {@link #getMessages(Object)}, used by the asynchronous
     * ({@link java.util.concurrent.CompletableFuture}/{@link CompletionStage}) and reactive
     * ({@link java.util.concurrent.Flow.Publisher}) AI Service APIs.
     * <p>
     * The default implementation throws {@link UnsupportedOperationException}: a store backed by blocking I/O is
     * <b>not</b> silently offloaded to a worker thread, because that would hide the fact that it is not truly
     * non-blocking. Implement this method to return the messages without blocking the calling thread (e.g. using a
     * reactive client), or, if the underlying client is blocking, offload it to an executor explicitly.
     *
     * @param memoryId The ID of the chat memory.
     * @return A stage that completes with the list of messages for the specified chat memory. Must not be null.
     * @since 1.17.0
     */
    default CompletionStage<List<ChatMessage>> getMessagesAsync(Object memoryId) {
        throw new UnsupportedOperationException(
                "getMessagesAsync() is not implemented by " + getClass().getName());
    }

    /**
     * Non-blocking counterpart of {@link #updateMessages(Object, List)}, used by the asynchronous
     * ({@link java.util.concurrent.CompletableFuture}/{@link CompletionStage}) and reactive
     * ({@link java.util.concurrent.Flow.Publisher}) AI Service APIs.
     * <p>
     * The default implementation throws {@link UnsupportedOperationException}; see {@link #getMessagesAsync(Object)}
     * for the rationale.
     *
     * @param memoryId The ID of the chat memory.
     * @param messages List of messages for the specified chat memory, that represent the current state of the
     *                 {@link ChatMemory}.
     * @return A stage that completes when the messages have been stored.
     * @since 1.17.0
     */
    default CompletionStage<Void> updateMessagesAsync(Object memoryId, List<ChatMessage> messages) {
        throw new UnsupportedOperationException(
                "updateMessagesAsync() is not implemented by " + getClass().getName());
    }

    /**
     * Non-blocking counterpart of {@link #deleteMessages(Object)}, used by the asynchronous
     * ({@link java.util.concurrent.CompletableFuture}/{@link CompletionStage}) and reactive
     * ({@link java.util.concurrent.Flow.Publisher}) AI Service APIs.
     * <p>
     * The default implementation throws {@link UnsupportedOperationException}; see {@link #getMessagesAsync(Object)}
     * for the rationale.
     *
     * @param memoryId The ID of the chat memory.
     * @return A stage that completes when the messages have been deleted.
     * @since 1.17.0
     */
    default CompletionStage<Void> deleteMessagesAsync(Object memoryId) {
        throw new UnsupportedOperationException(
                "deleteMessagesAsync() is not implemented by " + getClass().getName());
    }
}
