package dev.langchain4j.memory.chat;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class HitCountChatMemoryStore extends InMemoryChatMemoryStore {

    public record HitCounts(int getMessages, int updateMessages, int deleteMessages) {
        public HitCounts subtract(HitCounts other) {
            return new HitCounts(
                    getMessages - other.getMessages,
                    updateMessages - other.updateMessages,
                    deleteMessages - other.deleteMessages);
        }
    }

    final AtomicInteger getMessagesCount = new AtomicInteger();
    final AtomicInteger updateMessagesCount = new AtomicInteger();
    final AtomicInteger deleteMessagesCount = new AtomicInteger();

    HitCounts hitCounts() {
        return new HitCounts(getMessagesCount.get(), updateMessagesCount.get(), deleteMessagesCount.get());
    }

    HitCounts measureHitCounts(Runnable r) {
        HitCounts start = hitCounts();
        r.run();
        return hitCounts().subtract(start);
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        getMessagesCount.incrementAndGet();
        return super.getMessages(memoryId);
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        updateMessagesCount.incrementAndGet();
        super.updateMessages(memoryId, messages);
    }

    @Override
    public void deleteMessages(Object memoryId) {
        deleteMessagesCount.incrementAndGet();
        super.deleteMessages(memoryId);
    }
}
