package dev.langchain4j.memory.chat;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

class ConcurrentAddRaceStore implements ChatMemoryStore {

    private final int parties;
    private final CountDownLatch release;
    private final AtomicInteger gets = new AtomicInteger();
    private final Object guard = new Object();
    private List<ChatMessage> messages = new ArrayList<>();

    ConcurrentAddRaceStore(int parties) {
        this.parties = parties;
        this.release = new CountDownLatch(parties);
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        List<ChatMessage> snapshot;
        synchronized (guard) {
            snapshot = new ArrayList<>(messages);
        }
        if (gets.incrementAndGet() <= parties) {
            release.countDown();
            try {
                release.await(300, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        return snapshot;
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        synchronized (guard) {
            this.messages = new ArrayList<>(messages);
        }
    }

    @Override
    public void deleteMessages(Object memoryId) {
        synchronized (guard) {
            this.messages = new ArrayList<>();
        }
    }
}
