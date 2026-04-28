package dev.langchain4j.model.chat;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
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

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Flow.Publisher;
import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.Flow.Subscription;

import static dev.langchain4j.internal.InternalFlowUtils.EMPTY_SUBSCRIPTION;
import static dev.langchain4j.model.ModelProvider.OTHER;
import static dev.langchain4j.model.chat.ChatModelListenerUtils.onRequest;
import static dev.langchain4j.model.chat.ChatModelListenerUtils.onResponse;

import static dev.langchain4j.internal.Utils.getOrDefault;

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
    default void chat(ChatRequest request, StreamingChatResponseHandler handler) { // TODO rewrite using publisher
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

    default void chat(String userMessage, StreamingChatResponseHandler handler) {

        ChatRequest chatRequest =
                ChatRequest.builder().messages(UserMessage.from(userMessage)).build();

        chat(chatRequest, handler);
    }

    default void chat(List<ChatMessage> messages, StreamingChatResponseHandler handler) {

        ChatRequest chatRequest = ChatRequest.builder().messages(messages).build();

        chat(chatRequest, handler);
    }

    default Set<Capability> supportedCapabilities() {
        return Set.of();
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
     *     <li>exactly one terminal {@link ChatResponse} (the aggregated final response),</li>
     * </ul>
     * followed by {@code onComplete}. On failure, {@code onError} is signaled after {@code onSubscribe}.
     * <p>
     * Registered {@link ChatModelListener}s are invoked: {@code onRequest} on each new subscription
     * (just before the underlying request goes out), {@code onResponse} after the terminal
     * {@link ChatResponse} is emitted, {@code onError} on failure.
     */
    default Publisher<StreamingEvent> chat(ChatRequest request) {

        ChatRequest finalChatRequest = ChatRequest.builder()
                .messages(request.messages())
                .parameters(defaultRequestParameters().overrideWith(request.parameters()))
                .build();

        List<ChatModelListener> listeners = listeners();

        ModelProvider provider = provider();

        // Note on cancellation: when downstream cancels mid-stream, no listener fires. Consistent
        // with the handler path — listeners observe successful and failed requests; user-initiated
        // cancellation is a third state that the listener API simply doesn't model. TODO

        return new Publisher<StreamingEvent>() {

            @Override
            public void subscribe(Subscriber<? super StreamingEvent> downstream) {

                Map<Object, Object> attributes = new ConcurrentHashMap<>();

                onRequest(finalChatRequest, provider, attributes, listeners);

                Publisher<StreamingEvent> innerPublisher;
                try {
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
                        if (event instanceof ChatResponse chatResponse) {
                            completeResponse = chatResponse;
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

    // TODO accepting options

    default Publisher<StreamingEvent> doChat(ChatRequest chatRequest) {
        throw new RuntimeException("Not implemented");
    }

    // TODO more convenience methods accepting String, messages, etc
}
