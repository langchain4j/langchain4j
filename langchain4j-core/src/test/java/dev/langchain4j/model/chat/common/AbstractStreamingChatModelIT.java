package dev.langchain4j.model.chat.common;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Contains all the common tests that every {@link StreamingChatModel} must successfully pass.
 * This ensures that {@link StreamingChatModel} implementations are interchangeable among themselves.
 */
@TestInstance(PER_CLASS)
public abstract class AbstractStreamingChatModelIT extends AbstractBaseChatModelIT<StreamingChatModel> {

    public abstract StreamingChatModel createModelWith(ChatModelListener listener);

    @ParameterizedTest
    @MethodSource("models")
    @EnabledIf("supportsStreamingCancellation")
    void should_cancel_streaming(StreamingChatModel model) {

        // given
        int partialResponsesBeforeCancellation = 5;
        AtomicInteger partialResponsesCounter = new AtomicInteger();
        AtomicReference<StreamingHandle> streamingHandleReference = new AtomicReference<>();
        Consumer<StreamingHandle> streamingHandleConsumer = streamingHandle -> {
            assertThat(streamingHandle.isCancelled()).isFalse();
            streamingHandleReference.set(streamingHandle);

            assertThat(partialResponsesCounter).hasValueLessThan(partialResponsesBeforeCancellation);
            if (partialResponsesCounter.incrementAndGet() == partialResponsesBeforeCancellation) {
                streamingHandle.cancel();
            }
        };

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("Tell me a long story about animals"))
                .build();

        // when
        ChatResponseAndStreamingMetadata responseAndStreamingMetadata =
                chat(model, chatRequest, streamingHandleConsumer, 30, false);

        // then
        assertThat(responseAndStreamingMetadata.chatResponse()).isNull();

        StreamingMetadata streamingMetadata = responseAndStreamingMetadata.streamingMetadata();
        assertThat(streamingMetadata.timesOnPartialResponseWasCalled()).isEqualTo(partialResponsesBeforeCancellation);
        assertThat(streamingMetadata.timesOnCompleteResponseWasCalled()).isEqualTo(0);

