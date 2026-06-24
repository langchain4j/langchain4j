package dev.langchain4j.memory;

import dev.langchain4j.data.message.ChatMessage;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletionStage;

import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

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
     * Adds messages to the chat memory
     * @param messages The {@link ChatMessage}s to add
     */
    default void add(ChatMessage... messages) {
        if ((messages != null) && (messages.length > 0)) {
            add(Arrays.asList(messages));
        }
    }

    /**
     * Adds messages to the chat memory
     * @param messages The {@link ChatMessage}s to add
     */
    default void add(Iterable<ChatMessage> messages) {
        if (messages != null) {
            messages.forEach(this::add);
        }
    }

    /**
     * Replaces all messages in the chat memory with the specified messages.
     * Unlike {@link #add(ChatMessage...) add}, this method replaces the entire message history
     * rather than appending to it.
     * <p>
     * The default implementation delegates to {@link #set(Iterable) set(Iterable&lt;ChatMessage&gt;)}.
     * <p>
     * NOTE: This method is never called automatically by LangChain4j.
     *
     * @param messages The {@link ChatMessage}s to set. Must not be {@code null} or empty.
     * @since 1.11.0
     */
    default void set(ChatMessage... messages) {
        ensureNotEmpty(messages, "messages");
        set(Arrays.asList(messages));
    }

    /**
     * Replaces all messages in the chat memory with the specified messages.
     * Unlike {@link #add(Iterable) add}, this method replaces the entire message history
     * rather than appending to it.
     * <p>
     * Implementations should override this method to provide more efficient atomic operations if possible.
     * The default implementation calls {@link #clear()} followed by {@link #add(Iterable) add(Iterable&lt;ChatMessage&gt;)}
     * which is not atomic.
     * <p>
     * This method will typically be used when chat memory needs to be re-written to implement things like
     * memory compaction.
     * <p>
     * NOTE: This method is never called automatically by LangChain4j.
     *
     * @param messages The {@link ChatMessage}s to set. Must not be {@code null} or empty.
     * @since 1.11.0
     */
    default void set(Iterable<ChatMessage> messages) {
        ensureNotNull(messages, "messages");
        if (!messages.iterator().hasNext()) {
            throw new IllegalArgumentException("messages must not be empty");
        }
        clear();
        add(messages);
    }

    /**
     * Retrieves messages from the chat memory.
     * Depending on the implementation, it may not return all previously added messages,
     * but rather a subset, a summary, or a combination thereof.
     *
     * @return A list of {@link ChatMessage} objects that represent the current state of the chat memory.
     */
    List<ChatMessage> messages();

    /**
     * Non-blocking counterpart of {@link #add(Iterable)}, used by the asynchronous
     * ({@link java.util.concurrent.CompletableFuture}/{@link CompletionStage}) and reactive
     * ({@link java.util.concurrent.Flow.Publisher}) AI Service APIs. It takes a list (rather than a single
     * message) so that adding several messages at once is a single operation; to add one message, pass a
     * singleton list.
     * <p>
     * The default implementation throws {@link UnsupportedOperationException}: a memory backed by a blocking
     * {@link dev.langchain4j.store.memory.chat.ChatMemoryStore} is <b>not</b> silently offloaded to a worker thread.
     * Implementations should compose the store's
     * {@link dev.langchain4j.store.memory.chat.ChatMemoryStore#getMessagesAsync(Object) getMessagesAsync}/
     * {@link dev.langchain4j.store.memory.chat.ChatMemoryStore#updateMessagesAsync(Object, List) updateMessagesAsync},
     * persisting all messages in a single read-modify-write (fewer round trips and an atomic update).
     * <p>
     * Callers must not invoke this method concurrently for the same memory: implementations typically read, modify
     * and write the store, so concurrent calls would race. The AI Service chains its calls sequentially.
     *
     * @param messages The {@link ChatMessage}s to add.
     * @return A stage that completes when the messages have been added.
     * @since 1.17.0
     */
    default CompletionStage<Void> addAsync(List<ChatMessage> messages) {
        throw new UnsupportedOperationException(
                "addAsync() is not implemented by " + getClass().getName());
    }

    /**
     * Non-blocking counterpart of {@link #set(Iterable)}, used by the asynchronous
     * ({@link java.util.concurrent.CompletableFuture}/{@link CompletionStage}) and reactive
     * ({@link java.util.concurrent.Flow.Publisher}) AI Service APIs (e.g. to rewrite memory for tool
     * compensation without blocking the model-delivery thread).
     * <p>
     * Replaces the entire message history with {@code messages}. Like the other async methods, callers must not
     * invoke it concurrently for the same memory. The default implementation throws
     * {@link UnsupportedOperationException}; see {@link #addAsync(List)} for the rationale.
     *
     * @param messages The {@link ChatMessage}s to set.
     * @return A stage that completes when the messages have been set.
     * @since 1.17.0
     */
    default CompletionStage<Void> setAsync(List<ChatMessage> messages) {
        throw new UnsupportedOperationException(
                "setAsync() is not implemented by " + getClass().getName());
    }

    /**
     * Non-blocking counterpart of {@link #messages()}, used by the asynchronous
     * ({@link java.util.concurrent.CompletableFuture}/{@link CompletionStage}) and reactive
     * ({@link java.util.concurrent.Flow.Publisher}) AI Service APIs.
     * <p>
     * The default implementation throws {@link UnsupportedOperationException}; see {@link #addAsync(List)}
     * for the rationale.
     *
     * @return A stage that completes with the current state of the chat memory.
     * @since 1.17.0
     */
    default CompletionStage<List<ChatMessage>> messagesAsync() { // TODO name
        throw new UnsupportedOperationException(
                "messagesAsync() is not implemented by " + getClass().getName());
    }

    /**
     * Clears the chat memory.
     */
    void clear();
}
