package dev.langchain4j.store.memory.chat;

import dev.langchain4j.data.message.ChatMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores chat memory for each user in memory.
 */
public class InMemoryChatMemoryStore implements ChatMemoryStore {

    private final Map<Object, List<ChatMessage>> messagesByUserId = new ConcurrentHashMap<>();

    @Override
    public List<ChatMessage> getMessages(Object userId) {
        return messagesByUserId.computeIfAbsent(userId, ignored -> new ArrayList<>());
    }

    @Override
    public void updateMessages(Object userId, List<ChatMessage> messages) {
        messagesByUserId.put(userId, messages);
    }

    @Override
    public void deleteMessages(Object userId) {
        messagesByUserId.remove(userId);
    }
}
