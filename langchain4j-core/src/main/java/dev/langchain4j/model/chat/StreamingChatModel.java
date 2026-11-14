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

import static dev.langchain4j.internal.InternalFlowUtils.EMPTY_SUBSCRIPTION;
import static dev.langchain4j.internal.Utils.getOrDefault;
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
     *
     * @param request a {@link ChatRequest}, containing all the inputs to the LLM
     * @param handler a {@link StreamingChatResponseHandler} that will handle streaming response from the LLM
     */
    default void chat(ChatRequest request, StreamingChatResponseHandler handler) {
        chat(request, ChatRequestOptions.EMPTY, handler);
    }

    /**
     * Sends a streaming chat request with additional invocation options.
     *
     * @param request a {@link ChatRequest}, containing all the inputs to the LLM
     * @param options a {@link ChatRequestOptions} carrying listener attributes and other per-call metadata
     * @param handler a {@link StreamingChatResponseHandler} that will handle streaming response from the LLM
     * @since 1.13.0
     */
    default void chat(ChatRequest request, ChatRequestOptions options, StreamingChatResponseHandler handler) {

        ChatRequest finalChatRequest = ChatRequest.builder()
                .messages(request.messages())
                .parameters(defaultRequestParameters().overrideWith(request.parameters()))
                .build();

        ChatRequestOptions effectiveOptions = getOrDefault(options, ChatRequestOptions.EMPTY);

        List<ChatModelListener> listeners = ObservabilityListeners.chat(listeners());
        Map<Object, Object> attributes = new ConcurrentHashMap<>(effectiveOptions.listenerAttributes());
        UUID invocationId = CurrentInvocation.invocationId(effectiveOptions);

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
                handler.onCompleteResponse(completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                ChatModelListenerUtils.onError(error, finalChatRequest, provider(), attributes, invocationId, listeners);
                handler.onError(error);
            }
        };

        onRequest(finalChatRequest, provider(), attributes, invocationId, listeners);
        int maxRetries = maxRetries();
        if (maxRetries > 0) {
            new RetryingStreamingChat(this::doChat, finalChatRequest, observingHandler, maxRetries, invocationId)
                    .start();
        } else {
            doChat(finalChatRequest, observingHandler);
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
     * followed by {@code onComplete}. On failure, {@code onError} is signaled after {@code onSubscribe}.
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

        ChatRequest finalChatRequest = ChatRequest.builder()
                .messages(request.messages())
                .parameters(defaultRequestParameters().overrideWith(request.parameters()))
                .build();

        List<ChatModelListener> listeners = ObservabilityListeners.chat(listeners());

        ModelProvider provider = provider();
        UUID invocationId = CurrentInvocation.invocationId();

        return new Publisher<ChatModelStreamingEvent>() {

            @Override
            public void subscribe(Subscriber<? super ChatModelStreamingEvent> downstream) {

                Map<Object, Object> attributes = new ConcurrentHashMap<>();

                Publisher<ChatModelStreamingEvent> innerPublisher;
                try {
                    onRequest(finalChatRequest, provider, attributes, invocationId, listeners);
                    innerPublisher = doChat(finalChatRequest);
                } catch (Throwable error) {
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

    /**
     * The maximum number of times
     * {@link #chat(ChatRequest, ChatRequestOptions, StreamingChatResponseHandler)} retries a failed request.
     * <p>
     * A request is retried only if it fails before anything (a partial response, partial thinking, partial or
     * complete tool call, or the complete response) is delivered to the {@link StreamingChatResponseHandler},
     * and the error is an {@link Exception} that is not a {@link dev.langchain4j.exception.NonRetriableException}
     * (the same rule that {@link ChatModel} implementations use).
     * Once anything has been delivered, the error is passed to
     * {@link StreamingChatResponseHandler#onError(Throwable)} as usual.
     * Raw events passed to {@link StreamingChatResponseHandler#onUnmappedRawEvent(Object)} do not prevent
     * a retry, so the handler may receive raw events from a failed attempt; if that callback throws,
     * the request is not retried.
     * <p>
     * Retries run on the default executor (see {@link dev.langchain4j.spi.ExecutorProvider}) after an
     * exponential backoff with jitter (500 ms, 750 ms, 1125 ms, ...), without blocking a thread.
     * Like other retry backoff, a retry does not carry the context captured by
     * {@link dev.langchain4j.spi.ExecutorProvider#captureContext()}.
     * {@link ChatModelListener}s are notified once per call, not once per attempt.
     * An exception thrown synchronously by the first attempt (for example, for an unsupported request parameter)
     * propagates to the caller and is not retried.
     * <p>
     * Unlike {@link ChatModel} implementations, which usually retry twice by default, retries are disabled by default.
     * Retries apply only to models that do not override
     * {@link #chat(ChatRequest, ChatRequestOptions, StreamingChatResponseHandler)} or
     * {@link #chat(ChatRequest, StreamingChatResponseHandler)}.
     * The reactive {@link #chat(ChatRequest)} does not retry.
     * A {@code StreamingChatModel} that delegates to another model's {@code chat(...)} should not override this
     * method, otherwise each request is retried by both models.
     *
     * @return the maximum number of retries; {@code 0} or less disables retries
     * @since 1.24.0
     */
    @Experimental
    default int maxRetries() {
        return 0;
    }

    default ModelProvider provider() {
        return OTHER;
    }

    default void chat(String userMessage, StreamingChatResponseHandler handler) {

        ChatRequest chatRequest =
                ChatRequest.builder().messages(UserMessage.from(userMessage)).build();

        chat(chatRequest, handler);
    }

    default void chat(List<ChatMessage> messages, StreamingChatResponseHandler handler) {

        ChatRequest chatRequest = ChatRequest.builder().messages(messages).build();

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

        ChatRequest chatRequest =
                ChatRequest.builder().messages(UserMessage.from(userMessage)).build();

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

        ChatRequest chatRequest = ChatRequest.builder().messages(messages).build();

        return chat(chatRequest);
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

        ChatRequest chatRequest = ChatRequest.builder().messages(messages).build();

        return chat(chatRequest);
    }

    default Set<Capability> supportedCapabilities() {
        return Set.of();
    }
}
