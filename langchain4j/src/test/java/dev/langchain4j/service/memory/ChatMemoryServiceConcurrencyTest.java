package dev.langchain4j.service.memory;

import static dev.langchain4j.service.memory.ChatMemoryService.DEFAULT;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class ChatMemoryServiceConcurrencyTest {

    @Test
    void should_create_only_one_default_chat_memory_under_concurrent_load() throws Exception {
        List<ChatMemory> createdMemories = Collections.synchronizedList(new ArrayList<>());

        ChatMemoryProvider provider = memoryId -> {
            slowDownProvider();
            ChatMemory memory =
                    MessageWindowChatMemory.builder().maxMessages(10).build();
            createdMemories.add(memory);
            return memory;
        };

        ChatMemoryService service = new ChatMemoryService(provider);
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        List<ChatMemory> results = Collections.synchronizedList(new ArrayList<>());
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                try {
                    startLatch.await();
                    results.add(service.getOrCreateChatMemory(DEFAULT));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                } finally {
                    doneLatch.countDown();
                }
            }));
        }

        startLatch.countDown();
        assertThat(doneLatch.await(5, TimeUnit.SECONDS)).isTrue();
        for (Future<?> future : futures) {
            future.get();
        }
        executor.shutdown();

        assertThat(createdMemories).hasSize(1);
        assertThat(results).hasSize(threadCount);
        ChatMemory createdMemory = createdMemories.get(0);
        results.forEach(result -> assertThat(result).isSameAs(createdMemory));
    }

    private static void slowDownProvider() {
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}
