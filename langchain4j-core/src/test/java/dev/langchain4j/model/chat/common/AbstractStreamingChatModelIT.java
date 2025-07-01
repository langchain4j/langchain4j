package dev.langchain4j.model.chat.common;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Contains all the common tests that every {@link StreamingChatModel} must successfully pass.
 * This ensures that {@link StreamingChatModel} implementations are interchangeable among themselves.
 */
@TestInstance(PER_CLASS)
public abstract class AbstractStreamingChatModelIT extends AbstractBaseChatModelIT<StreamingChatModel> {

    public abstract StreamingChatModel createModelWith(ChatModelListener listener);

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
            public void onPartialResponse(String partialResponse) {
            }

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
            public void onPartialResponse(String partialResponse) {
            }

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

        CompletableFuture<ChatResponse> futureChatResponse = new CompletableFuture<>();
        StringBuffer concatenatedPartialResponsesBuilder = new StringBuffer();
        AtomicInteger timesOnPartialResponseWasCalled = new AtomicInteger();
        AtomicInteger timesOnCompleteResponseWasCalled = new AtomicInteger();
        Set<Thread> threads = new CopyOnWriteArraySet<>();

        chatModel.chat(chatRequest, new StreamingChatResponseHandler() {

            @Override
            public void onPartialResponse(String partialResponse) {
                concatenatedPartialResponsesBuilder.append(partialResponse);
                timesOnPartialResponseWasCalled.incrementAndGet();
                threads.add(Thread.currentThread());
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                futureChatResponse.complete(completeResponse);
                timesOnCompleteResponseWasCalled.incrementAndGet();
                threads.add(Thread.currentThread());
            }

            @Override
            public void onError(Throwable error) {
                futureChatResponse.completeExceptionally(error);
                threads.add(Thread.currentThread());
            }
        });

        try {
            ChatResponse chatResponse = futureChatResponse.get(120, SECONDS);
            String concatenatedPartialResponses = concatenatedPartialResponsesBuilder.toString();
            StreamingMetadata metadata = new StreamingMetadata(
                    concatenatedPartialResponses.isEmpty() ? null : concatenatedPartialResponses,
                    timesOnPartialResponseWasCalled.get(),
                    timesOnCompleteResponseWasCalled.get(),
                    threads
            );
            return new ChatResponseAndStreamingMetadata(chatResponse, metadata);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
