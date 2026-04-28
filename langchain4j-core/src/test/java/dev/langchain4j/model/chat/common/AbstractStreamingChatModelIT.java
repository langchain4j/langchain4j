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
import java.util.concurrent.Flow;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.CompleteToolCall;
import dev.langchain4j.model.chat.response.PartialResponse;
import dev.langchain4j.model.chat.response.PartialResponseContext;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.PartialThinkingContext;
import dev.langchain4j.model.chat.response.PartialToolCall;
import dev.langchain4j.model.chat.response.PartialToolCallContext;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.chat.response.StreamingEvent;
import dev.langchain4j.model.chat.response.StreamingHandle;
import org.junit.jupiter.api.Assumptions;
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

    protected List<StreamingMode> streamingModes() {
        return List.of(StreamingMode.HANDLER);
    }

    @Override
    protected List<StreamingChatModel> models() {
        List<StreamingChatModel> baseModels = baseModels();
        if (baseModels.isEmpty()) {
            throw new IllegalStateException(
                    "Either override models() (legacy single-mode) "
                            + "or baseModels() + streamingModes() (multi-mode)");
        }
        return applyModes(baseModels);
    }

    protected List<StreamingChatModel> baseModels() {
        return List.of();
    }

    @Override
    protected List<StreamingChatModel> modelsSupportingTools() {
        List<StreamingChatModel> baseModels = baseModelsSupportingTools();
        return baseModels.isEmpty() ? super.modelsSupportingTools() : applyModes(baseModels);
    }

    protected List<StreamingChatModel> baseModelsSupportingTools() {
        return baseModels();
    }

    @Override
    protected List<StreamingChatModel> modelsSupportingStructuredOutputs() {
        List<StreamingChatModel> baseModels = baseModelsSupportingStructuredOutputs();
        return baseModels.isEmpty() ? super.modelsSupportingStructuredOutputs() : applyModes(baseModels);
    }

    protected List<StreamingChatModel> baseModelsSupportingStructuredOutputs() {
        return baseModels();
    }

    @Override
    protected List<StreamingChatModel> modelsSupportingImageInputs() {
        List<StreamingChatModel> baseModels = baseModelsSupportingImageInputs();
        return baseModels.isEmpty() ? super.modelsSupportingImageInputs() : applyModes(baseModels);
    }

    protected List<StreamingChatModel> baseModelsSupportingImageInputs() {
        return baseModels();
    }

    private List<StreamingChatModel> applyModes(List<StreamingChatModel> bases) {
        List<StreamingMode> modes = streamingModes();
        if (modes.size() == 1 && modes.get(0) == StreamingMode.HANDLER) {
            return bases;
        }
        List<StreamingChatModel> result = new ArrayList<>();
        for (StreamingChatModel base : bases) {
            for (StreamingMode mode : modes) {
                result.add(new ModeAwareModel(base, mode));
            }
        }
        return result;
    }

    @Override
    protected List<StreamingChatModel> createModelsWith(ChatRequestParameters parameters) {
        return applyModes(List.of(createModelWith(parameters)));
    }

    public abstract StreamingChatModel createModelWith(ChatModelListener listener);

    @ParameterizedTest
    @MethodSource("models")
    @EnabledIf("supportsStreamingCancellation")
    void should_cancel_streaming(StreamingChatModel model) {

        // StreamingHandle.cancel() is part of the handler-based API only — there's no equivalent
        // visible in the publisher path (callers use Flow.Subscription.cancel() instead, which
        // a separate test would exercise). Skip publisher-wrapped invocations.
        Assumptions.assumeTrue(
                !(model instanceof ModeAwareModel w) || w.mode() == StreamingMode.HANDLER,
                "StreamingHandle cancellation is handler-mode only");


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

    @Override
    protected boolean assertThreads() {
        return streamingModes().equals(List.of(StreamingMode.HANDLER));
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

        assertThat(onPartialResponseCalled.get()).isGreaterThan(0);

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

    public ChatResponseAndStreamingMetadata chat(StreamingChatModel chatModel,
                                                 ChatRequest chatRequest,
                                                 Consumer<StreamingHandle> streamingHandleConsumer,
                                                 int timeoutSeconds,
                                                 boolean failOnTimeout) {
        if (chatModel instanceof ModeAwareModel wrapped && wrapped.mode() == StreamingMode.PUBLISHER) {
            return chatViaPublisher(wrapped.delegate(), chatRequest, timeoutSeconds, failOnTimeout);
        }
        StreamingChatModel target = chatModel instanceof ModeAwareModel w ? w.delegate() : chatModel;
        return chatViaHandler(target, chatRequest, streamingHandleConsumer, timeoutSeconds, failOnTimeout);
    }

    public static ChatResponseAndStreamingMetadata chatViaHandler(StreamingChatModel chatModel,
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
                spyHandler,
                StreamingMode.HANDLER);
        return new ChatResponseAndStreamingMetadata(chatResponse, metadata);
    }

    public static ChatResponseAndStreamingMetadata chatViaPublisher(StreamingChatModel chatModel,
                                                                    ChatRequest chatRequest,
                                                                    int timeoutSeconds,
                                                                    boolean failOnTimeout) {

        CompletableFuture<ChatResponse> futureChatResponse = new CompletableFuture<>();
        StringBuffer concatenatedPartialResponsesBuilder = new StringBuffer();
        Queue<PartialToolCall> partialToolCalls = new ConcurrentLinkedQueue<>();
        Queue<CompleteToolCall> completeToolCalls = new ConcurrentLinkedQueue<>();
        AtomicInteger timesOnPartialResponseWasCalled = new AtomicInteger();
        AtomicInteger timesOnPartialThinkingWasCalled = new AtomicInteger();
        AtomicInteger timesOnCompleteResponseWasCalled = new AtomicInteger();

        chatModel.chat(chatRequest).subscribe(new Flow.Subscriber<>() {

            private ChatResponse chatResponse;

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(StreamingEvent event) {
                // TODO collect all events and verify them (quantity, oder, content, etc)
                if (event instanceof PartialResponse partial) {
                    concatenatedPartialResponsesBuilder.append(partial.text());
                    timesOnPartialResponseWasCalled.incrementAndGet();
                } else if (event instanceof PartialThinking) {
                    timesOnPartialThinkingWasCalled.incrementAndGet();
                } else if (event instanceof PartialToolCall partial) {
                    partialToolCalls.add(partial);
                } else if (event instanceof CompleteToolCall complete) {
                    completeToolCalls.add(complete);
                } else if (event instanceof ChatResponse response) {
                    chatResponse = response;
                    timesOnCompleteResponseWasCalled.incrementAndGet();
                } else {
                    throw new IllegalStateException("Unknown event type: " + event);
                }
            }

            @Override
            public void onError(Throwable throwable) {
                futureChatResponse.completeExceptionally(throwable);
            }

            @Override
            public void onComplete() {
                futureChatResponse.complete(chatResponse);
            }
        });

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
                null,
                null,
                StreamingMode.PUBLISHER);
        return new ChatResponseAndStreamingMetadata(chatResponse, metadata);
    }

    /**
     * Marker wrapper that pairs a {@link StreamingChatModel} with a {@link StreamingMode}.
     * Used as an element of {@link #models()} so each test runs once per mode without changing
     * test method signatures. Delegates all interface methods to the wrapped model;
     * {@link #chat(StreamingChatModel, ChatRequest, Consumer, int, boolean)} unwraps and
     * dispatches via the appropriate transport (handler vs. publisher).
     */
    public static final class ModeAwareModel implements StreamingChatModel {

        private final StreamingChatModel delegate;
        private final StreamingMode mode;

        public ModeAwareModel(StreamingChatModel delegate, StreamingMode mode) {
            this.delegate = delegate;
            this.mode = mode;
        }

        public StreamingChatModel delegate() {
            return delegate;
        }

        public StreamingMode mode() {
            return mode;
        }

        @Override
        public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
            delegate.doChat(chatRequest, handler);
        }

        @Override
        public java.util.concurrent.Flow.Publisher<StreamingEvent> doChat(ChatRequest chatRequest) {
            return delegate.doChat(chatRequest);
        }

        @Override
        public dev.langchain4j.model.chat.request.ChatRequestParameters defaultRequestParameters() {
            return delegate.defaultRequestParameters();
        }

        @Override
        public List<ChatModelListener> listeners() {
            return delegate.listeners();
        }

        @Override
        public dev.langchain4j.model.ModelProvider provider() {
            return delegate.provider();
        }

        @Override
        public Set<dev.langchain4j.model.chat.Capability> supportedCapabilities() {
            return delegate.supportedCapabilities();
        }

        @Override
        public String toString() {
            return mode + "/" + delegate;
        }
    }
}
