package dev.langchain4j.service.tool;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.CompensateFor;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.observability.api.event.ToolCompensatedEvent;
import dev.langchain4j.observability.api.event.ToolExecutedEvent;
import dev.langchain4j.observability.api.listener.ToolCompensatedEventListener;
import dev.langchain4j.observability.api.listener.ToolExecutedEventListener;
import dev.langchain4j.service.AiServiceStreamingEvent;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.test.mock.MockChatModel;
import dev.langchain4j.test.mock.MockStreamingChatModel;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Once a non-blocking invocation is canceled, a tool call that has not started does not start: neither its tool nor
 * its callbacks run, and it fires no events. A call that has already started runs to the end and is rolled back.
 */
class ToolCancellationBeforeStartTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    enum Mode {
        COMPLETABLE_FUTURE,
        REACTIVE
    }

    interface FutureAssistant {

        CompletableFuture<String> chat(@UserMessage String message);
    }

    interface ReactiveAssistant {

        Flow.Publisher<AiServiceStreamingEvent> chat(@UserMessage String message);
    }

    /** A bank whose credit tool waits until {@link #release} is released, so a test can cancel while it runs. */
    public static class Bank {

        final List<String> log = new CopyOnWriteArrayList<>();
        final CountDownLatch creditStarted = new CountDownLatch(1);
        final CountDownLatch release;

        Bank(boolean isBlocking) {
            this.release = new CountDownLatch(isBlocking ? 1 : 0);
        }

        @Tool
        String credit(String name) throws InterruptedException {
            log.add("credit");
            creditStarted.countDown();
            release.await(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            return "credited";
        }

        @CompensateFor("credit")
        void uncredit(ToolExecution execution) {
            log.add("uncredit");
        }
    }

    private ThreadPoolExecutor executor;
    private final List<ToolExecutedEvent> executedEvents = new CopyOnWriteArrayList<>();
    private final List<ToolCompensatedEvent> compensatedEvents = new CopyOnWriteArrayList<>();
    private final List<String> callbacks = new CopyOnWriteArrayList<>();

    @BeforeEach
    void createExecutor() {
        executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
    }

    @AfterEach
    void shutDownExecutor() {
        executor.shutdownNow();
    }

    private static List<AiMessage> responsesCallingCredit() {
        return List.of(
                AiMessage.from(ToolExecutionRequest.builder()
                        .id("1")
                        .name("credit")
                        .arguments("{\"arg0\": \"Mario\"}")
                        .build()),
                AiMessage.from("done"));
    }

    /** Starts the invocation, returning what cancels it. */
    private Runnable start(Mode mode, Bank bank, BooleanSupplier[] modelCalled) {
        Runnable cancel;
        switch (mode) {
            case COMPLETABLE_FUTURE -> {
                var model = MockChatModel.thatResponds(responsesCallingCredit());
                modelCalled[0] = () -> !model.requests().isEmpty();
                var answer = configure(AiServices.builder(FutureAssistant.class).chatModel(model), bank)
                        .build()
                        .chat("hello");
                cancel = () -> answer.cancel(true);
            }
            case REACTIVE -> {
                var model = MockStreamingChatModel.thatResponds(responsesCallingCredit());
                modelCalled[0] = () -> !model.requests().isEmpty();
                var subscription = new CompletableFuture<Flow.Subscription>();
                configure(AiServices.builder(ReactiveAssistant.class).streamingChatModel(model), bank)
                        .build()
                        .chat("hello")
                        .subscribe(new Flow.Subscriber<>() {
                            @Override
                            public void onSubscribe(Flow.Subscription s) {
                                subscription.complete(s);
                                s.request(Long.MAX_VALUE);
                            }

                            @Override
                            public void onNext(AiServiceStreamingEvent item) {}

                            @Override
                            public void onError(Throwable throwable) {}

                            @Override
                            public void onComplete() {}
                        });
                cancel = () -> subscription.join().cancel();
            }
            default -> throw new IllegalArgumentException(mode.name());
        }
        return cancel;
    }

    private <T> AiServices<T> configure(AiServices<T> services, Bank bank) {
        return services.chatMemory(MessageWindowChatMemory.withMaxMessages(20))
                .tools(bank)
                .compensateOnToolErrors(true)
                .executeToolsConcurrently(executor)
                .beforeToolExecution(before -> callbacks.add("before " + before.request().name()))
                .afterToolExecution(after -> callbacks.add("after " + after.request().name()))
                .registerListener((ToolExecutedEventListener) executedEvents::add)
                .registerListener((ToolCompensatedEventListener) compensatedEvents::add);
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void a_call_still_queued_on_the_executor_when_the_invocation_is_canceled_does_not_start(Mode mode)
            throws Exception {
        var bank = new Bank(false);
        var blocker = new CountDownLatch(1);
        // the only thread of the executor waits, so the call of the tool stays in the queue
        executor.execute(() -> awaitQuietly(blocker));
        BooleanSupplier[] modelCalled = new BooleanSupplier[1];

        Runnable cancel = start(mode, bank, modelCalled);
        awaitUntil(() -> modelCalled[0].getAsBoolean() && !executor.getQueue().isEmpty());
        cancel.run();
        blocker.countDown();
        drainExecutor();

        assertThat(bank.log).isEmpty();
        assertThat(callbacks).isEmpty();
        assertThat(executedEvents).isEmpty();
        assertThat(compensatedEvents).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void a_call_that_already_started_when_the_invocation_is_canceled_runs_to_the_end_and_is_rolled_back(Mode mode)
            throws Exception {
        var bank = new Bank(true);
        BooleanSupplier[] modelCalled = new BooleanSupplier[1];

        Runnable cancel = start(mode, bank, modelCalled);
        assertThat(bank.creditStarted.await(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS))
                .isTrue();
        cancel.run();
        bank.release.countDown();
        drainExecutor();
        awaitUntil(() -> bank.log.contains("uncredit"));

        assertThat(bank.log).containsExactly("credit", "uncredit");
        assertThat(callbacks).containsExactly("before credit", "after credit");
        assertThat(compensatedEvents)
                .singleElement()
                .returns("credit", event -> event.request().name());
    }

    private void drainExecutor() throws Exception {
        // runs after every task queued before it on the single thread
        CompletableFuture.runAsync(() -> {}, executor).get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        // and once more, for what the tasks of the round started on completion
        CompletableFuture.runAsync(() -> {}, executor).get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void awaitUntil(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (!condition.getAsBoolean()) {
            assertThat(System.nanoTime()).as("condition met in time").isLessThan(deadline);
            Thread.sleep(5);
        }
    }
}
