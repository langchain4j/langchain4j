package dev.langchain4j.service.tool;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.mock.StreamingChatModelMock;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/**
 * Validates {@link AiServices#streamingToolDispatchHook(StreamingToolDispatchHook)} — the
 * integration SPI used by downstream framework integrators (e.g. {@code quarkus-langchain4j})
 * to control threading and context propagation around the streaming tool batch dispatch.
 */
class StreamingToolDispatchHookTest {

    interface StreamingAssistant {
        TokenStream chat(String message);
    }

    static class CountingTool {
        final AtomicInteger calls = new AtomicInteger();

        @Tool
        public String doWork(String arg) {
            calls.incrementAndGet();
            return "ok-" + arg;
        }
    }

    @Test
    void framework_dispatch_hook_receives_the_tool_batch_dispatch() throws Exception {
        CountingTool tool = new CountingTool();

        AiMessage twoToolCalls =
                AiMessage.from(toolCall("c1", "a"), toolCall("c2", "b"));
        AiMessage finalAnswer = AiMessage.from("done");
        StreamingChatModelMock model = StreamingChatModelMock.thatAlwaysStreams(twoToolCalls, finalAnswer);

        AtomicInteger hookInvocations = new AtomicInteger();
        AtomicReference<Thread> hookThread = new AtomicReference<>();
        StreamingToolDispatchHook hook = new StreamingToolDispatchHook() {
            @Override
            public <T> CompletionStage<T> dispatch(Supplier<T> work) {
                hookInvocations.incrementAndGet();
                hookThread.set(Thread.currentThread());
                // Run inline (just like INLINE) so the test stays deterministic, but record
                // that the hook was invoked.
                try {
                    return CompletableFuture.completedFuture(work.get());
                } catch (Throwable t) {
                    CompletableFuture<T> failed = new CompletableFuture<>();
                    failed.completeExceptionally(t);
                    return failed;
                }
            }
        };

        StreamingAssistant assistant = AiServices.builder(StreamingAssistant.class)
                .streamingChatModel(model)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .tools(tool)
                .streamingToolDispatchHook(hook)
                .build();

        CompletableFuture<ChatResponse> future = new CompletableFuture<>();
        assistant
                .chat("go")
                .onPartialResponse(ignored -> {})
                .onCompleteResponse(future::complete)
                .onError(future::completeExceptionally)
                .start();

        ChatResponse response = future.get(10, TimeUnit.SECONDS);
        assertThat(response.aiMessage().text()).isEqualTo("done");
        assertThat(tool.calls.get()).isEqualTo(2);
        // The hook is invoked once per LLM response that contains tool calls.
        assertThat(hookInvocations.get())
                .as("hook should be invoked once for the response that contains tool calls")
                .isEqualTo(1);
        assertThat(hookThread.get()).isNotNull();
    }

    @Test
    void without_hook_default_inline_dispatch_runs_tools() throws Exception {
        CountingTool tool = new CountingTool();
        AiMessage oneToolCall = AiMessage.from(toolCall("c1", "a"));
        AiMessage finalAnswer = AiMessage.from("done");
        StreamingChatModelMock model = StreamingChatModelMock.thatAlwaysStreams(oneToolCall, finalAnswer);

        StreamingAssistant assistant = AiServices.builder(StreamingAssistant.class)
                .streamingChatModel(model)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .tools(tool)
                // No hook configured — the INLINE default runs inline.
                .build();

        CompletableFuture<ChatResponse> future = new CompletableFuture<>();
        assistant
                .chat("go")
                .onPartialResponse(ignored -> {})
                .onCompleteResponse(future::complete)
                .onError(future::completeExceptionally)
                .start();

        ChatResponse response = future.get(10, TimeUnit.SECONDS);
        assertThat(response.aiMessage().text()).isEqualTo("done");
        assertThat(tool.calls.get()).isEqualTo(1);
    }

