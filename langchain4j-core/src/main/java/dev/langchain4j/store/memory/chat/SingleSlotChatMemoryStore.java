package dev.langchain4j.store.memory.chat;

import dev.langchain4j.data.message.ChatMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Implementation of {@link ChatMemoryStore} that stores state of {@link dev.langchain4j.memory.ChatMemory} (chat messages) in-memory.
 * <p>
 * This storage mechanism is transient and does not persist data across application restarts.
 */
public class SingleSlotChatMemoryStore implements ChatMemoryStore {
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

    private void checkMemoryId(Object memoryId) {
        if (this.memoryId != memoryId) {
            throw new IllegalStateException();
        }
    }
}