        StreamingHandle streamingHandle = streamingHandleReference.get();
        assertThat(streamingHandle.isCancelled()).isTrue();
        streamingHandle.cancel(); // testing idempotency
        assertThat(streamingHandle.isCancelled()).isTrue();
    }

    protected boolean supportsStreamingCancellation() {
        return true;
    }

    @Test
    void should_propagate_user_exceptions_thrown_from_onPartialResponse() throws Exception {

        // given
        AtomicInteger onPartialResponseCalled = new AtomicInteger(0);
        CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();
        List<Throwable> errors = new ArrayList<>();
        CompletableFuture<Void> futureErrors = new CompletableFuture<>();

        RuntimeException userCodeException = new RuntimeException("something wrong happened in user code");

        StreamingChatResponseHandler handler = new StreamingChatResponseHandler() {

            @Override
            public void onPartialResponse(String partialResponse) {
                onPartialResponseCalled.incrementAndGet();
                throw userCodeException;
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                futureResponse.complete(completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                errors.add(error);
                futureErrors.complete(null);
            }
        };

        ChatModelListener listener = mock(ChatModelListener.class);
        StreamingChatModel model = createModelWith(listener);
        if (model == null) {
            return;
        }

        // when
        model.chat("What is the capital of Germany?", handler);

        // then
        ChatResponse response = futureResponse.get(30, SECONDS);
        assertThat(response.aiMessage().text()).containsIgnoringCase("Berlin");

        assertThat(onPartialResponseCalled.get()).isGreaterThan(1);

        futureErrors.get(30, SECONDS);
        assertThat(errors).hasSize(onPartialResponseCalled.get());
        for (Throwable error : errors) {
            assertThat(error).isEqualTo(userCodeException);
        }

        verify(listener).onRequest(any());
        verify(listener, times(onPartialResponseCalled.get())).onError(any());
        verify(listener).onResponse(any());
        verifyNoMoreInteractions(listener);
    }

    @Test
    void should_propagate_user_exceptions_thrown_from_onCompleteResponse() throws Exception {

        // given
        CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();
        List<Throwable> errors = new ArrayList<>();
        CompletableFuture<Void> futureErrors = new CompletableFuture<>();

        RuntimeException userCodeException = new RuntimeException("something wrong happened in user code");

        StreamingChatResponseHandler handler = new StreamingChatResponseHandler() {

            @Override
            public void onPartialResponse(String partialResponse) {}

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                futureResponse.complete(completeResponse);
                throw userCodeException;
            }

            @Override
            public void onError(Throwable error) {
                errors.add(error);
                futureErrors.complete(null);
            }
        };

        ChatModelListener listener = mock(ChatModelListener.class);
        StreamingChatModel model = createModelWith(listener);
        if (model == null) {
            return;
        }

        // when
        model.chat("What is the capital of Germany?", handler);

        // then
        ChatResponse response = futureResponse.get(30, SECONDS);
        assertThat(response.aiMessage().text()).containsIgnoringCase("Berlin");

        futureErrors.get(30, SECONDS);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0)).isEqualTo(userCodeException);

        verify(listener).onRequest(any());
        verify(listener).onError(any());
        verify(listener).onResponse(any());
        verifyNoMoreInteractions(listener);
    }

    @Test
    void should_ignore_user_exceptions_thrown_from_onError() throws Exception {

        // given
        CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();
        List<Throwable> errors = new ArrayList<>();
        CompletableFuture<Void> futureErrors = new CompletableFuture<>();

        RuntimeException userCodeException = new RuntimeException("something wrong happened in user code");

        StreamingChatResponseHandler handler = new StreamingChatResponseHandler() {

            @Override
            public void onPartialResponse(String partialResponse) {}

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                futureResponse.complete(completeResponse);
                throw userCodeException; // to make sure onError will be called
            }

            @Override
            public void onError(Throwable error) {
                errors.add(error);
                futureErrors.complete(null);
                throw new RuntimeException("something unexpected happened, but it should be ignored");
            }
        };

        ChatModelListener listener = mock(ChatModelListener.class);
        StreamingChatModel model = createModelWith(listener);
        if (model == null) {
            return;
        }

        // when
        model.chat("What is the capital of Germany?", handler);

        // then
        ChatResponse response = futureResponse.get(30, SECONDS);
        assertThat(response.aiMessage().text()).containsIgnoringCase("Berlin");

        futureErrors.get(30, SECONDS);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0)).isEqualTo(userCodeException);

        verify(listener).onRequest(any());
        verify(listener).onError(any());
        verify(listener).onResponse(any());
        verifyNoMoreInteractions(listener);
    }

    @Override
    protected ChatResponseAndStreamingMetadata chat(StreamingChatModel chatModel, ChatRequest chatRequest) {
        return chat(chatModel, chatRequest, ignored -> {}, 120, true);
    }

    public static ChatResponseAndStreamingMetadata chat(StreamingChatModel chatModel,
                                                        ChatRequest chatRequest,
                                                        Consumer<StreamingHandle> streamingHandleConsumer,
                                                        int timeoutSeconds,
                                                        boolean failOnTimeout) {

        CompletableFuture<ChatResponse> futureChatResponse = new CompletableFuture<>();
        StringBuffer concatenatedPartialResponsesBuilder = new StringBuffer();
        Queue<PartialToolCall> partialToolCalls = new ConcurrentLinkedQueue<>();
        Queue<CompleteToolCall> completeToolCalls = new ConcurrentLinkedQueue<>();
        AtomicInteger timesOnPartialResponseWasCalled = new AtomicInteger();
        AtomicInteger timesOnPartialThinkingWasCalled = new AtomicInteger();
        AtomicInteger timesOnCompleteResponseWasCalled = new AtomicInteger();
        Set<Thread> threads = new CopyOnWriteArraySet<>();

        StreamingChatResponseHandler handler = new StreamingChatResponseHandler() {

            @Override
            public void onPartialResponse(String partialResponse) {
                concatenatedPartialResponsesBuilder.append(partialResponse);
                timesOnPartialResponseWasCalled.incrementAndGet();
                threads.add(Thread.currentThread());
            }

            @Override
            public void onPartialResponse(PartialResponse partialResponse, PartialResponseContext context) {
                streamingHandleConsumer.accept(context.streamingHandle());
                concatenatedPartialResponsesBuilder.append(partialResponse.text());
                timesOnPartialResponseWasCalled.incrementAndGet();
                threads.add(Thread.currentThread());
            }

            @Override
            public void onPartialThinking(PartialThinking partialThinking) {
                timesOnPartialThinkingWasCalled.incrementAndGet();
                threads.add(Thread.currentThread());
            }

            @Override
            public void onPartialThinking(PartialThinking partialThinking, PartialThinkingContext context) {
                timesOnPartialThinkingWasCalled.incrementAndGet();
                threads.add(Thread.currentThread());
            }

            @Override
            public void onPartialToolCall(PartialToolCall partialToolCall) {
                partialToolCalls.add(partialToolCall);
                threads.add(Thread.currentThread());
            }

            @Override
            public void onPartialToolCall(PartialToolCall partialToolCall, PartialToolCallContext context) {
                partialToolCalls.add(partialToolCall);
                threads.add(Thread.currentThread());
            }

            @Override
            public void onCompleteToolCall(CompleteToolCall completeToolCall) {
                completeToolCalls.add(completeToolCall);
                threads.add(Thread.currentThread());
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                timesOnCompleteResponseWasCalled.incrementAndGet();
                threads.add(Thread.currentThread());
                futureChatResponse.complete(completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                threads.add(Thread.currentThread());
                futureChatResponse.completeExceptionally(error);
            }
        };

        StreamingChatResponseHandler spyHandler = spy(handler);
        chatModel.chat(chatRequest, spyHandler);

        ChatResponse chatResponse = null;
        try {
            chatResponse = futureChatResponse.get(timeoutSeconds, SECONDS);
        } catch (TimeoutException e) {
            if (failOnTimeout) {
                throw new RuntimeException(e);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        String concatenatedPartialResponses = concatenatedPartialResponsesBuilder.toString();
        StreamingMetadata metadata = new StreamingMetadata(
                concatenatedPartialResponses.isEmpty() ? null : concatenatedPartialResponses,
                timesOnPartialResponseWasCalled.get(),
                timesOnPartialThinkingWasCalled.get(),
                new ArrayList<>(partialToolCalls),
                new ArrayList<>(completeToolCalls),
                timesOnCompleteResponseWasCalled.get(),
                threads,
                spyHandler);
        return new ChatResponseAndStreamingMetadata(chatResponse, metadata);
    }
}