    @Test
    void framework_dispatch_hook_can_switch_to_a_worker_thread() throws Exception {
        // Validates the SPI's primary use case: a framework integrator submits the supplied
        // work to its own executor (e.g. a Vert.x worker pool or a virtual thread) and the tool
        // executor runs on that thread — NOT on the calling/streaming-callback thread.
        //
        // The streaming flow continues after the worker thread runs the supplier, so the test
        // must wait for the entire stream (including the follow-up final response) to complete.
        CountingTool tool = new CountingTool();
        AtomicReference<Thread> toolThread = new AtomicReference<>();
        // Wrap @Tool to capture the executing thread.
        class ThreadCapturingTool {
            @Tool
            public String doWork(String arg) {
                toolThread.set(Thread.currentThread());
                return tool.doWork(arg);
            }
        }
        ThreadCapturingTool threadCapturingTool = new ThreadCapturingTool();

        AiMessage twoToolCalls = AiMessage.from(toolCall("c1", "a"), toolCall("c2", "b"));
        AiMessage finalAnswer = AiMessage.from("done");
        StreamingChatModelMock model = StreamingChatModelMock.thatAlwaysStreams(twoToolCalls, finalAnswer);

        ThreadFactory workerThreadFactory = r -> {
            Thread t = new Thread(r, "test-dispatch-hook-worker");
            t.setDaemon(true);
            return t;
        };
        ExecutorService workerExecutor = Executors.newSingleThreadExecutor(workerThreadFactory);
        try {
            AtomicInteger hookInvocations = new AtomicInteger();
            AtomicReference<Thread> hookSubmitThread = new AtomicReference<>();
            AtomicReference<Thread> workerThreadFromHook = new AtomicReference<>();
            CountDownLatch workSubmittedLatch = new CountDownLatch(1);

            StreamingToolDispatchHook hook = new StreamingToolDispatchHook() {
                @Override
                public <T> CompletionStage<T> dispatch(Supplier<T> work) {
                    hookInvocations.incrementAndGet();
                    hookSubmitThread.set(Thread.currentThread());
                    CompletableFuture<T> future = new CompletableFuture<>();
                    workerExecutor.submit(() -> {
                        workerThreadFromHook.set(Thread.currentThread());
                        try {
                            future.complete(work.get());
                        } catch (Throwable t) {
                            future.completeExceptionally(t);
                        } finally {
                            workSubmittedLatch.countDown();
                        }
                    });
                    return future;
                }
            };

            StreamingAssistant assistant = AiServices.builder(StreamingAssistant.class)
                    .streamingChatModel(model)
                    .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                    .tools(threadCapturingTool)
                    .streamingToolDispatchHook(hook)
                    .build();

            CompletableFuture<ChatResponse> future = new CompletableFuture<>();
            assistant
                    .chat("go")
                    .onPartialResponse(ignored -> {})
                    .onCompleteResponse(future::complete)
                    .onError(future::completeExceptionally)
                    .start();

            ChatResponse response = future.get(10, TimeUnit.SECONDS);
            // Worker thread accepted the submission.
            assertThat(workSubmittedLatch.await(5, TimeUnit.SECONDS))
                    .as("worker should have run the submitted supplier")
                    .isTrue();

            assertThat(response.aiMessage().text()).isEqualTo("done");
            assertThat(tool.calls.get()).isEqualTo(2);

            // Hook was invoked exactly once for the single tool-call response.
            assertThat(hookInvocations.get())
                    .as("hook should be invoked once per tool-call response")
                    .isEqualTo(1);

            // The tool executor must have run on the framework's worker thread, NOT on the
            // thread that invoked the hook (which would be the streaming callback thread).
            assertThat(toolThread.get())
                    .as("tool should have executed on the worker thread")
                    .isNotNull()
                    .isEqualTo(workerThreadFromHook.get());
            assertThat(toolThread.get().getName()).isEqualTo("test-dispatch-hook-worker");
            assertThat(toolThread.get())
                    .as("tool must not have executed on the hook-submit (streaming-callback) thread")
                    .isNotEqualTo(hookSubmitThread.get());
            assertThat(toolThread.get())
                    .as("tool must not have executed on the main/test thread")
                    .isNotEqualTo(Thread.currentThread());
        } finally {
            workerExecutor.shutdownNow();
            workerExecutor.awaitTermination(2, TimeUnit.SECONDS);
        }
    }

