package dev.langchain4j.memory.chat;

import static dev.langchain4j.internal.CompletableFutureUtils.failOnThrow;

import dev.langchain4j.Internal;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Implementation of {@link ChatMemoryStore} that stores state of {@link ChatMemory} (chat messages) in-memory.
 * <p>
 * This storage mechanism is transient and does not persist data across application restarts.
 */
@Internal
class SingleSlotChatMemoryStore implements ChatMemoryStore {

    private List<ChatMessage> messages = new ArrayList<>();

    private final Object memoryId;

    public SingleSlotChatMemoryStore(final Object memoryId) {
        this.memoryId = memoryId;
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        checkMemoryId(memoryId);
        return messages;
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        checkMemoryId(memoryId);
        this.messages = messages;
    }

    @Override
    public void deleteMessages(Object memoryId) {
        checkMemoryId(memoryId);
        this.messages = new ArrayList<>();
    }

    @Override
    public CompletableFuture<List<ChatMessage>> getMessagesAsync(Object memoryId) {
        return failOnThrow(() -> CompletableFuture.completedFuture(getMessages(memoryId)));
    }

    @Override
    public CompletableFuture<Void> updateMessagesAsync(Object memoryId, List<ChatMessage> messages) {
        return failOnThrow(() -> {
            updateMessages(memoryId, messages);
            return CompletableFuture.completedFuture(null);
        });
    }

    @Override
    public CompletableFuture<Void> deleteMessagesAsync(Object memoryId) {
        return failOnThrow(() -> {
            deleteMessages(memoryId);
            return CompletableFuture.completedFuture(null);
        });
    }

    private void checkMemoryId(Object memoryId) {
        if (!this.memoryId.equals(memoryId)) {
            throw new IllegalStateException("This chat memory has id: " + this.memoryId +
                    " but an operation has been requested on a memory with id: " + memoryId);
        }
    }
}
