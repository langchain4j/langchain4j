package dev.langchain4j.service.memory;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ChatMemoryServiceTest {

    @Test
    void invocation_chat_memory_should_forward_every_method_of_chat_memory() throws Exception {
        // a method that is not overridden would run the interface's default instead of the user's chat memory
        Class<?> invocationChatMemory = Class.forName(ChatMemoryService.class.getName() + "$InvocationChatMemory");

        List<String> notForwarded = Arrays.stream(ChatMemory.class.getMethods())
                .filter(method -> !Modifier.isStatic(method.getModifiers()))
                .filter(method -> !isDeclaredBy(invocationChatMemory, method))
                .map(Method::toGenericString)
                .toList();

        assertThat(notForwarded).isEmpty();
    }

    @Test
    void invocation_chat_memory_should_stay_bound_to_the_first_resolved_chat_memory() throws Exception {
        CountDownLatch firstResolutionStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstResolution = new CountDownLatch(1);
        AtomicInteger resolutions = new AtomicInteger();
        ChatMemoryService chatMemoryService =
                new ChatMemoryService(memoryId -> MessageWindowChatMemory.withMaxMessages(10)) {

                    @Override
                    public ChatMemory getOrCreateChatMemory(Object memoryId) {
                        if (resolutions.incrementAndGet() == 1) {
                            firstResolutionStarted.countDown();
                            await(releaseFirstResolution);
                        }
                        // A new chat memory on each call, as when the chat memory is evicted in between
                        return MessageWindowChatMemory.withMaxMessages(10);
                    }
                };
        ChatMemory invocationChatMemory = chatMemoryService.getOrCreateInvocationChatMemory(InvocationContext.builder()
                .invocationId(UUID.randomUUID())
                .chatMemoryId("user-1")
                .build());
        AtomicReference<ChatMemory> resolvedByFirst = new AtomicReference<>();
        AtomicReference<ChatMemory> resolvedBySecond = new AtomicReference<>();
        Thread first = new Thread(() -> resolvedByFirst.set(ChatMemoryService.unwrap(invocationChatMemory)));
        Thread second = new Thread(() -> resolvedBySecond.set(ChatMemoryService.unwrap(invocationChatMemory)));

        first.start();
        assertThat(firstResolutionStarted.await(10, SECONDS)).isTrue();
        second.start();
        // The second thread either waits for the first resolution or resolves a chat memory of its own
        while (second.getState() != Thread.State.WAITING && second.getState() != Thread.State.TERMINATED) {
            Thread.onSpinWait();
        }
        releaseFirstResolution.countDown();
        first.join(SECONDS.toMillis(10));
        second.join(SECONDS.toMillis(10));

        assertThat(resolutions).hasValue(1);
        assertThat(resolvedBySecond.get()).isNotNull().isSameAs(resolvedByFirst.get());
        assertThat(ChatMemoryService.unwrap(invocationChatMemory)).isSameAs(resolvedByFirst.get());
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(10, SECONDS)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    private static boolean isDeclaredBy(Class<?> type, Method method) {
        try {
            type.getDeclaredMethod(method.getName(), method.getParameterTypes());
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }
}