    @Test
    void hook_returning_without_invoking_work_silently_drops_the_batch() throws Exception {
        // Without a per-tool executor, tools run inside the hook's work supplier. If the hook
        // drops that supplier, no tool executor runs and no follow-up streaming inference is issued.
        // The test must NOT hang waiting for a final response (since none will arrive), so it uses
        // a short timeout and asserts the future remains incomplete.
        CountingTool tool = new CountingTool();
        AiMessage oneToolCall = AiMessage.from(toolCall("c1", "a"));
        AiMessage finalAnswer = AiMessage.from("done");
        StreamingChatModelMock model = StreamingChatModelMock.thatAlwaysStreams(oneToolCall, finalAnswer);

        AtomicInteger hookInvocations = new AtomicInteger();
        StreamingToolDispatchHook droppingHook = new StreamingToolDispatchHook() {
            @Override
            public <T> CompletionStage<T> dispatch(Supplier<T> work) {
                hookInvocations.incrementAndGet();
                // Deliberately do NOT invoke work.get().
                return CompletableFuture.completedFuture(null);
            }
        };

        StreamingAssistant assistant = AiServices.builder(StreamingAssistant.class)
                .streamingChatModel(model)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .tools(tool)
                .streamingToolDispatchHook(droppingHook)
                .build();

        CompletableFuture<ChatResponse> future = new CompletableFuture<>();
        assistant
                .chat("go")
                .onPartialResponse(ignored -> {})
                .onCompleteResponse(future::complete)
                .onError(future::completeExceptionally)
                .start();

        // Give the streaming pipeline ample time to (a) deliver the tool-call response,
        // (b) invoke the hook, and (c) finish. Since work was never invoked, no follow-up
        // inference is issued and the final-response future should never complete.
        try {
            future.get(1, TimeUnit.SECONDS);
            // If we reach here, the future completed — that's a contract violation.
            throw new AssertionError(
                    "Final response future completed even though hook dropped the batch: " + future.get());
        } catch (TimeoutException expected) {
            // Expected: no final response when batch is dropped.
        }

        assertThat(hookInvocations.get())
                .as("hook should have been invoked for the tool-call response")
                .isEqualTo(1);
        assertThat(tool.calls.get())
                .as("tool executor must not run when the hook drops the batch")
                .isZero();
        assertThat(future).as("no final response should have been delivered").isNotDone();
    }

    @Test
    void hook_returning_without_invoking_work_does_not_cancel_already_scheduled_tool_executor_work()
            throws Exception {
        CountingTool tool = new CountingTool();
        AiMessage oneToolCall = AiMessage.from(toolCall("c1", "a"));
        AiMessage finalAnswer = AiMessage.from("done");
        StreamingChatModelMock model = StreamingChatModelMock.thatAlwaysStreams(oneToolCall, finalAnswer);

        AtomicInteger hookInvocations = new AtomicInteger();
        StreamingToolDispatchHook droppingHook = new StreamingToolDispatchHook() {
            @Override
            public <T> CompletionStage<T> dispatch(Supplier<T> work) {
                hookInvocations.incrementAndGet();
                // Deliberately do NOT invoke work.get().
                return CompletableFuture.completedFuture(null);
            }
        };

        StreamingAssistant assistant = AiServices.builder(StreamingAssistant.class)
                .streamingChatModel(model)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .tools(tool)
                .executeToolsConcurrently(Runnable::run)
                .streamingToolDispatchHook(droppingHook)
                .build();

        CompletableFuture<ChatResponse> future = new CompletableFuture<>();
        assistant
                .chat("go")
                .onPartialResponse(ignored -> {})
                .onCompleteResponse(future::complete)
                .onError(future::completeExceptionally)
                .start();

        try {
            future.get(1, TimeUnit.SECONDS);
            throw new AssertionError(
                    "Final response future completed even though hook dropped follow-up work: " + future.get());
        } catch (TimeoutException expected) {
            // Expected: no follow-up response when batch result gathering is dropped.
        }

        assertThat(hookInvocations.get())
                .as("hook should have been invoked for the tool-call response")
                .isEqualTo(1);
        assertThat(tool.calls.get())
                .as("per-tool executor work is scheduled eagerly before the hook runs")
                .isEqualTo(1);
        assertThat(future).as("no final response should have been delivered").isNotDone();
    }

    private static ToolExecutionRequest toolCall(String id, String arg) {
        return ToolExecutionRequest.builder()
                .id(id)
                .name("doWork")
                .arguments("{\"arg0\": \"" + arg + "\"}")
                .build();
    }
}
