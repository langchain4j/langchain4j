package dev.langchain4j.service;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.CompensateFor;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.test.mock.MockChatModel;
import dev.langchain4j.test.mock.MockStreamingChatModel;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;

/**
 * That a rollback after a cancellation that fails is logged in the non-blocking modes, as nobody else is told about
 * it.
 * <p>
 * These tests capture {@link System#err} (the default tinylog destination for WARN), so this class runs
 * {@link Isolated} and single-threaded.
 */
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
class ToolCompensationRollbackLoggingTest {

    private static final String ROLLBACK_FAILED = "Failed to roll back the tools after the invocation was cancelled";

    private static final ToolExecutionRequest CREDIT = ToolExecutionRequest.builder()
            .id("1")
            .name("credit")
            .arguments("{\"arg0\": \"Mario\"}")
            .build();

    private static final ToolExecutionRequest RESERVE = ToolExecutionRequest.builder()
            .id("2")
            .name("reserve")
            .arguments("{\"arg0\": \"Mario\"}")
            .build();

    interface AsyncAssistant {

        CompletableFuture<String> chat(String message);
    }

    interface ReactiveAssistant {

        Flow.Publisher<String> chat(String message);
    }

    // reserve runs until it is released
    static class Bank {

        final CountDownLatch reserveStarted = new CountDownLatch(1);
        final CountDownLatch releaseReserve = new CountDownLatch(1);
        final CountDownLatch uncredited = new CountDownLatch(1);

        @Tool
        String credit(String name) {
            return "credited";
        }

        @CompensateFor("credit")
        void uncredit(String name) {
            uncredited.countDown();
        }

        @Tool
        String reserve(String name) {
            reserveStarted.countDown();
            try {
                releaseReserve.await(10, SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "reserved";
        }
    }

    // a store that cannot be written to while it is being rolled back
    static class MemoryFailingOnRewrite implements ChatMemory {

        private final ChatMemory delegate = MessageWindowChatMemory.withMaxMessages(20);

        @Override
        public Object id() {
            return delegate.id();
        }

        @Override
        public void add(ChatMessage message) {
            delegate.add(message);
        }

        @Override
        public List<ChatMessage> messages() {
            return delegate.messages();
        }

        @Override
        public void clear() {
            delegate.clear();
        }

        @Override
        public CompletableFuture<Void> addAsync(List<ChatMessage> messages) {
            return delegate.addAsync(messages);
        }

        @Override
        public CompletableFuture<List<ChatMessage>> messagesAsync() {
            return delegate.messagesAsync();
        }

        @Override
        public void set(Iterable<ChatMessage> messages) {
            throw new IllegalStateException("store is down");
        }

        @Override
        public CompletableFuture<Void> setAsync(List<ChatMessage> messages) {
            return CompletableFuture.failedFuture(new IllegalStateException("store is down"));
        }
    }

    @Test
    void completable_future_should_log_a_rollback_after_a_cancellation_that_fails() throws Exception {

        Bank bank = new Bank();
        AsyncAssistant assistant = AiServices.builder(AsyncAssistant.class)
                .chatModel(MockChatModel.thatResponds(AiMessage.from(CREDIT), AiMessage.from(RESERVE)))
                .chatMemory(new MemoryFailingOnRewrite())
                .tools(bank)
                .executeToolsConcurrently()
                .compensateOnToolErrors(true)
                .build();

        String logOutput = captureStdErr(() -> {
            CompletableFuture<String> answer = assistant.chat("transfer");
            awaitReserveStarted(bank);
            answer.cancel(true);
            bank.releaseReserve.countDown();
            awaitUncredited(bank);
        });

        assertThat(logOutput).contains("WARN").contains(ROLLBACK_FAILED).contains("store is down");
    }

    @Test
    void flow_publisher_should_log_a_rollback_after_a_cancellation_that_fails() throws Exception {

        Bank bank = new Bank();
        ReactiveAssistant assistant = AiServices.builder(ReactiveAssistant.class)
                .streamingChatModel(MockStreamingChatModel.thatResponds(AiMessage.from(CREDIT), AiMessage.from(RESERVE)))
                .chatMemory(new MemoryFailingOnRewrite())
                .tools(bank)
                .executeToolsConcurrently()
                .compensateOnToolErrors(true)
                .build();

        String logOutput = captureStdErr(() -> {
            CompletableFuture<Flow.Subscription> subscription = new CompletableFuture<>();
            assistant.chat("transfer").subscribe(new Flow.Subscriber<>() {

                @Override
                public void onSubscribe(Flow.Subscription s) {
                    subscription.complete(s);
                    s.request(Long.MAX_VALUE);
                }

                @Override
                public void onNext(String token) {}

                @Override
                public void onError(Throwable throwable) {}

                @Override
                public void onComplete() {}
            });
            awaitReserveStarted(bank);
            subscription.join().cancel();
            bank.releaseReserve.countDown();
            awaitUncredited(bank);
        });

        assertThat(logOutput).contains("WARN").contains(ROLLBACK_FAILED).contains("store is down");
    }

    private static void awaitReserveStarted(Bank bank) {
        try {
            assertThat(bank.reserveStarted.await(10, SECONDS)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    private static void awaitUncredited(Bank bank) {
        try {
            assertThat(bank.uncredited.await(10, SECONDS)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    // the rollback fails after the compensating actions ran, so the output is captured until the failure is logged
    private static String captureStdErr(Runnable action) throws InterruptedException {
        PrintStream originalErr = System.err;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setErr(new PrintStream(captured, true, StandardCharsets.UTF_8));
        try {
            action.run();
            long deadline = System.currentTimeMillis() + 10_000;
            while (!captured.toString(StandardCharsets.UTF_8).contains(ROLLBACK_FAILED)
                    && System.currentTimeMillis() < deadline) {
                Thread.sleep(10);
            }
        } finally {
            System.setErr(originalErr);
        }
        return captured.toString(StandardCharsets.UTF_8);
    }
}
