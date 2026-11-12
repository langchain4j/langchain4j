package dev.langchain4j.model.chat;

import dev.langchain4j.internal.ObservabilityListeners;
import dev.langchain4j.Experimental;
import dev.langchain4j.exception.AsyncNotSupportedException;
import dev.langchain4j.internal.AsyncNotSupported;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.internal.CurrentInvocation;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.response.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Flow.Publisher;
import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.Flow.Subscription;
import java.util.concurrent.atomic.AtomicBoolean;

import static dev.langchain4j.internal.Exceptions.restoreInterruptIfInterrupted;
import static dev.langchain4j.internal.InternalFlowUtils.EMPTY_SUBSCRIPTION;
import static dev.langchain4j.internal.InternalFlowUtils.failedPublisher;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.logIgnoredError;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.withLoggingExceptions;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.model.ModelProvider.OTHER;
import static dev.langchain4j.model.chat.ChatModelListenerUtils.onRequest;
import static dev.langchain4j.model.chat.ChatModelListenerUtils.onResponse;

/**
 * Represents a language model that has a chat API and can stream a response one token at a time.
 * <p>
 * A streaming response can be cancelled with the {@link StreamingHandle}
 * that is passed to the {@link StreamingChatResponseHandler} callbacks that accept a context,
 * such as {@link StreamingChatResponseHandler#onPartialResponse(PartialResponse, PartialResponseContext)}.
 * When using {@link #chat(ChatRequest)}, which returns a {@link Publisher},
 * cancel the {@link Subscription} instead.
 *
 * @see ChatModel
 * @see StreamingHandle
 */
public interface StreamingChatModel {

    /**
     * This is the main API to interact with the chat model.
     * <p>
     * This method does not throw: every failure, including an invalid request, is reported to
     * {@link StreamingChatResponseHandler#onError(Throwable)}. {@code onError} may be called on the calling thread,
     * before this method returns. With a {@code null} handler, a failure found before the request is sent is thrown
     * instead, because there is no handler to report it to.
     *
     * @param request a {@link ChatRequest}, containing all the inputs to the LLM
     * @param handler a {@link StreamingChatResponseHandler} that will handle streaming response from the LLM
     */
    default void chat(ChatRequest request, StreamingChatResponseHandler handler) {
        chat(request, ChatRequestOptions.EMPTY, handler);
    }

    /**
     * Sends a streaming chat request with additional invocation options.
     * <p>
     * This method does not throw: every failure, including an invalid request, is reported to
     * {@link StreamingChatResponseHandler#onError(Throwable)}. {@code onError} may be called on the calling thread,
     * before this method returns. With a {@code null} handler, a failure found before the request is sent is thrown
     * instead, because there is no handler to report it to.
     *
     * @param request a {@link ChatRequest}, containing all the inputs to the LLM
     * @param options a {@link ChatRequestOptions} carrying listener attributes and other per-call metadata
     * @param handler a {@link StreamingChatResponseHandler} that will handle streaming response from the LLM
     * @since 1.13.0
     */
    default void chat(ChatRequest request, ChatRequestOptions options, StreamingChatResponseHandler handler) {

        ChatRequest finalChatRequest;
        List<ChatModelListener> listeners;
        Map<Object, Object> attributes;
        UUID invocationId;
        try {
            ensureNotNull(request, "request");
            finalChatRequest = ChatRequest.builder()
                    .messages(request.messages())
                    .parameters(defaultRequestParameters().overrideWith(request.parameters()))
                    .build();

            ChatRequestOptions effectiveOptions = getOrDefault(options, ChatRequestOptions.EMPTY);

            listeners = ObservabilityListeners.chat(listeners());
            attributes = new ConcurrentHashMap<>(effectiveOptions.listenerAttributes());
            invocationId = CurrentInvocation.invocationId(effectiveOptions);
        } catch (Throwable error) {
            restoreInterruptIfInterrupted(error);
            if (handler == null) {
                throw error;
            }
            withLoggingExceptions(() -> handler.onError(error));
            return;
        }

        AtomicBoolean responseNotified = new AtomicBoolean();
        AtomicBoolean completed = new AtomicBoolean();
        AtomicBoolean errorReported = new AtomicBoolean();
        StreamingChatResponseHandler observingHandler = new StreamingChatResponseHandler() {

            @Override
            public void onPartialResponse(String partialResponse) {
                handler.onPartialResponse(partialResponse);
            }

            @Override
            public void onPartialResponse(PartialResponse partialResponse, PartialResponseContext context) {
                handler.onPartialResponse(partialResponse, context);
            }

            @Override
            public void onPartialThinking(PartialThinking partialThinking) {
                handler.onPartialThinking(partialThinking);
            }

            @Override
            public void onPartialThinking(PartialThinking partialThinking, PartialThinkingContext context) {
                handler.onPartialThinking(partialThinking, context);
            }

            @Override
            public void onPartialToolCall(PartialToolCall partialToolCall) {
                handler.onPartialToolCall(partialToolCall);
            }

            @Override
            public void onPartialToolCall(PartialToolCall partialToolCall, PartialToolCallContext context) {
                handler.onPartialToolCall(partialToolCall, context);
            }

            @Override
            public void onCompleteToolCall(CompleteToolCall completeToolCall) {
                handler.onCompleteToolCall(completeToolCall);
            }

            @Override
            public void onUnmappedRawEvent(Object rawEvent) {
                handler.onUnmappedRawEvent(rawEvent);
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                onResponse(completeResponse, finalChatRequest, provider(), attributes, invocationId, listeners);
                responseNotified.set(true);
                handler.onCompleteResponse(completeResponse);
                completed.set(true);
            }

            @Override
            public void onError(Throwable error) {
                if (completed.get() || !errorReported.compareAndSet(false, true)) {
                    logIgnoredError(error);
                    return;
                }
                if (!responseNotified.get()) {
                    ChatModelListenerUtils.onError(
                            error, finalChatRequest, provider(), attributes, invocationId, listeners);
                }
                handler.onError(error);
            }
        };

        try {
            onRequest(finalChatRequest, provider(), attributes, invocationId, listeners);
            doChat(finalChatRequest, observingHandler);
        } catch (Throwable error) {
            restoreInterruptIfInterrupted(error);
            if (handler == null && !completed.get() && !errorReported.get()) {
                if (!responseNotified.get()) {
                    ChatModelListenerUtils.onError(
                            error, finalChatRequest, provider(), attributes, invocationId, listeners);
                }
                throw error;
            }
            withLoggingExceptions(() -> observingHandler.onError(error));
        }
    }

