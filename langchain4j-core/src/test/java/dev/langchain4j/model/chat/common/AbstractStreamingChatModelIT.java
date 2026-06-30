package dev.langchain4j.model.chat.common;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.*;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.Flow.Publisher;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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

    private List<StreamingChatModel> applyModes(List<StreamingChatModel> baseModels) {
        List<StreamingMode> streamingModes = streamingModes();
        if (streamingModes.size() == 1 && streamingModes.get(0) == StreamingMode.HANDLER) {
            return baseModels;
        }
        List<StreamingChatModel> result = new ArrayList<>();
        for (StreamingChatModel baseModel : baseModels) {
            for (StreamingMode streamingMode : streamingModes) {
                result.add(new StreamingModeAwareModel(baseModel, streamingMode));
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
        // visible in the publisher path (callers use Flow.Subscription.cancel() instead, which is
        // covered separately by the Reactive Streams TCK and a WireMock mid-stream cancellation test).
        // Skip publisher-wrapped invocations.
        Assumptions.assumeTrue(
                !(model instanceof StreamingModeAwareModel w) || w.mode() == StreamingMode.HANDLER,
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

    @ParameterizedTest
    @MethodSource("models")
    @DisabledIf("supportsStopSequencesParameter")
    @Override
    protected void should_fail_if_stopSequences_parameter_is_not_supported(StreamingChatModel model) {

        // given
        List<String> stopSequences = List.of("World");
        ChatRequestParameters parameters =
                ChatRequestParameters.builder().stopSequences(stopSequences).build();
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("Say 'Hello World'"))
                .parameters(parameters)
                .build();

        // when-then: the same UnsupportedFeatureException is reported, but HOW it reaches the caller
        // depends on the streaming mode — thrown synchronously from the handler API (the handler is never
        // notified), but delivered via Subscriber.onError from the publisher API (a cold publisher does no
        // work at assembly, so chat(request) itself must not throw).
        if (model instanceof StreamingModeAwareModel wrapped && wrapped.mode() == StreamingMode.PUBLISHER) {

            AtomicReference<Publisher<StreamingEvent>> publisherReference = new AtomicReference<>();
            assertThatCode(() -> publisherReference.set(model.chat(chatRequest))).doesNotThrowAnyException();

            AtomicReference<Throwable> error = new AtomicReference<>();
            AtomicBoolean onNextCalled = new AtomicBoolean();
            AtomicBoolean onCompleteCalled = new AtomicBoolean();
            CompletableFuture<Void> terminated = new CompletableFuture<>();

            publisherReference.get().subscribe(new Flow.Subscriber<>() {
                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    subscription.request(Long.MAX_VALUE);
                }

                @Override
                public void onNext(StreamingEvent event) {
                    onNextCalled.set(true);
                }

                @Override
                public void onError(Throwable throwable) {
                    error.set(throwable);
                    terminated.complete(null);
                }

                @Override
                public void onComplete() {
                    onCompleteCalled.set(true);
                    terminated.complete(null);
                }
            });

            try {
                terminated.get(30, SECONDS);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            assertThat(error.get())
                    .isExactlyInstanceOf(UnsupportedFeatureException.class)
                    .hasMessageContaining("stopSequences")
                    .hasMessageContaining("not support");
            assertThat(onNextCalled).isFalse();
            assertThat(onCompleteCalled).isFalse();
        } else {
            StreamingChatResponseHandler handler = mock(StreamingChatResponseHandler.class);
            assertThatThrownBy(() -> model.chat(chatRequest, handler))
                    .isExactlyInstanceOf(UnsupportedFeatureException.class)
                    .hasMessageContaining("stopSequences")
                    .hasMessageContaining("not support");
            verify(handler, never()).onError(any());
            verify(handler, never()).onCompleteResponse(any());
        }

        if (supportsDefaultRequestParameters()) {
            assertThatThrownBy(() -> createModelWith(parameters))
                    .isExactlyInstanceOf(UnsupportedFeatureException.class)
                    .hasMessageContaining("stopSequences")
                    .hasMessageContaining("not support");
        }
    }

    @Override
    protected ChatResponseAndStreamingMetadata chat(StreamingChatModel chatModel, ChatRequest chatRequest) {
        return chat(chatModel, chatRequest, ignored -> {
        }, 120, true);
    }

    public static ChatResponseAndStreamingMetadata chat(StreamingChatModel chatModel,
                                                        ChatRequest chatRequest,
                                                        Consumer<StreamingHandle> streamingHandleConsumer,
                                                        int timeoutSeconds,
                                                        boolean failOnTimeout) {
        if (chatModel instanceof StreamingModeAwareModel wrapped && wrapped.mode() == StreamingMode.PUBLISHER) {
            return chatViaPublisher(wrapped.delegate(), chatRequest, timeoutSeconds, failOnTimeout);
        }
        StreamingChatModel target = chatModel instanceof StreamingModeAwareModel w ? w.delegate() : chatModel;
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
            public void onUnmappedRawEvent(Object rawEvent) {}

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
        } catch (ExecutionException e) {
            // Surface the model's exception as-is (e.g. UnsupportedFeatureException delivered via onError
            // in publisher mode) rather than wrapping it, so callers see the same exception the handler
            // path throws synchronously.
            Throwable cause = e.getCause();
            throw cause instanceof RuntimeException runtimeException
                    ? runtimeException
                    : new RuntimeException(cause != null ? cause : e);
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
        List<StreamingEvent> events = new CopyOnWriteArrayList<>();
        Set<Thread> threads = new CopyOnWriteArraySet<>();
        Thread callerThread = Thread.currentThread();

        chatModel.chat(chatRequest).subscribe(new Flow.Subscriber<>() {

            private ChatResponse chatResponse;

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(StreamingEvent event) {
                events.add(event);
                threads.add(Thread.currentThread());
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
                }
                // Other event types (e.g. RawStreamingEvent, or types introduced later) are intentionally
                // ignored here: subscribers must tolerate unrecognized StreamingEvent subtypes.
            }

            @Override
            public void onError(Throwable throwable) {
                threads.add(Thread.currentThread());
                futureChatResponse.completeExceptionally(throwable);
            }

            @Override
            public void onComplete() {
                threads.add(Thread.currentThread());
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
        } catch (ExecutionException e) {
            // Surface the model's exception as-is (e.g. UnsupportedFeatureException delivered via onError
            // in publisher mode) rather than wrapping it, so callers see the same exception the handler
            // path throws synchronously.
            Throwable cause = e.getCause();
            throw cause instanceof RuntimeException runtimeException
                    ? runtimeException
                    : new RuntimeException(cause != null ? cause : e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        if (chatResponse != null) {
            verifyPublisherEvents(events, threads, callerThread);
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
                null,
                StreamingMode.PUBLISHER);
        return new ChatResponseAndStreamingMetadata(chatResponse, metadata);
    }

    /**
     * Verifies the invariants of a successfully completed publisher stream:
     * <ul>
     *     <li>events are delivered asynchronously, off the subscribing (caller) thread;</li>
     *     <li>the terminal {@link ChatResponse} is emitted exactly once and is the very last event;</li>
     *     <li>for every tool call, all of its {@link PartialToolCall}s precede its {@link CompleteToolCall}.</li>
     * </ul>
     */
    private static void verifyPublisherEvents(List<StreamingEvent> events, Set<Thread> deliveryThreads, Thread callerThread) {
        assertThat(deliveryThreads)
                .as("publisher must deliver events asynchronously, not on the subscribing thread")
                .isNotEmpty()
                .doesNotContain(callerThread);

        long completeResponses = events.stream().filter(event -> event instanceof ChatResponse).count();
        assertThat(completeResponses).as("exactly one terminal ChatResponse event").isEqualTo(1);
        assertThat(events.get(events.size() - 1))
                .as("ChatResponse must be the last event")
                .isInstanceOf(ChatResponse.class);

        for (int i = 0; i < events.size(); i++) {
            if (events.get(i) instanceof CompleteToolCall complete) {
                for (int j = i + 1; j < events.size(); j++) {
                    assertThat(events.get(j) instanceof PartialToolCall partial && partial.index() == complete.index())
                            .as("PartialToolCall for tool index %s arrived after its CompleteToolCall", complete.index())
                            .isFalse();
                }
            }
        }
    }

    public static final class StreamingModeAwareModel implements StreamingChatModel {

        private final StreamingChatModel delegate;
        private final StreamingMode mode;

        public StreamingModeAwareModel(StreamingChatModel delegate, StreamingMode streamingMode) {
            this.delegate = delegate;
            this.mode = streamingMode;
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
        public Publisher<StreamingEvent> doChat(ChatRequest chatRequest) {
            return delegate.doChat(chatRequest);
        }

        @Override
        public ChatRequestParameters defaultRequestParameters() {
            return delegate.defaultRequestParameters();
        }

        @Override
        public List<ChatModelListener> listeners() {
            return delegate.listeners();
        }

        @Override
        public ModelProvider provider() {
            return delegate.provider();
        }

        @Override
        public Set<Capability> supportedCapabilities() {
            return delegate.supportedCapabilities();
        }

        @Override
        public String toString() {
            return mode + "/" + delegate;
        }
    }
}
