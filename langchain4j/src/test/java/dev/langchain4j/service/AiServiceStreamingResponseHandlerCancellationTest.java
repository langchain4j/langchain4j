package dev.langchain4j.service;

import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onCompleteResponse;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onCompleteToolCall;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ReturnBehavior;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.CompleteToolCall;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.observability.api.listener.ToolExecutedEventListener;
import dev.langchain4j.service.tool.StreamingToolDispatchHook;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class AiServiceStreamingResponseHandlerCancellationTest {

    interface Assistant {
        TokenStream chat(String userMessage);
    }

    @Test
    void cancel_before_response_completion_skips_memory_writes_and_follow_up_chat() throws Exception {
        var memory = MessageWindowChatMemory.withMaxMessages(10);
        AtomicBoolean cancelled = new AtomicBoolean(false);
        var tool = new FastTool();
        var toolRequest = toolCall("toolu_checkpoint1");

        // Flip cancel after the tool body runs but before onCompleteResponse fires —
        // checkpoint #1 must observe it and bail before addToMemory(aiMessage).
        var model = new ScriptedStreamingChatModel(List.of(toolRequest), handler -> {
            awaitOrThrow(tool.bodyFinished, "tool body");
            cancelled.set(true);
            completeToolTurn(handler, toolRequest);
        });

        ExecutorService toolExecutor = Executors.newCachedThreadPool();
        try {
            var assistant = AiServices.builder(Assistant.class)
                    .streamingChatModel(model)
                    .chatMemory(memory)
                    .tools(tool)
                    .executeToolsConcurrently(toolExecutor)
                    .build();

            assistant.chat("hi")
                    .cancelOn(cancelled::get)
                    .onCompleteResponse(r -> {})
                    .onError(t -> {})
                    .start();
            model.awaitFirstInvocationDone();

            List<ChatMessage> stored = memory.messages();
            assertThat(stored.stream().noneMatch(m -> m instanceof AiMessage))
                    .as("checkpoint #1 must skip addToMemory(aiMessage)")
                    .isTrue();
            assertThat(stored.stream().noneMatch(m -> m instanceof ToolExecutionResultMessage))
                    .as("checkpoint #1 must skip the tool-result memory writes")
                    .isTrue();
            assertThat(model.invocations())
                    .as("checkpoint #1 must skip the follow-up chat call")
                    .isEqualTo(1);
        } finally {
            toolExecutor.shutdownNow();
            model.shutdown();
        }
    }

    /** Regression: IMMEDIATE-return short-circuit must not fire onCompleteResponse after cancel. */
    @Test
    void cancel_with_immediate_return_tool_does_not_fire_complete_response_handler() throws Exception {
        var memory = MessageWindowChatMemory.withMaxMessages(10);
        AtomicBoolean cancelled = new AtomicBoolean(false);
        AtomicBoolean completeFired = new AtomicBoolean(false);
        var tool = new ImmediateTool();
        var toolRequest = toolCall("toolu_immediate");

        var model = new ScriptedStreamingChatModel(List.of(toolRequest), handler -> {
            awaitOrThrow(tool.bodyFinished, "tool body");
            cancelled.set(true);
            completeToolTurn(handler, toolRequest);
        });

        ExecutorService toolExecutor = Executors.newCachedThreadPool();
        try {
            var assistant = AiServices.builder(Assistant.class)
                    .streamingChatModel(model)
                    .chatMemory(memory)
                    .tools(tool)
                    .executeToolsConcurrently(toolExecutor)
                    .build();

            assistant.chat("hi")
                    .cancelOn(cancelled::get)
                    .onCompleteResponse(r -> completeFired.set(true))
                    .onError(t -> {})
                    .start();
            model.awaitFirstInvocationDone();

            assertThat(completeFired.get())
                    .as("silent cancellation: onCompleteResponse must NOT fire")
                    .isFalse();
            assertThat(model.invocations()).isEqualTo(1);
        } finally {
            toolExecutor.shutdownNow();
            model.shutdown();
        }
    }

    @Test
    void on_error_after_silent_cancellation_is_ignored() throws Exception {
        var memory = MessageWindowChatMemory.withMaxMessages(10);
        AtomicBoolean cancelled = new AtomicBoolean(false);
        AtomicInteger errors = new AtomicInteger();
        AtomicInteger completes = new AtomicInteger();
        RuntimeException streamError = new RuntimeException("transport closed after cancel");

        var model = new ScriptedStreamingChatModel(List.of(), handler -> {
            cancelled.set(true);
            handler.onError(streamError);
        });

        try {
            var assistant = AiServices.builder(Assistant.class)
                    .streamingChatModel(model)
                    .chatMemory(memory)
                    .build();

            assistant.chat("hi")
                    .cancelOn(cancelled::get)
                    .onCompleteResponse(r -> completes.incrementAndGet())
                    .onError(t -> errors.incrementAndGet())
                    .start();
            model.awaitFirstInvocationDone();

            assertThat(errors.get())
                    .as("provider onError after silent cancellation must stay silent")
                    .isZero();
            assertThat(completes.get())
                    .as("silent cancellation must not be converted to completion")
                    .isZero();
        } finally {
            model.shutdown();
        }
    }

    @Test
    void cancel_during_dispatch_hook_thread_hop_does_not_orphan_assistant_turn() throws Exception {
        var memory = MessageWindowChatMemory.withMaxMessages(10);
        AtomicBoolean cancelled = new AtomicBoolean(false);
        var tool = new FastTool();
        var toolRequest = toolCall("toolu_threadhop");

        // Dispatch hook hops to a worker thread; the test gates the hop with a latch so
        // it can flip cancellation precisely WHILE the work is parked — exactly the window
        // that closed when addToMemory(aiMessage) was moved inside the lambda.
        ExecutorService dispatchExecutor = Executors.newSingleThreadExecutor();
        CountDownLatch hookInvoked = new CountDownLatch(1);
        CountDownLatch holdDispatch = new CountDownLatch(1);
        StreamingToolDispatchHook hook = new StreamingToolDispatchHook() {
            @Override
            public <T> CompletionStage<T> dispatch(Supplier<T> work) {
                hookInvoked.countDown();
                CompletableFuture<T> future = new CompletableFuture<>();
                dispatchExecutor.submit(() -> {
                    try {
                        if (!holdDispatch.await(5, SECONDS)) {
                            future.completeExceptionally(new AssertionError("dispatch hold latch timed out"));
                            return;
                        }
                        future.complete(work.get());
                    } catch (Throwable t) {
                        future.completeExceptionally(t);
                    }
                });
                return future;
            }
        };

        var model = new ScriptedStreamingChatModel(List.of(toolRequest), handler -> {
            completeToolTurn(handler, toolRequest);
        });

        SettleSignal settle = new SettleSignal(hook);

        ExecutorService toolExecutor = Executors.newCachedThreadPool();
        try {
            var assistant = AiServices.builder(Assistant.class)
                    .streamingChatModel(model)
                    .chatMemory(memory)
                    .tools(tool)
                    .executeToolsConcurrently(toolExecutor)
                    .streamingToolDispatchHook(settle.hook())
                    .build();

            assistant.chat("hi")
                    .cancelOn(cancelled::get)
                    .onCompleteResponse(r -> {})
                    .onError(t -> {})
                    .start();

            // Wait for the dispatch hook to be invoked (worker is parked on holdDispatch).
            assertThat(hookInvoked.await(5, SECONDS))
                    .as("dispatch hook should be invoked")
                    .isTrue();

            // Flip cancellation while the work is parked, then release the hop.
            cancelled.set(true);
            holdDispatch.countDown();

            settle.awaitSettled();

            List<ChatMessage> stored = memory.messages();
            assertThat(stored.stream().noneMatch(m -> m instanceof AiMessage))
                    .as("cancellation during dispatch thread-hop must NOT orphan AiMessage(tool_calls) in memory")
                    .isTrue();
            assertThat(stored.stream().noneMatch(m -> m instanceof ToolExecutionResultMessage))
                    .as("no tool results should be in memory after the bail")
                    .isTrue();
            assertThat(model.invocations())
                    .as("follow-up chat call must NOT be issued after cancellation")
                    .isEqualTo(1);
        } finally {
            toolExecutor.shutdownNow();
            dispatchExecutor.shutdownNow();
            model.shutdown();
        }
    }

    @Test
    void cancellation_during_tool_batch_writes_mixed_real_and_placeholder_results() throws Exception {
        var memory = MessageWindowChatMemory.withMaxMessages(10);
        AtomicBoolean cancelled = new AtomicBoolean(false);
        AtomicInteger toolCallCount = new AtomicInteger();

        class TwoTools {
            @Tool
            String alpha(String arg) {
                toolCallCount.incrementAndGet();
                return "alpha-done";
            }

            @Tool
            String beta(String arg) {
                toolCallCount.incrementAndGet();
                return "beta-done";
            }
        }

        ToolExecutionRequest req1 = ToolExecutionRequest.builder()
                .id("c1")
                .name("alpha")
                .arguments("{\"arg0\": \"a\"}")
                .build();
        ToolExecutionRequest req2 = ToolExecutionRequest.builder()
                .id("c2")
                .name("beta")
                .arguments("{\"arg0\": \"b\"}")
                .build();

        var model = new ScriptedStreamingChatModel(
                List.of(req1, req2), handler -> completeToolTurn(handler, req1, req2));

        // Flip cancellation right after alpha's persistence-loop event fires; beta's
        // iteration then observes terminal loop state and must write the placeholder.
        ToolExecutedEventListener flipOnAlpha = event -> {
            if ("alpha".equals(event.request().name())) {
                cancelled.set(true);
            }
        };

        SettleSignal settle = new SettleSignal();

        try {
            var assistant = AiServices.builder(Assistant.class)
                    .streamingChatModel(model)
                    .chatMemory(memory)
                    .tools(new TwoTools())
                    // Deferred batch path (no executeToolsConcurrently) — both tools complete
                    // through ToolBatchDispatcher before the persistence loop starts, so the
                    // listener-triggered flip falls cleanly between persistence iterations.
                    .registerListener(flipOnAlpha)
                    .streamingToolDispatchHook(settle.hook())
                    .build();

            assistant.chat("hi")
                    .cancelOn(cancelled::get)
                    .onCompleteResponse(r -> {})
                    .onError(t -> {})
                    .start();
            settle.awaitSettled();

            List<ChatMessage> stored = memory.messages();

            long requests = stored.stream()
                    .filter(m -> m instanceof AiMessage)
                    .map(m -> (AiMessage) m)
                    .filter(AiMessage::hasToolExecutionRequests)
                    .mapToLong(m -> m.toolExecutionRequests().size())
                    .sum();
            List<ToolExecutionResultMessage> resultMessages = stored.stream()
                    .filter(m -> m instanceof ToolExecutionResultMessage)
                    .map(m -> (ToolExecutionResultMessage) m)
                    .toList();

            assertThat(resultMessages)
                    .as("memory invariant: every tool_use request must have a matching tool_result")
                    .hasSize((int) requests)
                    .hasSize(2);

            ToolExecutionResultMessage alphaResult = resultMessages.stream()
                    .filter(m -> "c1".equals(m.id()))
                    .findFirst()
                    .orElseThrow();
            ToolExecutionResultMessage betaResult = resultMessages.stream()
                    .filter(m -> "c2".equals(m.id()))
                    .findFirst()
                    .orElseThrow();

            assertThat(alphaResult.text())
                    .as("alpha completed before cancel flipped, so its real result must survive")
                    .isEqualTo("alpha-done");
            assertThat(betaResult.text())
                    .as("beta's persistence iteration sees terminal loop state and must write the placeholder")
                    .isEqualTo(AiServiceStreamingResponseHandler.CANCELLED_TOOL_RESULT_TEXT);

            assertThat(toolCallCount.get())
                    .as("both tool bodies execute through the dispatcher before the persistence loop runs")
                    .isEqualTo(2);
            assertThat(model.invocations())
                    .as("checkpoint #3 must skip the follow-up chat call")
                    .isEqualTo(1);
        } finally {
            model.shutdown();
        }
    }

    @Test
    void silent_cancellation_suppresses_stream_level_tool_callbacks_after_it_is_observed() throws Exception {
        var memory = MessageWindowChatMemory.withMaxMessages(10);
        AtomicBoolean cancelled = new AtomicBoolean(false);
        AtomicInteger streamToolCallbacks = new AtomicInteger();
        AtomicInteger toolBodies = new AtomicInteger();

        class TwoTools {
            @Tool
            String alpha(String arg) {
                toolBodies.incrementAndGet();
                return "alpha-done";
            }

            @Tool
            String beta(String arg) {
                toolBodies.incrementAndGet();
                return "beta-done";
            }
        }

        ToolExecutionRequest req1 = ToolExecutionRequest.builder()
                .id("stream-cb-1")
                .name("alpha")
                .arguments("{\"arg0\": \"a\"}")
                .build();
        ToolExecutionRequest req2 = ToolExecutionRequest.builder()
                .id("stream-cb-2")
                .name("beta")
                .arguments("{\"arg0\": \"b\"}")
                .build();

        var model = new ScriptedStreamingChatModel(
                List.of(req1, req2), handler -> completeToolTurn(handler, req1, req2));
        SettleSignal settle = new SettleSignal();

        try {
            var assistant = AiServices.builder(Assistant.class)
                    .streamingChatModel(model)
                    .chatMemory(memory)
                    .tools(new TwoTools())
                    .streamingToolDispatchHook(settle.hook())
                    .build();

            assistant.chat("hi")
                    .cancelOn(cancelled::get)
                    .onToolExecuted(execution -> {
                        streamToolCallbacks.incrementAndGet();
                        cancelled.set(true);
                    })
                    .onCompleteResponse(r -> {})
                    .onError(t -> {})
                    .start();
            settle.awaitSettled();

            assertThat(toolBodies.get())
                    .as("deferred dispatch has already entered the batch when cancellation is observed")
                    .isEqualTo(2);
            assertThat(streamToolCallbacks.get())
                    .as("stream-level tool callbacks after silent cancellation must be suppressed")
                    .isEqualTo(1);
            assertThat(model.invocations())
                    .as("silent cancellation must skip the follow-up call")
                    .isEqualTo(1);
        } finally {
            model.shutdown();
        }
    }

    /**
     * Exercises the failure-driven {@code catch (CancellationException)} branch in
     * {@code AiServiceStreamingResponseHandler.gatherScheduledToolResults}. With eager
     * scheduling, an in-flight tool future can be cancelled by {@code onError} while
     * {@code gatherScheduledToolResults} is iterating. That cancellation must be treated as
     * failure termination, not silent cancellation.
     *
     * <p>The test pins this end-to-end:
     * <ul>
     *   <li>alpha is eagerly scheduled and parks on a latch (its future is in-flight).</li>
     *   <li>beta is eagerly scheduled and completes normally (real result).</li>
     *   <li>A custom dispatch hook hops the post-{@code onCompleteResponse} work to a worker
     *       thread, so the model driver thread is free to fire {@code onError} concurrently.</li>
     *   <li>{@code onError} marks the loop failed and cancels slow's future. {@code gather}'s
     *       {@code future.get(slow)} hits the {@code CancellationException} branch.</li>
     *   <li>The completed fast result is preserved, slow gets a failure placeholder, and no
     *       follow-up chat call is issued.</li>
     * </ul>
     */
    @Test
    void eager_path_future_cancelled_by_on_error_writes_failure_placeholder_and_does_not_continue() throws Exception {
        CountDownLatch assistantToolMessagePersisted = new CountDownLatch(1);
        ChatMemory memory = new SignalOnAssistantToolMessageMemory(
                MessageWindowChatMemory.withMaxMessages(10), assistantToolMessagePersisted);
        CountDownLatch slowToolStarted = new CountDownLatch(1);
        CountDownLatch slowToolProceed = new CountDownLatch(1);
        CountDownLatch errorDelivered = new CountDownLatch(1);
        AtomicReference<Throwable> errorReceived = new AtomicReference<>();

        class TwoEagerTools {
            @Tool
            String fast(String arg) {
                return "fast-done";
            }

            @Tool
            String slow(String arg) {
                slowToolStarted.countDown();
                try {
                    // Stay in-flight until the test releases us. The future is cancelled while
                    // this body is parked, so the returned value is discarded by the
                    // CompletableFuture (cancel makes future.complete(...) a no-op).
                    slowToolProceed.await(5, SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return "slow-done";
            }
        }

        // Schedule fast first, slow second. With a single-threaded tool executor, fast runs
        // to completion (its future.complete is called) BEFORE slow starts; once slowToolStarted
        // fires, fast's future is guaranteed done and onError will not cancel it.
        ToolExecutionRequest fastRequest = ToolExecutionRequest.builder()
                .id("fast-id")
                .name("fast")
                .arguments("{\"arg0\": \"a\"}")
                .build();
        ToolExecutionRequest slowRequest = ToolExecutionRequest.builder()
                .id("slow-id")
                .name("slow")
                .arguments("{\"arg0\": \"b\"}")
                .build();

        ExecutorService dispatchExecutor = Executors.newSingleThreadExecutor();
        CountDownLatch dispatchStarted = new CountDownLatch(1);
        CountDownLatch dispatchWorkDone = new CountDownLatch(1);
        StreamingToolDispatchHook hook = new StreamingToolDispatchHook() {
            @Override
            public <T> CompletionStage<T> dispatch(Supplier<T> work) {
                CompletableFuture<T> future = new CompletableFuture<>();
                dispatchExecutor.submit(() -> {
                    dispatchStarted.countDown();
                    try {
                        future.complete(work.get());
                    } catch (Throwable t) {
                        future.completeExceptionally(t);
                    } finally {
                        dispatchWorkDone.countDown();
                    }
                });
                return future;
            }
        };

        var model = new ScriptedStreamingChatModel(List.of(fastRequest, slowRequest), handler -> {
            // The single-threaded tool executor ran fast first, so by the time slowToolStarted
            // fires, fast's future.complete has already been called and that future is DONE.
            awaitOrThrow(slowToolStarted, "slow tool to start");
            completeToolTurn(handler, fastRequest, slowRequest);
            // Wait for the dispatch hook's worker to enter the work supplier. From this point
            // slow's future will either be cancelled before gather's get(slow) (immediate
            // CancellationException) or while parked in get(slow) (the blocked get wakes
            // with CancellationException). Either ordering exercises the catch.
            awaitOrThrow(dispatchStarted, "dispatch worker to start");
            awaitOrThrow(assistantToolMessagePersisted, "assistant tool message to be persisted");
            handler.onError(new RuntimeException("simulated stream abort"));
        });

        ExecutorService toolExecutor = Executors.newSingleThreadExecutor();
        try {
            var assistant = AiServices.builder(Assistant.class)
                    .streamingChatModel(model)
                    .chatMemory(memory)
                    .tools(new TwoEagerTools())
                    .executeToolsConcurrently(toolExecutor)
                    .streamingToolDispatchHook(hook)
                    .build();

            assistant.chat("hi")
                    .onCompleteResponse(r -> {})
                    .onError(t -> {
                        errorReceived.set(t);
                        errorDelivered.countDown();
                    })
                    .start();

            // Wait for the dispatch worker to finish — that's when memory writes for the
            // first iteration are settled and checkpoint #3 has had a chance to bail.
            assertThat(dispatchWorkDone.await(5, SECONDS))
                    .as("dispatch work should complete")
                    .isTrue();
            slowToolProceed.countDown();

            assertThat(errorDelivered.await(5, SECONDS))
                    .as("the simulated onError should surface to the consumer")
                    .isTrue();
            assertThat(errorReceived.get()).isNotNull();

            List<ChatMessage> stored = memory.messages();
            long requests = stored.stream()
                    .filter(m -> m instanceof AiMessage)
                    .map(m -> (AiMessage) m)
                    .filter(AiMessage::hasToolExecutionRequests)
                    .mapToLong(m -> m.toolExecutionRequests().size())
                    .sum();
            List<ToolExecutionResultMessage> resultMessages = stored.stream()
                    .filter(m -> m instanceof ToolExecutionResultMessage)
                    .map(m -> (ToolExecutionResultMessage) m)
                    .toList();

            assertThat(resultMessages)
                    .as("memory invariant: every tool_use request must have a matching tool_result")
                    .hasSize((int) requests)
                    .hasSize(2);

            ToolExecutionResultMessage fastResult = resultMessages.stream()
                    .filter(m -> "fast-id".equals(m.id()))
                    .findFirst()
                    .orElseThrow();
            ToolExecutionResultMessage slowResult = resultMessages.stream()
                    .filter(m -> "slow-id".equals(m.id()))
                    .findFirst()
                    .orElseThrow();

            assertThat(fastResult.text())
                    .as("fast completed before failure termination, so its real result is preserved")
                    .isEqualTo("fast-done");
            assertThat(slowResult.text())
                    .as("slow's future was cancelled by onError, so it must not look like silent cancellation")
                    .isEqualTo(AiServiceStreamingResponseHandler.FAILED_TOOL_RESULT_TEXT);
            assertThat(slowResult.isError())
                    .as("failed terminal placeholders must be marked as tool errors")
                    .isTrue();
            assertThat(model.invocations())
                    .as("failed stream must skip the follow-up call")
                    .isEqualTo(1);
        } finally {
            slowToolProceed.countDown();
            toolExecutor.shutdownNow();
            dispatchExecutor.shutdownNow();
            model.shutdown();
        }
    }

    @Test
    void eager_path_future_cancelled_by_silent_cancel_writes_cancellation_placeholder_and_does_not_continue()
            throws Exception {
        AtomicBoolean cancelled = new AtomicBoolean(false);
        ChatMemory memory = new CancelAfterAssistantToolMessageMemory(
                MessageWindowChatMemory.withMaxMessages(10), cancelled);
        CountDownLatch slowToolStarted = new CountDownLatch(1);
        CountDownLatch slowToolProceed = new CountDownLatch(1);

        class SlowEagerTool {
            @Tool
            String slow(String arg) {
                slowToolStarted.countDown();
                try {
                    slowToolProceed.await(5, SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return "slow-done";
            }
        }

        ToolExecutionRequest slowRequest = ToolExecutionRequest.builder()
                .id("silent-slow-id")
                .name("slow")
                .arguments("{\"arg0\": \"b\"}")
                .build();

        var model = new ScriptedStreamingChatModel(List.of(slowRequest), handler -> {
            awaitOrThrow(slowToolStarted, "slow tool to start");
            completeToolTurn(handler, slowRequest);
        });

        ExecutorService toolExecutor = Executors.newSingleThreadExecutor();
        try {
            var assistant = AiServices.builder(Assistant.class)
                    .streamingChatModel(model)
                    .chatMemory(memory)
                    .tools(new SlowEagerTool())
                    .executeToolsConcurrently(toolExecutor)
                    .build();

            assistant.chat("hi")
                    .cancelOn(cancelled::get)
                    .onCompleteResponse(r -> {})
                    .onError(t -> {})
                    .start();

            model.awaitFirstInvocationDone();

            List<ToolExecutionResultMessage> resultMessages = memory.messages().stream()
                    .filter(m -> m instanceof ToolExecutionResultMessage)
                    .map(m -> (ToolExecutionResultMessage) m)
                    .toList();

            assertThat(resultMessages).hasSize(1);
            assertThat(resultMessages.get(0).text())
                    .as("silent cancellation after assistant persistence writes cancellation placeholder")
                    .isEqualTo(AiServiceStreamingResponseHandler.CANCELLED_TOOL_RESULT_TEXT);
            assertThat(model.invocations())
                    .as("silent cancellation must skip the follow-up call")
                    .isEqualTo(1);
        } finally {
            slowToolProceed.countDown();
            toolExecutor.shutdownNow();
            model.shutdown();
        }
    }

    /**
     * Sanity check that the non-tool branch still persists the assistant message — the move
     * of {@code addToMemory(aiMessage)} into branch-specific positions must not lose the
     * write for a plain text response.
     */
    @Test
    void plain_text_response_without_tools_still_writes_assistant_message_to_memory() throws Exception {
        var memory = MessageWindowChatMemory.withMaxMessages(10);
        AtomicBoolean cancelled = new AtomicBoolean(false);

        var model = new ScriptedStreamingChatModel(List.of(), handler -> {
            // No tool calls — the model goes straight to a final text response.
            onCompleteResponse(
                    handler,
                    ChatResponse.builder().aiMessage(AiMessage.from("hello back")).build());
        });

        try {
            CountDownLatch done = new CountDownLatch(1);
            var assistant = AiServices.builder(Assistant.class)
                    .streamingChatModel(model)
                    .chatMemory(memory)
                    .build();

            assistant.chat("hi")
                    .cancelOn(cancelled::get)
                    .onCompleteResponse(r -> done.countDown())
                    .onError(t -> done.countDown())
                    .start();

            assertThat(done.await(5, SECONDS)).as("loop should complete").isTrue();

            List<ChatMessage> stored = memory.messages();
            boolean hasAssistantTurn = stored.stream()
                    .anyMatch(m -> m instanceof AiMessage ai && "hello back".equals(ai.text()));
            assertThat(hasAssistantTurn)
                    .as("non-tool response must still be persisted (the addToMemory move keeps both branches)")
                    .isTrue();
        } finally {
            model.shutdown();
        }
    }

    /**
     * Regression: a throw from {@code completeResponseHandler} must reach {@code errorHandler}.
     * Pre-fix {@code loopState.complete()} ran before the user callback, so the resulting
     * {@code onError} round-trip hit a COMPLETED state machine and was silently dropped.
     */
    @Test
    void complete_response_handler_throw_is_reported_to_error_handler() throws Exception {
        var memory = MessageWindowChatMemory.withMaxMessages(10);
        RuntimeException boom = new RuntimeException("boom from complete handler");

        var model = new ScriptedStreamingChatModel(List.of(), handler -> {
            onCompleteResponse(
                    handler,
                    ChatResponse.builder().aiMessage(AiMessage.from("hello back")).build());
        });

        try {
            CountDownLatch errorFired = new CountDownLatch(1);
            AtomicReference<Throwable> captured = new AtomicReference<>();

            var assistant = AiServices.builder(Assistant.class)
                    .streamingChatModel(model)
                    .chatMemory(memory)
                    .build();

            assistant.chat("hi")
                    .onCompleteResponse(r -> {
                        throw boom;
                    })
                    .onError(t -> {
                        captured.set(t);
                        errorFired.countDown();
                    })
                    .start();

            assertThat(errorFired.await(5, SECONDS))
                    .as("errorHandler must observe the throw from completeResponseHandler")
                    .isTrue();
            assertThat(captured.get())
                    .as("the throwable from completeResponseHandler must be reported verbatim")
                    .isSameAs(boom);
        } finally {
            model.shutdown();
        }
    }

    /**
     * Regression: a throw from {@code addToMemory(aiMessage)} inside the dispatch lambda must reach
     * {@code errorHandler}. Pre-fix the call sat outside the lambda's try/catch, and even the default
     * INLINE hook would swallow the throw into an unobserved failed CompletionStage.
     */
    @Test
    void add_to_memory_throw_inside_dispatch_lambda_is_reported_to_error_handler() throws Exception {
        RuntimeException boom = new RuntimeException("boom from memory");
        var memory = new ThrowingMemory(MessageWindowChatMemory.withMaxMessages(10), boom);
        var tool = new FastTool();
        var toolRequest = toolCall("toolu_throw_memory");

        var model = new ScriptedStreamingChatModel(List.of(toolRequest), handler -> {
            completeToolTurn(handler, toolRequest);
        });

        ExecutorService toolExecutor = Executors.newCachedThreadPool();
        try {
            CountDownLatch errorFired = new CountDownLatch(1);
            AtomicReference<Throwable> captured = new AtomicReference<>();

            var assistant = AiServices.builder(Assistant.class)
                    .streamingChatModel(model)
                    .chatMemory(memory)
                    .tools(tool)
                    .executeToolsConcurrently(toolExecutor)
                    .build();

            assistant.chat("hi")
                    .onCompleteResponse(r -> {})
                    .onError(t -> {
                        captured.set(t);
                        errorFired.countDown();
                    })
                    .start();

            assertThat(errorFired.await(5, SECONDS))
                    .as("errorHandler must observe the throw from addToMemory inside the dispatch lambda")
                    .isTrue();
            assertThat(captured.get())
                    .as("the throwable from the memory must be reported verbatim")
                    .isSameAs(boom);
        } finally {
            toolExecutor.shutdownNow();
            model.shutdown();
        }
    }

    private static class FastTool {
        final CountDownLatch bodyFinished = new CountDownLatch(1);

        @Tool
        String now() {
            bodyFinished.countDown();
            return "12:00";
        }
    }

    private static class ImmediateTool {
        final CountDownLatch bodyFinished = new CountDownLatch(1);

        @Tool(returnBehavior = ReturnBehavior.IMMEDIATE)
        String now() {
            bodyFinished.countDown();
            return "12:00";
        }
    }

    /**
     * First invocation emits the tool calls then runs {@code afterToolCalls} (where tests inject
     * cancellation timing). Any later invocation would return a plain "done" response — used to
     * detect that the follow-up chat call was issued.
     */
    private static class ScriptedStreamingChatModel implements StreamingChatModel {
        private final List<ToolExecutionRequest> toolRequests;
        private final Consumer<StreamingChatResponseHandler> afterToolCalls;
        private final ExecutorService driver = Executors.newCachedThreadPool();
        private final AtomicInteger invocations = new AtomicInteger();
        private final CompletableFuture<Void> firstInvocationDone = new CompletableFuture<>();

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
                } finally {
                    if (invocation == 1) firstInvocationDone.complete(null);
                }
            });
        }

        int invocations() {
            return invocations.get();
        }

        void awaitFirstInvocationDone() throws Exception {
            try {
                firstInvocationDone.get(5, SECONDS);
            } catch (java.util.concurrent.TimeoutException e) {
                throw new AssertionError("first invocation did not settle within 5s", e);
            }
        }

        void shutdown() {
            driver.shutdownNow();
        }
    }

    private static ToolExecutionRequest toolCall(String id) {
        return ToolExecutionRequest.builder().id(id).name("now").arguments("{}").build();
    }

    private static void completeToolTurn(StreamingChatResponseHandler handler, ToolExecutionRequest... requests) {
        onCompleteResponse(
                handler, ChatResponse.builder().aiMessage(AiMessage.from(requests)).build());
    }

    private static void awaitOrThrow(CountDownLatch latch, String what) {
        try {
            if (!latch.await(5, SECONDS)) throw new AssertionError("timed out waiting for " + what);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted waiting for " + what, e);
        }
    }

    private static class CancelAfterAssistantToolMessageMemory implements ChatMemory {
        private final ChatMemory delegate;
        private final AtomicBoolean cancelled;

        CancelAfterAssistantToolMessageMemory(ChatMemory delegate, AtomicBoolean cancelled) {
            this.delegate = delegate;
            this.cancelled = cancelled;
        }

        @Override
        public Object id() {
            return delegate.id();
        }

        @Override
        public void add(ChatMessage message) {
            delegate.add(message);
            if (message instanceof AiMessage aiMessage && aiMessage.hasToolExecutionRequests()) {
                cancelled.set(true);
            }
        }

        @Override
        public List<ChatMessage> messages() {
            return delegate.messages();
        }

        @Override
        public void clear() {
            delegate.clear();
        }
    }

    private static class SignalOnAssistantToolMessageMemory implements ChatMemory {
        private final ChatMemory delegate;
        private final CountDownLatch assistantToolMessagePersisted;

        SignalOnAssistantToolMessageMemory(ChatMemory delegate, CountDownLatch assistantToolMessagePersisted) {
            this.delegate = delegate;
            this.assistantToolMessagePersisted = assistantToolMessagePersisted;
        }

        @Override
        public Object id() {
            return delegate.id();
        }

        @Override
        public void add(ChatMessage message) {
            delegate.add(message);
            if (message instanceof AiMessage aiMessage && aiMessage.hasToolExecutionRequests()) {
                assistantToolMessagePersisted.countDown();
            }
        }

        @Override
        public List<ChatMessage> messages() {
            return delegate.messages();
        }

        @Override
        public void clear() {
            delegate.clear();
        }
    }

    private static class ThrowingMemory implements ChatMemory {
        private final ChatMemory delegate;
        private final RuntimeException toThrow;

        ThrowingMemory(ChatMemory delegate, RuntimeException toThrow) {
            this.delegate = delegate;
            this.toThrow = toThrow;
        }

        @Override
        public Object id() {
            return delegate.id();
        }

        @Override
        public void add(ChatMessage message) {
            if (message instanceof AiMessage aiMessage && aiMessage.hasToolExecutionRequests()) {
                throw toThrow;
            }
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
    }

    /** Completes only after the dispatch lambda returns — used in place of bounded-sleep polls. */
    private static class SettleSignal {
        private final CompletableFuture<Void> settled = new CompletableFuture<>();
        private final StreamingToolDispatchHook delegate;

        SettleSignal() {
            this(null);
        }

        SettleSignal(StreamingToolDispatchHook delegate) {
            this.delegate = delegate;
        }

        StreamingToolDispatchHook hook() {
            return new StreamingToolDispatchHook() {
                @Override
                public <T> CompletionStage<T> dispatch(Supplier<T> work) {
                    CompletionStage<T> stage = delegate != null
                            ? delegate.dispatch(work)
                            : StreamingToolDispatchHook.INLINE.dispatch(work);
                    return stage.whenComplete((r, t) -> {
                        if (t != null) settled.completeExceptionally(t);
                        else settled.complete(null);
                    });
                }
            };
        }

        void awaitSettled() throws Exception {
            try {
                settled.get(5, SECONDS);
            } catch (java.util.concurrent.TimeoutException e) {
                throw new AssertionError("dispatch lambda did not settle within 5s", e);
            }
        }
    }
}