    default void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
        throw new RuntimeException("Not implemented");
    }

    /**
     * Reactive entry point: sends a chat request and returns a {@link Publisher} of {@link ChatModelStreamingEvent}s.
     * <p>
     * The publisher is cold: nothing happens until you subscribe, and each {@code subscribe()} call
     * initiates a new LLM request.
     * It emits, in this order (each {@link dev.langchain4j.model.chat.response.CompleteToolCall} arriving as soon
     * as that tool call finishes assembling, so it interleaves with the next call's chunks):
     * <ul>
     *     <li>0..N {@link dev.langchain4j.model.chat.response.PartialThinking} (thinking/reasoning chunks),</li>
     *     <li>0..N {@link dev.langchain4j.model.chat.response.PartialResponse} (text chunks),</li>
     *     <li>0..N {@link dev.langchain4j.model.chat.response.PartialToolCall} (tool-call argument chunks),</li>
     *     <li>0..N {@link dev.langchain4j.model.chat.response.CompleteToolCall} (assembled tool calls),</li>
     *     <li>0..N {@link dev.langchain4j.model.chat.response.RawStreamingEvent} (provider-specific raw events,
     *         interleaved with the above),</li>
     *     <li>exactly one terminal {@link CompleteResponse} (wrapping the aggregated final {@link ChatResponse}),</li>
     * </ul>
     * followed by {@code onComplete}. On failure, {@code onError} is signaled after {@code onSubscribe}. This method
     * never throws: any failure, including an invalid request, reaches the subscriber's {@code onError}.
     * <p>
     * Registered {@link ChatModelListener}s are invoked: {@code onRequest} on each new subscription
     * (just before the underlying request goes out), {@code onResponse} after the terminal
     * {@link ChatResponse} is emitted, {@code onError} on failure.
     * <p>
     * If the {@link Subscriber} throws from {@code onNext} (or any other signal method), it violates the
     * Reactive Streams contract (Rule 2.13): the stream is cancelled and no further events are delivered,
     * and no {@link ChatModelListener} callback fires for it — neither {@code onResponse} nor
     * {@code onError}. This differs from the handler-based
     * {@link #chat(ChatRequest, StreamingChatResponseHandler)} path, which catches exceptions thrown from
     * handler callbacks, reports them to {@code onError}, and keeps streaming.
     * <p>
     * Subscribers must be prepared to receive {@link ChatModelStreamingEvent} subtypes they do not recognize and
     * ignore them. New event types may be introduced over time (and providers may surface unmapped events
     * as {@link dev.langchain4j.model.chat.response.RawStreamingEvent}), so consuming this stream with an
     * exhaustive type switch that lacks a default branch is unsafe.
     * <p>
     * <b>Demand and back-pressure.</b> This streams a finite, bounded-rate source — an LLM response over HTTP.
     * Implementations are <b>not</b> required to propagate subscriber demand to the model: meaningfully
     * throttling an LLM is impractical (its work and cost are incurred regardless of how fast the response is
     * read, and stalling the transport to slow it down only risks provider/proxy idle timeouts). An
     * implementation therefore typically consumes the response eagerly and relays events through a
     * <b>bounded</b> internal buffer. A subscriber that requests fewer items than are produced may thus cause
     * buffering and, once the buffer is exhausted, a terminal error. Subscribers should request liberally
     * (e.g. {@code Long.MAX_VALUE}) and must <b>not</b> block or perform heavy work in {@code onNext} — offload
     * it to another thread.
     * <p>
     * <b>Threading.</b> Events are delivered on the model's own thread — for HTTP models, the transport's I/O
     * worker that reads the response (the JDK HTTP client's {@code HttpClient-*} workers), the same scarce,
     * shared threads a {@link dev.langchain4j.model.chat.listener.ChatModelListener} callback runs on. Blocking
     * there stalls this stream and, under concurrency, degrades throughput for every in-flight call.
     *
     * @since 1.20.0
     */
    @Experimental
    default Publisher<ChatModelStreamingEvent> chat(ChatRequest request) {

        ChatRequest finalChatRequest;
        List<ChatModelListener> listeners;
        ModelProvider provider;
        UUID invocationId;
        try {
            ensureNotNull(request, "request");
            finalChatRequest = ChatRequest.builder()
                    .messages(request.messages())
                    .parameters(defaultRequestParameters().overrideWith(request.parameters()))
                    .build();

            listeners = ObservabilityListeners.chat(listeners());

            provider = provider();
            invocationId = CurrentInvocation.invocationId();
        } catch (Throwable error) {
            restoreInterruptIfInterrupted(error);
            return failedPublisher(error);
        }

        return new Publisher<ChatModelStreamingEvent>() {

            @Override
            public void subscribe(Subscriber<? super ChatModelStreamingEvent> downstream) {

                Map<Object, Object> attributes = new ConcurrentHashMap<>();

                Publisher<ChatModelStreamingEvent> innerPublisher;
                try {
                    onRequest(finalChatRequest, provider, attributes, invocationId, listeners);
                    innerPublisher = doChat(finalChatRequest);
                    if (innerPublisher == null) {
                        throw new IllegalStateException(
                                StreamingChatModel.this.getClass().getName() + ".doChat(ChatRequest) returned null");
                    }
                } catch (Throwable error) {
                    restoreInterruptIfInterrupted(error);
                    ChatModelListenerUtils.onError(error, finalChatRequest, provider, attributes, invocationId, listeners);
                    downstream.onSubscribe(EMPTY_SUBSCRIPTION);
                    downstream.onError(error);
                    return;
                }

                innerPublisher.subscribe(new Subscriber<>() {

                    private ChatResponse completeResponse;

                    @Override
                    public void onSubscribe(Subscription subscription) {
                        downstream.onSubscribe(subscription);
                    }

                    @Override
                    public void onNext(ChatModelStreamingEvent event) {
                        if (event instanceof CompleteResponse completeResponseEvent) {
                            completeResponse = completeResponseEvent.chatResponse();
                        }
                        downstream.onNext(event);
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        ChatModelListenerUtils.onError(throwable, finalChatRequest, provider, attributes, invocationId, listeners);
                        downstream.onError(throwable);
                    }

                    @Override
                    public void onComplete() {
                        if (completeResponse != null) {
                            onResponse(completeResponse, finalChatRequest, provider, attributes, invocationId, listeners);
                        }
                        downstream.onComplete();
                    }
                });
            }
        };
    }

    /**
     * Provider-specific implementation of the reactive stream returned by {@link #chat(ChatRequest)} (which wraps
     * it with {@link ChatModelListener} invocation). Implementations must honor the event ordering and the
     * demand / back-pressure expectations documented on {@link #chat(ChatRequest)} — in particular, they
     * typically consume the response eagerly and relay {@link ChatModelStreamingEvent}s through a bounded buffer rather
     * than propagating subscriber demand to the model.
     * <p>
     * The default implementation returns an immediately-failing Publisher carrying {@link AsyncNotSupportedException} to signal that this model has no
     * native reactive-streaming implementation; a provider that does not support reactive streaming leaves it
     * unimplemented (consistent with {@code ChatModel#doChatAsync} and the other async defaults).
     * <p>
     * An implementation never throws and never returns {@code null}: every failure, including an invalid request, is
     * signaled to the subscriber's {@code onError} after {@code onSubscribe}.
     *
     * @since 1.20.0
     */
    @Experimental
    default Publisher<ChatModelStreamingEvent> doChat(ChatRequest chatRequest) {
        return AsyncNotSupported.failingPublisher(getClass(), "doChat");
    }

    default ChatRequestParameters defaultRequestParameters() {
        return DefaultChatRequestParameters.EMPTY;
    }

    default List<ChatModelListener> listeners() {
        return List.of();
    }

    default ModelProvider provider() {
        return OTHER;
    }

    /**
     * Sends a single user message. Like {@link #chat(ChatRequest, StreamingChatResponseHandler)}, it does not throw:
     * an invalid argument, for example a {@code null} message, is reported to the handler's {@code onError}.
     */
    default void chat(String userMessage, StreamingChatResponseHandler handler) {

        ChatRequest chatRequest;
        try {
            chatRequest = ChatRequest.builder().messages(UserMessage.from(userMessage)).build();
        } catch (Throwable error) {
            restoreInterruptIfInterrupted(error);
            if (handler == null) {
                throw error;
            }
            withLoggingExceptions(() -> handler.onError(error));
            return;
        }

        chat(chatRequest, handler);
    }

    /**
     * Sends the given messages. Like {@link #chat(ChatRequest, StreamingChatResponseHandler)}, it does not throw: an
     * invalid argument, for example no messages, is reported to the handler's {@code onError}.
     */
    default void chat(List<ChatMessage> messages, StreamingChatResponseHandler handler) {

        ChatRequest chatRequest;
        try {
            chatRequest = ChatRequest.builder().messages(messages).build();
        } catch (Throwable error) {
            restoreInterruptIfInterrupted(error);
            if (handler == null) {
                throw error;
            }
            withLoggingExceptions(() -> handler.onError(error));
            return;
        }

        chat(chatRequest, handler);
    }

    /**
     * Reactive convenience counterpart of {@link #chat(String, StreamingChatResponseHandler)}: returns a cold
     * {@code Publisher} that streams the model's textual response to {@code userMessage}, token by token.
     * <p>
     * This is the streaming analog of the simplified {@link ChatModel#chat(String)} (which returns the response
     * {@code String}): it emits only the text chunks ({@link PartialResponse#text()}), filtering out the other
     * {@link ChatModelStreamingEvent}s of the underlying {@link #chat(ChatRequest)} stream. For the full event stream,
     * use {@link #chat(ChatMessage...)} / {@link #chat(List)} / {@link #chat(ChatRequest)}.
     * <p>
     * Nothing happens until you subscribe: the request is sent on each {@code subscribe()} call.
     *
     * @since 1.20.0
     */
    @Experimental
    default Publisher<String> chat(String userMessage) {

        ChatRequest chatRequest;
        try {
            chatRequest = ChatRequest.builder().messages(UserMessage.from(userMessage)).build();
        } catch (Throwable error) {
            restoreInterruptIfInterrupted(error);
            return failedPublisher(error);
        }

        return downstream -> chat(chatRequest).subscribe(new Subscriber<>() {

            private Subscription subscription;

            @Override
            public void onSubscribe(Subscription subscription) {
                this.subscription = subscription;
                downstream.onSubscribe(subscription);
            }

            @Override
            public void onNext(ChatModelStreamingEvent event) {
                if (event instanceof PartialResponse partialResponse) {
                    downstream.onNext(partialResponse.text());
                } else {
                    subscription.request(1);
                }
            }

            @Override
            public void onError(Throwable throwable) {
                downstream.onError(throwable);
            }

            @Override
            public void onComplete() {
                downstream.onComplete();
            }
        });
    }

    /**
     * Reactive convenience overload accepting the messages directly: returns a cold {@code Publisher} that streams
     * the response to {@code messages}.
     * <p>
     * Nothing happens until you subscribe: the request is sent on each {@code subscribe()} call.
     *
     * @since 1.20.0
     */
    @Experimental
    default Publisher<ChatModelStreamingEvent> chat(ChatMessage... messages) {
        try {
            return chat(ChatRequest.builder().messages(messages).build());
        } catch (Throwable error) {
            restoreInterruptIfInterrupted(error);
            return failedPublisher(error);
        }
    }

    /**
     * Reactive convenience counterpart of {@link #chat(List, StreamingChatResponseHandler)}: returns a cold
     * {@code Publisher} that streams the response to {@code messages}.
     * <p>
     * Nothing happens until you subscribe: the request is sent on each {@code subscribe()} call.
     *
     * @since 1.20.0
     */
    @Experimental
    default Publisher<ChatModelStreamingEvent> chat(List<ChatMessage> messages) {
        try {
            return chat(ChatRequest.builder().messages(messages).build());
        } catch (Throwable error) {
            restoreInterruptIfInterrupted(error);
            return failedPublisher(error);
        }
    }

    default Set<Capability> supportedCapabilities() {
        return Set.of();
    }
}
