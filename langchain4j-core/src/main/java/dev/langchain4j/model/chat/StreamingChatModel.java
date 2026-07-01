package dev.langchain4j.model.chat;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.response.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
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
 *
 * @see ChatModel
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

        List<ChatModelListener> listeners = listeners();
        Map<Object, Object> attributes = new ConcurrentHashMap<>(effectiveOptions.listenerAttributes());

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
                onResponse(completeResponse, finalChatRequest, provider(), attributes, listeners);
                handler.onCompleteResponse(completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                ChatModelListenerUtils.onError(error, finalChatRequest, provider(), attributes, listeners);
                handler.onError(error);
            }
        };

        onRequest(finalChatRequest, provider(), attributes, listeners);
        doChat(finalChatRequest, observingHandler);
    }

    default void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
        throw new RuntimeException("Not implemented");
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

    default Set<Capability> supportedCapabilities() {
        return Set.of();
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
     * Reactive entry point: sends a chat request and returns a {@link Publisher} of {@link StreamingEvent}s. TODO
     * <p>
     * The publisher is cold: each {@code subscribe()} call initiates a new LLM request.
     * It emits events in this order:
     * <ul>
     *     <li>0..N {@link dev.langchain4j.model.chat.response.PartialThinking} (thinking/reasoning chunks),</li>
     *     <li>0..N {@link dev.langchain4j.model.chat.response.PartialResponse} (text chunks),</li>
     *     <li>0..N {@link dev.langchain4j.model.chat.response.PartialToolCall} (tool-call argument chunks),</li>
     *     <li>0..N {@link dev.langchain4j.model.chat.response.CompleteToolCall} (assembled tool calls),</li>
     *     // TODO raw events
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
     * Subscribers must be prepared to receive {@link StreamingEvent} subtypes they do not recognize and
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
     *
     * @since 1.17.0
     */
    default Publisher<StreamingEvent> chat(ChatRequest request) {

        ChatRequest finalChatRequest = ChatRequest.builder()
                .messages(request.messages())
                .parameters(defaultRequestParameters().overrideWith(request.parameters()))
                .build();

        List<ChatModelListener> listeners = listeners();

        ModelProvider provider = provider();

        return new Publisher<StreamingEvent>() {

            @Override
            public void subscribe(Subscriber<? super StreamingEvent> downstream) {

                Map<Object, Object> attributes = new ConcurrentHashMap<>();

                Publisher<StreamingEvent> innerPublisher;
                try {
                    onRequest(finalChatRequest, provider, attributes, listeners);
                    innerPublisher = doChat(finalChatRequest);
                } catch (Throwable error) {
                    ChatModelListenerUtils.onError(error, finalChatRequest, provider, attributes, listeners);
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
                    public void onNext(StreamingEvent event) {
                        if (event instanceof CompleteResponse completeResponseEvent) {
                            completeResponse = completeResponseEvent.chatResponse();
                        }
                        downstream.onNext(event);
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        ChatModelListenerUtils.onError(throwable, finalChatRequest, provider, attributes, listeners);
                        downstream.onError(throwable);
                    }

                    @Override
                    public void onComplete() {
                        if (completeResponse != null) {
                            onResponse(completeResponse, finalChatRequest, provider, attributes, listeners);
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
     * typically consume the response eagerly and relay {@link StreamingEvent}s through a bounded buffer rather
     * than propagating subscriber demand to the model.
     * <p>
     * The default implementation throws {@link UnsupportedFeatureException}; a provider that does not support
     * reactive streaming leaves it unimplemented.
     */
    default Publisher<StreamingEvent> doChat(ChatRequest chatRequest) {
        throw new UnsupportedFeatureException("Not implemented yet for " + getClass().getName()); // TODO
    }

    // TODO more convenience methods accepting String, messages, etc
}
