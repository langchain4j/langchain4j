package dev.langchain4j.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.service.AiServiceStreamingEvent.AfterToolExecutionEvent;
import dev.langchain4j.service.AiServiceStreamingEvent.BeforeToolExecutionEvent;
import dev.langchain4j.service.AiServiceStreamingEvent.CompleteToolCallEvent;
import dev.langchain4j.service.AiServiceStreamingEvent.FinalResponseEvent;
import dev.langchain4j.service.AiServiceStreamingEvent.IntermediateResponseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Proves the one load-bearing concurrency assumption of {@link AiServiceStreamingEventPublisher}: the framework
 * emits into the {@code mutiny-zero} {@code Tube} from <b>several threads at once</b> — the model-delivery thread
 * (mapping each {@code StreamingEvent} in {@code onNext}) and every tool-executor thread (each firing
 * {@code BeforeToolExecutionEvent}/{@code AfterToolExecutionEvent} as its tool starts and finishes) — yet the
 * Reactive Streams contract forbids concurrent signals to a subscriber (rule 1.3). The {@code Tube} must
 * therefore serialize these concurrent {@code send()}s.
 * <p>
 * The Reactive Streams TCK ({@code OpenAiStreamingChatModelPublisherTckTest}) only drives a single-threaded
 * producer, so it never exercises this. This test does, by:
 * <ol>
 *   <li>issuing many tool calls in one round, executed concurrently on a fixed thread pool, and asserting that
 *       at least two tools genuinely overlapped — i.e. concurrent producers really did race into the tube
 *       (otherwise the test would prove nothing); and</li>
 *   <li>subscribing with a re-entrancy guard spanning <i>every</i> signal ({@code onNext}/{@code onComplete}/
 *       {@code onError}), with a deliberately widened window, and asserting that no two signals ever overlapped
 *       (max observed signal concurrency == 1).</li>
 * </ol>
 * If the tube did not serialize, the concurrent tool-thread and delivery-thread emissions would trip the guard.
 */
class AiServiceStreamingPublisherConcurrencyTest {

    private static final int TOOL_CALLS = 24;
    private static final int POOL_SIZE = 8;

    interface EventStreamer {
        Flow.Publisher<AiServiceStreamingEvent> chat(String message);
    }

    /**
     * A single tool invoked many times concurrently. It records the peak number of simultaneously-running
     * invocations so the test can assert that concurrent producers really did hit the tube.
     */
    static class BusyTools {

        private final AtomicInteger active = new AtomicInteger();
        private final AtomicInteger peakActive = new AtomicInteger();

        @Tool
        String busy(String input) {
            int now = active.incrementAndGet();
            peakActive.accumulateAndGet(now, Math::max);
            try {
                // Brief work so overlapping invocations coincide, widening the concurrent-emission window.
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(2));
            } finally {
                active.decrementAndGet();
            }
            return "ok-" + input;
        }

