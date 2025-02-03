package dev.langchain4j.store.memory.chat;

import dev.langchain4j.data.message.ChatMessage;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of {@link ChatMemoryStore} with a single entry that then ignores the passed memoryId.
 * <p>
 * This storage mechanism is transient and does not persist data across application restarts.
 */
public class SingleEntryChatMemoryStore implements ChatMemoryStore {

    List<ChatMessage> chatMessages = new ArrayList<>();

    @Override
    public List<ChatMessage> getMessages(final Object memoryId) {
        return chatMessages;
    }

    @Override
    public void updateMessages(final Object memoryId, final List<ChatMessage> messages) {
        this.chatMessages = messages;
    }

    @Override
    public void deleteMessages(final Object memoryId) {
        chatMessages.clear();
    }
}
