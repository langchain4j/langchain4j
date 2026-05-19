package dev.langchain4j.service;

import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onCompleteResponse;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onCompleteToolCall;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.CompleteToolCall;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class AiServiceStreamingResponseHandlerEagerToolOrderingTest {

    interface Assistant {

        TokenStream chat(String userMessage);
    }

    @Test
    void without_stream_before_tool_body_runs_before_intermediate_but_stream_tool_output_waits_until_after_intermediate()
            throws Exception {
        ConcurrentLinkedQueue<String> events = new ConcurrentLinkedQueue<>();
        FastTool tool = new FastTool(events);
        ToolExecutionRequest toolRequest = toolCall("toolu_order", "now");

        ScriptedStreamingChatModel model = new ScriptedStreamingChatModel(List.of(toolRequest), handler -> {
            awaitOrThrow(tool.bodyFinished, "tool body to finish");
            spinUntil(() -> events.contains(streamAfter(toolRequest)), 250, MILLISECONDS);
            completeToolTurn(handler, toolRequest);
        });

        ExecutorService toolExecutor = Executors.newCachedThreadPool();
        try {
            Assistant assistant = AiServices.builder(Assistant.class)
                    .streamingChatModel(model)
                    .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                    .tools(tool)
                    .executeToolsConcurrently(toolExecutor)
                    .build();

            CompletableFuture<ChatResponse> done = new CompletableFuture<>();
            assistant.chat("what time is it?")
                    .onToolExecuted(execution -> events.add(streamAfter(execution.request())))
                    .onIntermediateResponse(ignored -> events.add("intermediate"))
                    .onCompleteResponse(done::complete)
                    .onError(done::completeExceptionally)
                    .start();

            done.get(10, SECONDS);

            List<String> captured = List.copyOf(events);
            assertThat(indexOf(captured, "toolBody:now")).isLessThan(indexOf(captured, "intermediate"));
            assertThat(indexOf(captured, "intermediate")).isLessThan(indexOf(captured, streamAfter(toolRequest)));
            assertThat(captured).filteredOn(streamAfter(toolRequest)::equals).hasSize(1);
        } finally {
            toolExecutor.shutdownNow();
            model.shutdown();
        }
    }

    @Test
    void stream_beforeToolExecution_runs_after_intermediate_and_before_tool_body() throws Exception {
        ConcurrentLinkedQueue<String> events = new ConcurrentLinkedQueue<>();
        FastTool tool = new FastTool(events);
        ToolExecutionRequest toolRequest = toolCall("toolu_stream_before", "now");

        ScriptedStreamingChatModel model =
                new ScriptedStreamingChatModel(List.of(toolRequest), handler -> completeToolTurn(handler, toolRequest));

        ExecutorService toolExecutor =
                Executors.newSingleThreadExecutor(command -> new Thread(command, "stream-before-tool-executor"));
        try {
            Assistant assistant = AiServices.builder(Assistant.class)
                    .streamingChatModel(model)
                    .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                    .tools(tool)
                    .executeToolsConcurrently(toolExecutor)
                    .build();

            CompletableFuture<ChatResponse> done = new CompletableFuture<>();
            assistant.chat("what time is it?")
                    .beforeToolExecution(before -> events.add(streamBefore(before.request())))
                    .onToolExecuted(execution -> events.add(streamAfter(execution.request())))
                    .onIntermediateResponse(ignored -> events.add("intermediate"))
                    .onCompleteResponse(done::complete)
                    .onError(done::completeExceptionally)
                    .start();

            done.get(10, SECONDS);

            List<String> captured = List.copyOf(events);
            assertThat(indexOf(captured, "intermediate")).isLessThan(indexOf(captured, streamBefore(toolRequest)));
            assertThat(indexOf(captured, streamBefore(toolRequest))).isLessThan(indexOf(captured, "toolBody:now"));
            assertThat(indexOf(captured, "toolBody:now")).isLessThan(indexOf(captured, streamAfter(toolRequest)));
            assertThat(tool.bodyThread.get()).isNotNull();
            assertThat(tool.bodyThread.get().getName()).isEqualTo("stream-before-tool-executor");
        } finally {
            toolExecutor.shutdownNow();
            model.shutdown();
        }
    }

    @Test
    void throwing_stream_beforeToolExecution_runs_after_intermediate_prevents_tool_body_and_delivers_one_error()
            throws Exception {
        ConcurrentLinkedQueue<String> events = new ConcurrentLinkedQueue<>();
        FastTool tool = new FastTool(events);
        ToolExecutionRequest toolRequest = toolCall("toolu_before_failure", "now");
        RuntimeException beforeFailure = new RuntimeException("before-boom");

        ScriptedStreamingChatModel model =
                new ScriptedStreamingChatModel(List.of(toolRequest), handler -> completeToolTurn(handler, toolRequest));

        ExecutorService toolExecutor = Executors.newCachedThreadPool();
        try {
            Assistant assistant = AiServices.builder(Assistant.class)
                    .streamingChatModel(model)
                    .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                    .tools(tool)
                    .executeToolsConcurrently(toolExecutor)
                    .build();

            CompletableFuture<Void> done = new CompletableFuture<>();
            AtomicInteger errorCount = new AtomicInteger();
            AtomicReference<Throwable> error = new AtomicReference<>();
            assistant.chat("what time is it?")
                    .beforeToolExecution(before -> {
                        events.add(streamBefore(before.request()));
                        throw beforeFailure;
                    })
                    .onToolExecuted(execution -> events.add(streamAfter(execution.request())))
                    .onIntermediateResponse(ignored -> events.add("intermediate"))
                    .onCompleteResponse(ignored -> done.complete(null))
                    .onError(t -> {
                        events.add(error(t));
                        error.set(t);
                        errorCount.incrementAndGet();
                        done.complete(null);
                    })
                    .start();

            done.get(10, SECONDS);

            List<String> captured = List.copyOf(events);
            assertThat(tool.bodyStarted.getCount())
                    .as("throwing beforeToolExecution must stop the tool body. Captured: %s", captured)
                    .isEqualTo(1);
            assertThat(indexOf(captured, "intermediate")).isLessThan(indexOf(captured, streamBefore(toolRequest)));
            assertThat(captured).doesNotContain("toolBody:now", streamAfter(toolRequest));
            assertThat(errorCount).hasValue(1);
            assertThat(error.get()).isSameAs(beforeFailure);
        } finally {
            toolExecutor.shutdownNow();
            model.shutdown();
        }
    }

    @Test
    void builder_tool_callbacks_run_at_actual_execution_time_in_eager_mode_while_stream_output_waits()
            throws Exception {
        ConcurrentLinkedQueue<String> events = new ConcurrentLinkedQueue<>();
        FastTool tool = new FastTool(events);
        ToolExecutionRequest toolRequest = toolCall("toolu_builder_after", "now");
        CountDownLatch builderBefore = new CountDownLatch(1);
        CountDownLatch builderAfter = new CountDownLatch(1);

        ScriptedStreamingChatModel model = new ScriptedStreamingChatModel(List.of(toolRequest), handler -> {
            awaitOrThrow(tool.bodyFinished, "tool body to finish");
            awaitBriefly(builderBefore);
            awaitBriefly(builderAfter);
            completeToolTurn(handler, toolRequest);
        });

        ExecutorService toolExecutor = Executors.newCachedThreadPool();
        try {
            Assistant assistant = AiServices.builder(Assistant.class)
                    .streamingChatModel(model)
                    .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                    .tools(tool)
                    .executeToolsConcurrently(toolExecutor)
                    .beforeToolExecution(before -> {
                        events.add(builderBefore(before.request()));
                        builderBefore.countDown();
                    })
                    .afterToolExecution(execution -> {
                        events.add(builderAfter(execution.request()));
                        builderAfter.countDown();
                    })
                    .build();

            CompletableFuture<ChatResponse> done = new CompletableFuture<>();
            assistant.chat("what time is it?")
                    .onToolExecuted(execution -> events.add(streamAfter(execution.request())))
                    .onIntermediateResponse(ignored -> events.add("intermediate"))
                    .onCompleteResponse(done::complete)
                    .onError(done::completeExceptionally)
                    .start();

            done.get(10, SECONDS);

            List<String> captured = List.copyOf(events);
            assertThat(indexOf(captured, builderBefore(toolRequest))).isLessThan(indexOf(captured, "toolBody:now"));
            assertThat(indexOf(captured, "toolBody:now")).isLessThan(indexOf(captured, builderAfter(toolRequest)));
            assertThat(indexOf(captured, builderAfter(toolRequest))).isLessThan(indexOf(captured, "intermediate"));
            assertThat(indexOf(captured, "intermediate")).isLessThan(indexOf(captured, streamAfter(toolRequest)));
        } finally {
            toolExecutor.shutdownNow();
            model.shutdown();
        }
    }

    @Test
    void terminal_model_error_prevents_late_stream_tool_output_after_error_handler() throws Exception {
        ConcurrentLinkedQueue<String> events = new ConcurrentLinkedQueue<>();
        BlockingTool tool = new BlockingTool(events);
        ToolExecutionRequest toolRequest = toolCall("toolu_terminal_error", "slowNow");
        RuntimeException streamError = new RuntimeException("stream-boom");

        ScriptedStreamingChatModel model = new ScriptedStreamingChatModel(List.of(toolRequest), handler -> {
            awaitOrThrow(tool.bodyStarted, "tool body to start");
            handler.onError(streamError);
        });

        ExecutorService toolExecutor = Executors.newCachedThreadPool();
        try {
            Assistant assistant = AiServices.builder(Assistant.class)
                    .streamingChatModel(model)
                    .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                    .tools(tool)
                    .executeToolsConcurrently(toolExecutor)
                    .build();

            CompletableFuture<Void> done = new CompletableFuture<>();
            CountDownLatch outputAfterError = new CountDownLatch(1);
            CountDownLatch errorDelivered = new CountDownLatch(1);
            AtomicReference<Throwable> error = new AtomicReference<>();
            assistant.chat("what time is it?")
                    .onToolExecuted(execution -> {
                        events.add(streamAfter(execution.request()));
                        if (errorDelivered.getCount() == 0) {
                            outputAfterError.countDown();
                        }
                    })
                    .onIntermediateResponse(ignored -> events.add("intermediate"))
                    .onCompleteResponse(ignored -> done.complete(null))
                    .onError(t -> {
                        events.add(error(t));
                        error.set(t);
                        errorDelivered.countDown();
                        done.complete(null);
                    })
                    .start();

            done.get(10, SECONDS);
            tool.release.countDown();
            awaitOrThrow(tool.bodyFinished, "tool body to finish");

            assertThat(outputAfterError.await(250, MILLISECONDS))
                    .as("late stream tool output must not arrive after errorHandler")
                    .isFalse();
            List<String> captured = List.copyOf(events);
            assertThat(error.get()).isSameAs(streamError);
            assertNoStreamOutputAfterError(captured);
        } finally {
            toolExecutor.shutdownNow();
            model.shutdown();
        }
    }

    @Test
    void throwing_buffered_stream_tool_output_delivers_one_error_and_no_later_tool_output() throws Exception {
        ConcurrentLinkedQueue<String> events = new ConcurrentLinkedQueue<>();
        TwoFastTools tools = new TwoFastTools(events);
        ToolExecutionRequest first = toolCall("toolu_first", "first");
        ToolExecutionRequest second = toolCall("toolu_second", "second");
        RuntimeException callbackFailure = new RuntimeException("callback-boom");
        CountDownLatch builderAfterBoth = new CountDownLatch(2);

        ScriptedStreamingChatModel model = new ScriptedStreamingChatModel(List.of(first, second), handler -> {
            awaitOrThrow(tools.firstFinished, "first tool body to finish");
            awaitOrThrow(tools.secondFinished, "second tool body to finish");
            awaitBriefly(builderAfterBoth);
            completeToolTurn(handler, first, second);
        });

        ExecutorService toolExecutor = Executors.newFixedThreadPool(2);
        try {
            Assistant assistant = AiServices.builder(Assistant.class)
                    .streamingChatModel(model)
                    .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                    .tools(tools)
                    .executeToolsConcurrently(toolExecutor)
                    .afterToolExecution(execution -> builderAfterBoth.countDown())
                    .build();

            CompletableFuture<Void> done = new CompletableFuture<>();
            AtomicInteger errorCount = new AtomicInteger();
            AtomicReference<Throwable> error = new AtomicReference<>();
            CountDownLatch errorDelivered = new CountDownLatch(1);
            CountDownLatch outputAfterError = new CountDownLatch(1);

            assistant.chat("run both tools")
                    .onToolExecuted(execution -> {
                        events.add(streamAfter(execution.request()));
                        if (errorDelivered.getCount() == 0) {
                            outputAfterError.countDown();
                        }
                        if (execution.request().name().equals(first.name())) {
                            throw callbackFailure;
                        }
                    })
                    .onIntermediateResponse(ignored -> events.add("intermediate"))
                    .onCompleteResponse(ignored -> done.complete(null))
                    .onError(t -> {
                        events.add(error(t));
                        error.set(t);
                        errorCount.incrementAndGet();
                        errorDelivered.countDown();
                        done.complete(null);
                    })
                    .start();

            done.get(10, SECONDS);

            assertThat(outputAfterError.await(250, MILLISECONDS))
                    .as("no stream tool output should arrive after the terminal error")
                    .isFalse();
            List<String> captured = List.copyOf(events);
            assertThat(indexOf(captured, "intermediate")).isLessThan(indexOf(captured, streamAfter(first)));
            assertThat(errorCount).hasValue(1);
            assertThat(error.get()).isSameAs(callbackFailure);
            assertNoStreamOutputAfterError(captured);
        } finally {
            toolExecutor.shutdownNow();
            model.shutdown();
        }
    }

    private static class FastTool {

        final ConcurrentLinkedQueue<String> events;
        final CountDownLatch bodyStarted = new CountDownLatch(1);
        final CountDownLatch bodyFinished = new CountDownLatch(1);
        final AtomicReference<Thread> bodyThread = new AtomicReference<>();
        final CountDownLatch eagerSignal;

        FastTool(ConcurrentLinkedQueue<String> events) {
            this(events, null);
        }

        FastTool(ConcurrentLinkedQueue<String> events, CountDownLatch eagerSignal) {
            this.events = events;
            this.eagerSignal = eagerSignal;
        }

        @Tool
        String now() {
            bodyThread.set(Thread.currentThread());
            events.add("toolBody:now");
            bodyStarted.countDown();
            if (eagerSignal != null) {
                eagerSignal.countDown();
            }
            bodyFinished.countDown();
            return "12:00";
        }
    }

    private static class BlockingTool {

        final ConcurrentLinkedQueue<String> events;
        final CountDownLatch bodyStarted = new CountDownLatch(1);
        final CountDownLatch bodyFinished = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);

        BlockingTool(ConcurrentLinkedQueue<String> events) {
            this.events = events;
        }

        @Tool
        String slowNow() throws InterruptedException {
            events.add("toolBody:slowNow");
            bodyStarted.countDown();
            release.await();
            bodyFinished.countDown();
            return "12:00";
        }
    }

    private static class TwoFastTools {

        final ConcurrentLinkedQueue<String> events;
        final CountDownLatch firstFinished = new CountDownLatch(1);
        final CountDownLatch secondFinished = new CountDownLatch(1);

        TwoFastTools(ConcurrentLinkedQueue<String> events) {
            this.events = events;
        }

        @Tool
        String first() {
            events.add("toolBody:first");
            firstFinished.countDown();
            return "first-result";
        }

        @Tool
        String second() {
            events.add("toolBody:second");
            secondFinished.countDown();
            return "second-result";
        }
    }

    private static class ScriptedStreamingChatModel implements StreamingChatModel {

        private final List<ToolExecutionRequest> toolRequests;
        private final Consumer<StreamingChatResponseHandler> afterToolCalls;
        private final ExecutorService driver = Executors.newCachedThreadPool();
        private final AtomicInteger invocations = new AtomicInteger();

        ScriptedStreamingChatModel(
                List<ToolExecutionRequest> toolRequests, Consumer<StreamingChatResponseHandler> afterToolCalls) {
            this.toolRequests = toolRequests;
            this.afterToolCalls = afterToolCalls;
        }

        @Override
        public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
            int invocation = invocations.incrementAndGet();
            driver.execute(() -> {
                try {
                    if (invocation == 1) {
                        for (int i = 0; i < toolRequests.size(); i++) {
                            onCompleteToolCall(handler, new CompleteToolCall(i, toolRequests.get(i)));
                        }
                        afterToolCalls.accept(handler);
                    } else {
                        onCompleteResponse(
                                handler,
                                ChatResponse.builder()
                                        .aiMessage(AiMessage.from("done"))
                                        .build());
                    }
                } catch (Throwable t) {
                    handler.onError(t);
                }
            });
        }

        void shutdown() {
            driver.shutdownNow();
        }
    }

    private static ToolExecutionRequest toolCall(String id, String name) {
        return ToolExecutionRequest.builder()
                .id(id)
                .name(name)
                .arguments("{}")
                .build();
    }

    private static void completeToolTurn(
            StreamingChatResponseHandler handler, ToolExecutionRequest... toolRequests) {
        onCompleteResponse(
                handler,
                ChatResponse.builder()
                        .aiMessage(AiMessage.from(toolRequests))
                        .build());
    }

    private static void awaitOrThrow(CountDownLatch latch, String description) {
        try {
            if (!latch.await(5, SECONDS)) {
                throw new AssertionError("Timed out waiting for " + description);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for " + description, e);
        }
    }

    private static void awaitBriefly(CountDownLatch latch) {
        try {
            latch.await(250, MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting briefly", e);
        }
    }

    private static void spinUntil(BooleanSupplier condition, long timeout, java.util.concurrent.TimeUnit unit) {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
    }

    private static int indexOf(List<String> events, String event) {
        assertThat(events).as("Captured events").contains(event);
        return events.indexOf(event);
    }

    private static void assertNoStreamOutputAfterError(List<String> events) {
        int errorIndex = firstIndexStartingWith(events, "error:");
        List<String> afterError = events.subList(errorIndex + 1, events.size());
        assertThat(afterError.stream().noneMatch(event -> event.startsWith("streamAfter:")))
                .as("Captured events: %s", events)
                .isTrue();
    }

    private static int firstIndexStartingWith(List<String> events, String prefix) {
        for (int i = 0; i < events.size(); i++) {
            if (events.get(i).startsWith(prefix)) {
                return i;
            }
        }
        assertThat(events).as("Captured events").anyMatch(event -> event.startsWith(prefix));
        return -1;
    }

    private static String streamBefore(ToolExecutionRequest request) {
        return "streamBefore:" + request.id();
    }

    private static String streamAfter(ToolExecutionRequest request) {
        return "streamAfter:" + request.id();
    }

    private static String builderBefore(ToolExecutionRequest request) {
        return "builderBefore:" + request.id();
    }

    private static String builderAfter(ToolExecutionRequest request) {
        return "builderAfter:" + request.id();
    }

    private static String error(Throwable error) {
        return "error:" + error.getMessage();
    }
}
