package dev.langchain4j.store.memory.chat;

import dev.langchain4j.data.message.ChatMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of {@link ChatMemoryStore} that stores state of {@link dev.langchain4j.memory.ChatMemory} (chat messages) in-memory.
 * <p>
 * This storage mechanism is transient and does not persist data across application restarts.
 */
public class InMemoryChatMemoryStore implements ChatMemoryStore {

    private final Map<Object, List<ChatMessage>> messagesByMemoryId = new ConcurrentHashMap<>();

    /**
     * Constructs a new {@link InMemoryChatMemoryStore}.
     */
    public InMemoryChatMemoryStore() {}

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        return messagesByMemoryId.computeIfAbsent(memoryId, ignored -> new ArrayList<>());
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        messagesByMemoryId.put(memoryId, messages);
    }

    @Override
    public void deleteMessages(Object memoryId) {
        messagesByMemoryId.remove(memoryId);
    }

    @Override
    public CompletionStage<List<ChatMessage>> getMessagesAsync(Object memoryId) {
        return CompletableFuture.completedFuture(getMessages(memoryId));
    }

    @Override
    public CompletionStage<Void> updateMessagesAsync(Object memoryId, List<ChatMessage> messages) {
        updateMessages(memoryId, messages);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> deleteMessagesAsync(Object memoryId) {
        deleteMessages(memoryId);
        return CompletableFuture.completedFuture(null);
    }
}