        int peakActive() {
            return peakActive.get();
        }
    }

    @Test
    void concurrent_tube_emissions_are_serialized_to_the_subscriber() throws Exception {
        ExecutorService toolExecutor = Executors.newFixedThreadPool(POOL_SIZE);
        try {
            List<ToolExecutionRequest> toolRequests = new ArrayList<>();
            for (int i = 0; i < TOOL_CALLS; i++) {
                toolRequests.add(ToolExecutionRequest.builder()
                        .id("t" + i)
                        .name("busy")
                        .arguments("{\"input\": \"" + i + "\"}")
                        .build());
            }

            // Round 1: many concurrent tool calls. Round 2: the final textual answer.
            StreamingEventChatModelMock model = StreamingEventChatModelMock.thatStreams(
                    AiMessage.from(toolRequests), AiMessage.from("done"));

            BusyTools tools = new BusyTools();
            EventStreamer assistant = AiServices.builder(EventStreamer.class)
                    .streamingChatModel(model)
                    .tools(tools)
                    .executeToolsConcurrently(toolExecutor)
                    .build();

            SerializationCheckingSubscriber subscriber = new SerializationCheckingSubscriber();
            assistant.chat("run the tools").subscribe(subscriber);

            assertThat(subscriber.awaitTerminal(30, TimeUnit.SECONDS))
                    .as("stream terminated within 30s")
                    .isTrue();

            // Preconditions: the scenario actually created concurrent producers, otherwise it proves nothing.
            assertThat(tools.peakActive())
                    .as("at least two tools must have run concurrently (genuine multi-producer contention)")
                    .isGreaterThanOrEqualTo(2);

            // The property under test: signals to the subscriber were never concurrent, despite concurrent sends.
            assertThat(subscriber.maxConcurrentSignals())
                    .as("Reactive Streams rule 1.3: signals must be serialized (no concurrent onNext/onComplete/onError)")
                    .isEqualTo(1);

            // And the stream is intact — every event delivered exactly once, none lost or duplicated.
            assertThat(subscriber.error()).isNull();
            assertThat(subscriber.completed()).isTrue();
            assertThat(subscriber.countOf(CompleteToolCallEvent.class)).isEqualTo(TOOL_CALLS);
            assertThat(subscriber.countOf(BeforeToolExecutionEvent.class)).isEqualTo(TOOL_CALLS);
            assertThat(subscriber.countOf(AfterToolExecutionEvent.class)).isEqualTo(TOOL_CALLS);
            assertThat(subscriber.countOf(IntermediateResponseEvent.class)).isEqualTo(1);
            assertThat(subscriber.countOf(FinalResponseEvent.class)).isEqualTo(1);
            assertThat(subscriber.events().get(subscriber.events().size() - 1))
                    .isInstanceOf(FinalResponseEvent.class);

            // Per tool, its BeforeToolExecutionEvent is delivered before its AfterToolExecutionEvent (the
            // serialized order never interleaves a single tool's own pair, even though tools overlap each other).
            assertThat(subscriber.beforeIndexByToolId())
                    .allSatisfy((toolId, beforeIndex) ->
                            assertThat(beforeIndex).isLessThan(subscriber.afterIndexByToolId().get(toolId)));
        } finally {
            toolExecutor.shutdownNow();
        }
    }

    /**
     * Records every signal, guarding against concurrent delivery. {@link #maxConcurrentSignals()} stays at 1 iff
     * the publisher honored the serial-signal contract. A tiny park inside each signal widens the detection
     * window so a genuine overlap is caught rather than missed by luck.
     */
    private static final class SerializationCheckingSubscriber implements Flow.Subscriber<AiServiceStreamingEvent> {

        private final Queue<AiServiceStreamingEvent> events = new ConcurrentLinkedQueue<>();
        private final AtomicInteger inFlight = new AtomicInteger();
        private final AtomicInteger maxConcurrent = new AtomicInteger();
        private final AtomicReference<Throwable> error = new AtomicReference<>();
        private final CountDownLatch terminal = new CountDownLatch(1);
        private volatile boolean completed;

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(AiServiceStreamingEvent event) {
            enter();
            try {
                events.add(event);
            } finally {
                exit();
            }
        }

        @Override
        public void onError(Throwable throwable) {
            enter();
            try {
                error.set(throwable);
            } finally {
                exit();
                terminal.countDown();
            }
        }

        @Override
        public void onComplete() {
            enter();
            try {
                completed = true;
            } finally {
                exit();
                terminal.countDown();
            }
        }

        private void enter() {
            int now = inFlight.incrementAndGet();
            maxConcurrent.accumulateAndGet(now, Math::max);
            // Widen the window during which a concurrent signal (if the contract were violated) would overlap.
            LockSupport.parkNanos(TimeUnit.MICROSECONDS.toNanos(200));
        }

        private void exit() {
            inFlight.decrementAndGet();
        }

        boolean awaitTerminal(long timeout, TimeUnit unit) throws InterruptedException {
            return terminal.await(timeout, unit);
        }

        int maxConcurrentSignals() {
            return maxConcurrent.get();
        }

        Throwable error() {
            return error.get();
        }

        boolean completed() {
            return completed;
        }

        List<AiServiceStreamingEvent> events() {
            return new ArrayList<>(events);
        }

        long countOf(Class<? extends AiServiceStreamingEvent> type) {
            return events.stream().filter(type::isInstance).count();
        }

        java.util.Map<String, Integer> beforeIndexByToolId() {
            List<AiServiceStreamingEvent> snapshot = events();
            return indexByToolId(
                    snapshot,
                    BeforeToolExecutionEvent.class,
                    e -> ((BeforeToolExecutionEvent) e).beforeToolExecution().request().id());
        }

        java.util.Map<String, Integer> afterIndexByToolId() {
            List<AiServiceStreamingEvent> snapshot = events();
            return indexByToolId(
                    snapshot,
                    AfterToolExecutionEvent.class,
                    e -> ((AfterToolExecutionEvent) e).toolExecution().request().id());
        }

        private static java.util.Map<String, Integer> indexByToolId(
                List<AiServiceStreamingEvent> snapshot,
                Class<? extends AiServiceStreamingEvent> type,
                java.util.function.Function<AiServiceStreamingEvent, String> toolId) {
            return java.util.stream.IntStream.range(0, snapshot.size())
                    .filter(i -> type.isInstance(snapshot.get(i)))
                    .boxed()
                    .collect(Collectors.toMap(i -> toolId.apply(snapshot.get(i)), i -> i));
        }
    }
}
