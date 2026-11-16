package dev.langchain4j.model.chat;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.exception.InternalServerException;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.internal.CurrentInvocation;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.CompleteToolCall;
import dev.langchain4j.model.chat.response.PartialResponse;
import dev.langchain4j.model.chat.response.PartialResponseContext;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.PartialThinkingContext;
import dev.langchain4j.model.chat.response.PartialToolCall;
import dev.langchain4j.model.chat.response.PartialToolCallContext;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.chat.response.StreamingHandle;
import dev.langchain4j.spi.ExecutorProvider;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class StreamingChatModelRetryTest {

    private static final ChatRequest REQUEST =
            ChatRequest.builder().messages(UserMessage.from("hi")).build();

    private static final ChatResponse RESPONSE =
            ChatResponse.builder().aiMessage(AiMessage.from("hello")).build();

    @Test
    void should_not_retry_by_default() throws Exception {

        // given
        ScriptedStreamingChatModel model =
                new ScriptedStreamingChatModel(null, failWith(new InternalServerException("boom")), succeed());
        RecordingHandler handler = new RecordingHandler();

        // when
        model.chat(REQUEST, handler);

        // then
        assertThat(handler.error.get(5, SECONDS)).isInstanceOf(InternalServerException.class);
        assertThat(model.attempts).hasValue(1);
        assertThat(model.listener.requests).hasValue(1);
        assertThat(model.listener.errors).hasValue(1);
    }

    @Test
    void should_retry_failures_before_first_visible_callback() throws Exception {

        // given
        ScriptedStreamingChatModel model = new ScriptedStreamingChatModel(
                2, failWith(new InternalServerException("first")), failWith(new RuntimeException("second")), succeed());
        RecordingHandler handler = new RecordingHandler();

        // when
        model.chat(REQUEST, handler);

        // then
        assertThat(handler.response.get(10, SECONDS)).isEqualTo(RESPONSE);
        assertThat(model.attempts).hasValue(3);
        assertThat(handler.events).containsExactly("partial:hel", "partial:lo", "complete");
        assertThat(model.listener.requests).hasValue(1);
        assertThat(model.listener.responses).hasValue(1);
        assertThat(model.listener.errors).hasValue(0);
    }

    @Test
    void should_ignore_callbacks_of_failed_attempt_after_retry_was_scheduled() throws Exception {

        // given: like an SSE "error" event followed by the end of the stream
        ScriptedStreamingChatModel model = new ScriptedStreamingChatModel(
                2,
                handler -> {
                    handler.onError(new RuntimeException("error event"));
                    handler.onError(new InternalServerException("second error of the same attempt"));
                    handler.onCompleteResponse(
                            ChatResponse.builder().aiMessage(AiMessage.from("")).build());
                },
                succeed(),
                succeed());
        RecordingHandler handler = new RecordingHandler();

        // when
        model.chat(REQUEST, handler);

        // then
        assertThat(handler.response.get(10, SECONDS)).isEqualTo(RESPONSE);
        assertThat(model.attempts).hasValue(2);
        assertThat(handler.events).containsExactly("partial:hel", "partial:lo", "complete");
        assertThat(model.listener.responses).hasValue(1);
    }

    @Test
    void should_report_last_error_once_when_retries_are_exhausted() throws Exception {

        // given
        InternalServerException lastError = new InternalServerException("last");
        ScriptedStreamingChatModel model = new ScriptedStreamingChatModel(
                1, failWith(new InternalServerException("first")), failWith(lastError), succeed());
        RecordingHandler handler = new RecordingHandler();

        // when
        model.chat(REQUEST, handler);

        // then
        assertThat(handler.error.get(10, SECONDS)).isSameAs(lastError);
        assertThat(model.attempts).hasValue(2);
        assertThat(handler.events).containsExactly("error");
        assertThat(model.listener.requests).hasValue(1);
        assertThat(model.listener.errors).hasValue(1);
    }

    @Test
    void should_not_retry_after_partial_response() throws Exception {

        // given
        InternalServerException error = new InternalServerException("mid-stream");
        ScriptedStreamingChatModel model = new ScriptedStreamingChatModel(
                2,
                handler -> {
                    handler.onPartialResponse("hel");
                    handler.onError(error);
                },
                succeed());
        RecordingHandler handler = new RecordingHandler();

        // when
        model.chat(REQUEST, handler);

        // then
        assertThat(handler.error.get(5, SECONDS)).isSameAs(error);
        assertThat(model.attempts).hasValue(1);
        assertThat(handler.events).containsExactly("partial:hel", "error");
        assertThat(model.listener.errors).hasValue(1);
    }

    @Test
    void should_not_retry_after_complete_tool_call() throws Exception {

        // given
        ScriptedStreamingChatModel model = new ScriptedStreamingChatModel(
                2,
                handler -> {
                    handler.onCompleteToolCall(new CompleteToolCall(
                            0,
                            ToolExecutionRequest.builder()
                                    .id("1")
                                    .name("tool")
                                    .arguments("{}")
                                    .build()));
                    handler.onError(new InternalServerException("after tool call"));
                },
                succeed());
        RecordingHandler handler = new RecordingHandler();

        // when
        model.chat(REQUEST, handler);

        // then
        assertThat(handler.error.get(5, SECONDS)).isInstanceOf(InternalServerException.class);
        assertThat(model.attempts).hasValue(1);
    }

    @Test
    void should_not_retry_after_partial_thinking() throws Exception {

        // given
        ScriptedStreamingChatModel model = new ScriptedStreamingChatModel(
                2,
                handler -> {
                    handler.onPartialThinking(new PartialThinking("hmm"));
                    handler.onError(new InternalServerException("after thinking"));
                },
                succeed());
        RecordingHandler handler = new RecordingHandler();

        // when
        model.chat(REQUEST, handler);

        // then
        assertThat(handler.error.get(5, SECONDS)).isInstanceOf(InternalServerException.class);
        assertThat(model.attempts).hasValue(1);
    }

    @Test
    void should_not_retry_when_complete_response_callback_throws() throws Exception {

        // given
        RuntimeException userError = new RuntimeException("user handler failed");
        ScriptedStreamingChatModel model = new ScriptedStreamingChatModel(
                1,
                handler -> {
                    // providers catch exceptions thrown by handler callbacks and route them to onError
                    try {
                        handler.onCompleteResponse(RESPONSE);
                    } catch (RuntimeException e) {
                        handler.onError(e);
                    }
                },
                succeed());
        RecordingHandler handler = new RecordingHandler() {
            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                super.onCompleteResponse(completeResponse);
                throw userError;
            }
        };

        // when
        model.chat(REQUEST, handler);

        // then
        assertThat(handler.error.get(5, SECONDS)).isSameAs(userError);
        assertThat(model.attempts).hasValue(1);
        assertThat(handler.events).containsExactly("complete", "error");
    }

    @Test
    void should_not_retry_non_retriable_exception() throws Exception {

        // given
        ScriptedStreamingChatModel model =
                new ScriptedStreamingChatModel(2, failWith(new AuthenticationException("bad key")), succeed());
        RecordingHandler handler = new RecordingHandler();

        // when
        model.chat(REQUEST, handler);

        // then
        assertThat(handler.error.get(5, SECONDS)).isInstanceOf(AuthenticationException.class);
        assertThat(model.attempts).hasValue(1);
        assertThat(model.listener.errors).hasValue(1);
    }

    @Test
    void should_retry_after_unmapped_raw_event() throws Exception {

        // given
        ScriptedStreamingChatModel model = new ScriptedStreamingChatModel(
                1,
                handler -> {
                    handler.onUnmappedRawEvent("message_start");
                    handler.onError(new InternalServerException("connection reset"));
                },
                succeed());
        RecordingHandler handler = new RecordingHandler();

        // when
        model.chat(REQUEST, handler);

        // then
        assertThat(handler.response.get(10, SECONDS)).isEqualTo(RESPONSE);
        assertThat(model.attempts).hasValue(2);
        assertThat(handler.events).containsExactly("raw:message_start", "partial:hel", "partial:lo", "complete");
    }

    @Test
    void should_not_retry_when_user_callback_throws() throws Exception {

        // given
        RuntimeException userError = new RuntimeException("user handler failed");
        ScriptedStreamingChatModel model = new ScriptedStreamingChatModel(
                1,
                handler -> {
                    // providers catch exceptions thrown by handler callbacks and route them to onError
                    try {
                        handler.onUnmappedRawEvent("message_start");
                    } catch (RuntimeException e) {
                        handler.onError(e);
                    }
                },
                succeed());
        RecordingHandler handler = new RecordingHandler() {
            @Override
            public void onUnmappedRawEvent(Object rawEvent) {
                throw userError;
            }
        };

        // when
        model.chat(REQUEST, handler);

        // then
        assertThat(handler.error.get(5, SECONDS)).isSameAs(userError);
        assertThat(model.attempts).hasValue(1);
    }

    @Test
    void should_propagate_exception_thrown_synchronously_by_first_attempt() {

        // given
        ScriptedStreamingChatModel model = new ScriptedStreamingChatModel(
                2,
                handler -> {
                    throw new UnsupportedFeatureException("topK is not supported");
                },
                succeed());

        // when-then
        assertThatThrownBy(() -> model.chat(REQUEST, new RecordingHandler()))
                .isInstanceOf(UnsupportedFeatureException.class);
        assertThat(model.attempts).hasValue(1);
    }

    @Test
    void should_report_exception_thrown_synchronously_by_retry_attempt() throws Exception {

        // given
        UnsupportedFeatureException error = new UnsupportedFeatureException("thrown on retry");
        ScriptedStreamingChatModel model =
                new ScriptedStreamingChatModel(2, failWith(new InternalServerException("first")), handler -> {
                    throw error;
                });
        RecordingHandler handler = new RecordingHandler();

        // when
        model.chat(REQUEST, handler);

        // then
        assertThat(handler.error.get(10, SECONDS)).isSameAs(error);
        assertThat(model.attempts).hasValue(2);
        assertThat(model.listener.errors).hasValue(1);
    }

    @Test
    void should_retry_retriable_exception_thrown_synchronously_by_retry_attempt() throws Exception {

        // given
        ScriptedStreamingChatModel model = new ScriptedStreamingChatModel(
                2,
                failWith(new InternalServerException("first")),
                handler -> {
                    throw new InternalServerException("thrown on retry");
                },
                succeed());
        RecordingHandler handler = new RecordingHandler();

        // when
        model.chat(REQUEST, handler);

        // then
        assertThat(handler.response.get(10, SECONDS)).isEqualTo(RESPONSE);
        assertThat(model.attempts).hasValue(3);
    }

    @Test
    void should_report_error_thrown_synchronously_by_retry_attempt() throws Exception {

        // given
        AssertionError error = new AssertionError("not an exception");
        ScriptedStreamingChatModel model =
                new ScriptedStreamingChatModel(2, failWith(new InternalServerException("first")), handler -> {
                    throw error;
                });
        RecordingHandler handler = new RecordingHandler();

        // when
        model.chat(REQUEST, handler);

        // then
        assertThat(handler.error.get(10, SECONDS)).isSameAs(error);
        assertThat(model.attempts).hasValue(2);
    }

    @Test
    void should_report_original_error_when_retry_cannot_be_submitted() throws Exception {

        // given
        ExecutorProvider previousProvider = ExecutorProvider.get();
        ExecutorProvider.set(() -> task -> {
            throw new RejectedExecutionException("executor is shut down");
        });
        try {
            InternalServerException error = new InternalServerException("first");
            ScriptedStreamingChatModel model = new ScriptedStreamingChatModel(2, failWith(error), succeed());
            RecordingHandler handler = new RecordingHandler();

            // when
            model.chat(REQUEST, handler);

            // then
            assertThat(handler.error.get(10, SECONDS)).isSameAs(error);
            assertThat(error.getSuppressed()).singleElement().isInstanceOf(RejectedExecutionException.class);
            assertThat(model.attempts).hasValue(1);
            assertThat(model.listener.errors).hasValue(1);
        } finally {
            ExecutorProvider.set(previousProvider);
        }
    }

    static Stream<Consumer<StreamingChatResponseHandler>> callbacksWithContext() {
        return Stream.of(
                handler -> handler.onPartialResponse(
                        new PartialResponse("hel"), new PartialResponseContext(new NoOpStreamingHandle())),
                handler -> handler.onPartialThinking(
                        new PartialThinking("hmm"), new PartialThinkingContext(new NoOpStreamingHandle())),
                handler -> handler.onPartialToolCall(
                        PartialToolCall.builder()
                                .index(0)
                                .id("1")
                                .name("tool")
                                .partialArguments("{")
                                .build(),
                        new PartialToolCallContext(new NoOpStreamingHandle())));
    }

    @ParameterizedTest
    @MethodSource("callbacksWithContext")
    void should_not_retry_after_callback_with_context(Consumer<StreamingChatResponseHandler> callback)
            throws Exception {

        // given
        ScriptedStreamingChatModel model = new ScriptedStreamingChatModel(
                2,
                handler -> {
                    callback.accept(handler);
                    handler.onError(new InternalServerException("after callback"));
                },
                succeed());
        RecordingHandler handler = new RecordingHandler();

        // when
        model.chat(REQUEST, handler);

        // then
        assertThat(handler.error.get(5, SECONDS)).isInstanceOf(InternalServerException.class);
        assertThat(model.attempts).hasValue(1);
    }

    @Test
    void should_run_retry_with_invocation_id_of_the_call() throws Exception {

        // given
        UUID invocationId = UUID.randomUUID();
        CompletableFuture<UUID> invocationIdOnRetry = new CompletableFuture<>();
        ScriptedStreamingChatModel model =
                new ScriptedStreamingChatModel(1, failWith(new InternalServerException("first")), handler -> {
                    invocationIdOnRetry.complete(CurrentInvocation.invocationId());
                    succeed().accept(handler);
                });
        RecordingHandler handler = new RecordingHandler();

        // when
        model.chat(
                REQUEST, ChatRequestOptions.builder().invocationId(invocationId).build(), handler);

        // then
        assertThat(handler.response.get(10, SECONDS)).isEqualTo(RESPONSE);
        assertThat(invocationIdOnRetry.get(10, SECONDS)).isEqualTo(invocationId);
    }

    @Test
    void should_report_error_once_when_last_attempt_throws_after_on_error() throws Exception {

        // given: retries run synchronously, so the test can wait until the retry attempt has fully finished
        CountDownLatch retryFinished = new CountDownLatch(1);
        ExecutorProvider previousProvider = ExecutorProvider.get();
        ExecutorProvider.set(() -> task -> {
            task.run();
            retryFinished.countDown();
        });
        try {
            InternalServerException error = new InternalServerException("reported");
            ScriptedStreamingChatModel model =
                    new ScriptedStreamingChatModel(1, failWith(new InternalServerException("first")), handler -> {
                        handler.onError(error);
                        throw new IllegalStateException("thrown after onError");
                    });
            RecordingHandler handler = new RecordingHandler();

            // when
            model.chat(REQUEST, handler);

            // then
            assertThat(retryFinished.await(10, SECONDS)).isTrue();
            assertThat(handler.error.get()).isSameAs(error);
            assertThat(handler.events).containsExactly("error");
            assertThat(model.listener.errors).hasValue(1);
        } finally {
            ExecutorProvider.set(previousProvider);
        }
    }

    @Test
    void should_not_retry_when_max_retries_is_negative() throws Exception {

        // given
        ScriptedStreamingChatModel model =
                new ScriptedStreamingChatModel(-1, failWith(new InternalServerException("boom")), succeed());
        RecordingHandler handler = new RecordingHandler();

        // when
        model.chat(REQUEST, handler);

        // then
        assertThat(handler.error.get(5, SECONDS)).isInstanceOf(InternalServerException.class);
        assertThat(model.attempts).hasValue(1);
    }

    @Test
    void should_not_retry_after_error_was_reported() {

        // given: like an SSE "error" event followed by a failure of the connection
        AuthenticationException reported = new AuthenticationException("bad key");
        ScriptedStreamingChatModel model = new ScriptedStreamingChatModel(
                2,
                handler -> {
                    handler.onError(reported);
                    handler.onError(new InternalServerException("connection closed"));
                },
                succeed());
        RecordingHandler handler = new RecordingHandler();

        // when
        model.chat(REQUEST, handler);

        // then: both errors are passed on, as without retries, and no retry is scheduled
        assertThat(handler.error).isCompletedWithValue(reported);
        assertThat(handler.events).containsExactly("error", "error");
        assertThat(model.attempts).hasValue(1);
    }

    @Test
    void should_not_propagate_exception_thrown_by_first_attempt_after_retry_was_scheduled() throws Exception {

        // given
        ScriptedStreamingChatModel model = new ScriptedStreamingChatModel(
                2,
                handler -> {
                    handler.onError(new InternalServerException("retriable"));
                    throw new IllegalStateException("thrown after onError");
                },
                succeed());
        RecordingHandler handler = new RecordingHandler();

        // when
        model.chat(REQUEST, handler);

        // then: the caller does not get an exception for a request that is still being retried
        assertThat(handler.response.get(10, SECONDS)).isEqualTo(RESPONSE);
        assertThat(model.attempts).hasValue(2);
        assertThat(handler.events).containsExactly("partial:hel", "partial:lo", "complete");
    }

    private static Consumer<StreamingChatResponseHandler> failWith(Throwable error) {
        return handler -> handler.onError(error);
    }

    private static Consumer<StreamingChatResponseHandler> succeed() {
        return handler -> {
            handler.onPartialResponse("hel");
            handler.onPartialResponse("lo");
            handler.onCompleteResponse(RESPONSE);
        };
    }

    private static class NoOpStreamingHandle implements StreamingHandle {

        @Override
        public void cancel() {}

        @Override
        public boolean isCancelled() {
            return false;
        }
    }

    /**
     * Runs one scripted behavior per attempt, on the thread that calls {@code doChat}.
     */
    private static class ScriptedStreamingChatModel implements StreamingChatModel {

        final AtomicInteger attempts = new AtomicInteger();
        final CountingListener listener = new CountingListener();
        private final Integer maxRetries;
        private final Queue<Consumer<StreamingChatResponseHandler>> attemptScripts;

        @SafeVarargs
        ScriptedStreamingChatModel(Integer maxRetries, Consumer<StreamingChatResponseHandler>... attemptScripts) {
            this.maxRetries = maxRetries;
            this.attemptScripts = new ArrayDeque<>(List.of(attemptScripts));
        }

        @Override
        public int maxRetries() {
            return maxRetries == null ? StreamingChatModel.super.maxRetries() : maxRetries;
        }

        @Override
        public List<ChatModelListener> listeners() {
            return List.of(listener);
        }

        @Override
        public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
            attempts.incrementAndGet();
            Consumer<StreamingChatResponseHandler> script;
            synchronized (attemptScripts) {
                script = attemptScripts.remove();
            }
            script.accept(handler);
        }
    }

    private static class CountingListener implements ChatModelListener {

        final AtomicInteger requests = new AtomicInteger();
        final AtomicInteger responses = new AtomicInteger();
        final AtomicInteger errors = new AtomicInteger();

        @Override
        public void onRequest(ChatModelRequestContext requestContext) {
            requests.incrementAndGet();
        }

        @Override
        public void onResponse(ChatModelResponseContext responseContext) {
            responses.incrementAndGet();
        }

        @Override
        public void onError(ChatModelErrorContext errorContext) {
            errors.incrementAndGet();
        }
    }

    private static class RecordingHandler implements StreamingChatResponseHandler {

        final List<String> events = new CopyOnWriteArrayList<>();
        final CompletableFuture<ChatResponse> response = new CompletableFuture<>();
        final CompletableFuture<Throwable> error = new CompletableFuture<>();

        @Override
        public void onPartialResponse(String partialResponse) {
            events.add("partial:" + partialResponse);
        }

        @Override
        public void onUnmappedRawEvent(Object rawEvent) {
            events.add("raw:" + rawEvent);
        }

        @Override
        public void onCompleteResponse(ChatResponse completeResponse) {
            events.add("complete");
            response.complete(completeResponse);
        }

        @Override
        public void onError(Throwable throwable) {
            events.add("error");
            error.complete(throwable);
        }
    }
}
