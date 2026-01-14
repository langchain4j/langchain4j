package dev.langchain4j.memory;

import dev.langchain4j.data.message.ChatMessage;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

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
     *
     * @param messages The {@link ChatMessage}s to set. Must not be {@code null} or empty.
     * @since 1.11.0
     */
    default void set(ChatMessage... messages) {
        Objects.requireNonNull(messages, "messages must not be null");
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
     *
     * @param messages The {@link ChatMessage}s to set. Must not be {@code null} or empty.
     * @since 1.11.0
     */
    default void set(Iterable<ChatMessage> messages) {
        Objects.requireNonNull(messages, "messages must not be null");
        if (messages instanceof Collection<?> collection) {
            if (collection.isEmpty()) {
                throw new IllegalArgumentException("messages must not be empty");
            }
        } else if (!messages.iterator().hasNext()) {
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
     * Clears the chat memory.
     */
    void clear();
}
