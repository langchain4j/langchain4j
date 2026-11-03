package dev.langchain4j.model.chat;

import dev.langchain4j.Experimental;
import dev.langchain4j.exception.AsyncNotSupportedException;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.internal.AsyncNotSupported;
import static dev.langchain4j.internal.CompletableFutureUtils.propagateCancellation;
import static dev.langchain4j.internal.Exceptions.unwrapCompletionException;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.model.ModelProvider.OTHER;
import static dev.langchain4j.model.chat.ChatModelListenerUtils.onError;
import static dev.langchain4j.model.chat.ChatModelListenerUtils.onRequest;
import static dev.langchain4j.model.chat.ChatModelListenerUtils.onResponse;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.internal.InvocationChatRequestOptions;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents a language model that has a chat API.
 * <p>
 * Implementations should override {@link #doChat(ChatRequest)}, not {@link #chat(ChatRequest)}: the default
 * {@link #chat(ChatRequest, ChatRequestOptions)} applies the default request parameters, notifies the
 * {@link ChatModelListener}s and then calls {@link #doChat(ChatRequest)}. AI Services call
 * {@link #chat(ChatRequest, ChatRequestOptions)} (and {@link #chatAsync(ChatRequest, ChatRequestOptions)}), so an
 * override of {@link #chat(ChatRequest)} is used by them only when {@link #doChat(ChatRequest)} is not implemented.
 *
 * @see StreamingChatModel
 */
public interface ChatModel {

    /**
     * This is the main API to interact with the chat model.
     *
     * @param chatRequest a {@link ChatRequest}, containing all the inputs to the LLM
     * @return a {@link ChatResponse}, containing all the outputs from the LLM
     */
    default ChatResponse chat(ChatRequest chatRequest) {
        return chat(chatRequest, ChatRequestOptions.EMPTY);
    }

    /**
     * Sends a chat request with additional invocation options.
     * <p>
     * If the model does not implement {@link #doChat(ChatRequest)} (it overrides {@link #chat(ChatRequest)}
     * instead), the call falls back to {@link #chat(ChatRequest)} without the options, and the {@link #listeners()}
     * are notified of its response or error. A model that also notifies its listeners by hand in its own
     * {@link #chat(ChatRequest)} then reports each call twice; it should implement {@link #doChat(ChatRequest)}
     * instead.
     *
     * @param chatRequest a {@link ChatRequest}, containing all the inputs to the LLM
     * @param options     a {@link ChatRequestOptions} carrying listener attributes and other per-call metadata
     * @return a {@link ChatResponse}, containing all the outputs from the LLM
     * @since 1.13.0
     */
    default ChatResponse chat(ChatRequest chatRequest, ChatRequestOptions options) {

        ChatRequestOptions effectiveOptions = getOrDefault(options, ChatRequestOptions.EMPTY);

        ChatRequest finalChatRequest = ChatRequest.builder()
                .messages(chatRequest.messages())
                .parameters(defaultRequestParameters().overrideWith(chatRequest.parameters()))
                .build();

        List<ChatModelListener> listeners = listeners();
        Map<Object, Object> attributes = new ConcurrentHashMap<>(effectiveOptions.listenerAttributes());
        UUID invocationId = InvocationChatRequestOptions.invocationIdOf(effectiveOptions);

        onRequest(finalChatRequest, provider(), attributes, invocationId, listeners);
        try {
            ChatResponse chatResponse = doChat(finalChatRequest);
            onResponse(chatResponse, finalChatRequest, provider(), attributes, invocationId, listeners);
            return chatResponse;
        } catch (DoChatNotImplementedException notImplemented) {
            if (notImplemented.isThrownBy(this) && effectiveOptions != ChatRequestOptions.EMPTY) {
                try {
                    ChatResponse chatResponse = chat(chatRequest);
                    onResponse(chatResponse, finalChatRequest, provider(), attributes, invocationId, listeners);
                    return chatResponse;
                } catch (Exception error) {
                    onError(error, finalChatRequest, provider(), attributes, invocationId, listeners);
                    throw error;
                }
            }
            onError(notImplemented, finalChatRequest, provider(), attributes, invocationId, listeners);
            throw notImplemented;
        } catch (Exception error) {
            onError(error, finalChatRequest, provider(), attributes, invocationId, listeners);
            throw error;
        }
    }

    /**
     * The provider-specific implementation of {@link #chat(ChatRequest)}, called by
     * {@link #chat(ChatRequest, ChatRequestOptions)} after the default request parameters are applied and the
     * {@link ChatModelListener}s are notified.
     * <p>
     * The default throws a {@link UnsupportedFeatureException}. A model that overrides {@link #chat(ChatRequest)}
     * instead of this method still works when it is called with {@link ChatRequestOptions}: the call then falls
     * back to {@link #chat(ChatRequest)}, without the options.
     */
    default ChatResponse doChat(ChatRequest chatRequest) {
        throw new DoChatNotImplementedException(
                "doChat(ChatRequest) is not implemented by " + getClass().getName(), this);
    }

    /**
     * Non-blocking counterpart of {@link #chat(ChatRequest)}: sends a chat request and returns a
     * {@link CompletableFuture} that completes with the {@link ChatResponse} once the model responds.
     * <p>
     * Unlike {@link #chat(ChatRequest)}, this method does not block the calling thread. Operational
     * failures (including unsupported-parameter validation) are delivered through the returned future
     * (completed exceptionally), not thrown synchronously — the async analog of how the publisher API
     * signals errors via {@code onError}.
     * <p>
     * Registered {@link ChatModelListener}s are invoked: {@code onRequest} when the request is initiated,
     * then {@code onResponse} once the response is available, or {@code onError} on failure.
     * <p>
     * <b>Threading.</b> The returned future is completed on the model's own thread - for HTTP models, the
     * transport's I/O worker that reads the response (the JDK HTTP client's {@code HttpClient-*} workers). A
     * continuation attached without an explicit executor ({@code thenApply}, {@code thenAccept}, ...) therefore
     * runs on that thread, where blocking is as harmful as blocking in a {@link ChatModelListener} callback: it
     * stalls the worker and, under concurrency, degrades throughput for every in-flight call. Keep continuations
     * non-blocking, or hand blocking work to your own {@link java.util.concurrent.Executor} via the
     * {@code *Async} variants ({@code thenApplyAsync(fn, executor)}).
     *
     * @param chatRequest a {@link ChatRequest}, containing all the inputs to the LLM
     * @return a {@link CompletableFuture} of the {@link ChatResponse}
     * @since 1.20.0
     */
    @Experimental
    default CompletableFuture<ChatResponse> chatAsync(ChatRequest chatRequest) {
        return chatAsync(chatRequest, ChatRequestOptions.EMPTY);
    }

    /**
     * Sends a non-blocking chat request with additional invocation options.
     * <p>
     * If the model does not implement {@link #doChatAsync(ChatRequest)} but overrides {@link #chatAsync(ChatRequest)},
     * the call falls back to {@link #chatAsync(ChatRequest)} without the options, and the {@link #listeners()} are
     * notified of its response or error.
     *
     * @param chatRequest a {@link ChatRequest}, containing all the inputs to the LLM
     * @param options     a {@link ChatRequestOptions} carrying listener attributes and other per-call metadata
     * @return a {@link CompletableFuture} of the {@link ChatResponse}
     * @see #chatAsync(ChatRequest)
     * @since 1.20.0
     */
    @Experimental
    default CompletableFuture<ChatResponse> chatAsync(ChatRequest chatRequest, ChatRequestOptions options) {

        ChatRequestOptions effectiveOptions = getOrDefault(options, ChatRequestOptions.EMPTY);

        ChatRequest finalChatRequest = ChatRequest.builder()
                .messages(chatRequest.messages())
                .parameters(defaultRequestParameters().overrideWith(chatRequest.parameters()))
                .build();

        List<ChatModelListener> listeners = listeners();
        Map<Object, Object> attributes = new ConcurrentHashMap<>(effectiveOptions.listenerAttributes());
        UUID invocationId = InvocationChatRequestOptions.invocationIdOf(effectiveOptions);

        onRequest(finalChatRequest, provider(), attributes, invocationId, listeners);

        CompletableFuture<ChatResponse> source;
        try {
            source = doChatAsync(finalChatRequest);
        } catch (Exception error) {
            onError(error, finalChatRequest, provider(), attributes, invocationId, listeners);
            return CompletableFuture.failedFuture(error);
        }

        if (AsyncNotSupported.isNotImplementedFutureOf(source, this)
                && effectiveOptions != ChatRequestOptions.EMPTY) {
            try {
                source = chatAsync(chatRequest);
            } catch (Exception error) {
                onError(error, finalChatRequest, provider(), attributes, invocationId, listeners);
                return CompletableFuture.failedFuture(error);
            }
        }

        CompletableFuture<ChatResponse> result = source.whenComplete((chatResponse, error) -> {
            if (error != null) {
                Throwable cause = unwrapCompletionException(error);
                if (!(cause instanceof CancellationException)) {
                    onError(cause, finalChatRequest, provider(), attributes, invocationId, listeners);
                }
            } else {
                onResponse(chatResponse, finalChatRequest, provider(), attributes, invocationId, listeners);
            }
        });

        propagateCancellation(result, source);
        return result;
    }

    /**
     * SPI hook for a genuinely non-blocking chat implementation, invoked by {@link #chatAsync(ChatRequest)}.
     * <p>
     * The default returns a failed future carrying {@link AsyncNotSupportedException} to signal that this model has no native asynchronous
     * implementation. Callers on the asynchronous and reactive path (for example the non-blocking RAG stages) detect
     * this and either offload the blocking {@link #doChat(ChatRequest)} or fail loudly with an actionable message.
     * A model backed by remote HTTP I/O overrides this with a genuinely asynchronous call (no thread parked).
     *
     * @param chatRequest a {@link ChatRequest}, containing all the inputs to the LLM
     * @return a {@link CompletableFuture} of the {@link ChatResponse}
     * @since 1.20.0
     */
    @Experimental
    default CompletableFuture<ChatResponse> doChatAsync(ChatRequest chatRequest) {
        return AsyncNotSupported.notImplementedFuture(this, "doChatAsync");
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

    default String chat(String userMessage) {

        ChatRequest chatRequest =
                ChatRequest.builder().messages(UserMessage.from(userMessage)).build();

        ChatResponse chatResponse = chat(chatRequest);

        return chatResponse.aiMessage().text();
    }

    default ChatResponse chat(ChatMessage... messages) {

        ChatRequest chatRequest = ChatRequest.builder().messages(messages).build();

        return chat(chatRequest);
    }

    default ChatResponse chat(List<ChatMessage> messages) {

        ChatRequest chatRequest = ChatRequest.builder().messages(messages).build();

        return chat(chatRequest);
    }

    /**
     * Non-blocking convenience counterpart of {@link #chat(String)}: completes with the text of the model's response.
     *
     * @since 1.20.0
     */
    @Experimental
    default CompletableFuture<String> chatAsync(String userMessage) {

        ChatRequest chatRequest =
                ChatRequest.builder().messages(UserMessage.from(userMessage)).build();

        return chatAsync(chatRequest).thenApply(chatResponse -> chatResponse.aiMessage().text());
    }

    /**
     * Non-blocking convenience counterpart of {@link #chat(ChatMessage...)}.
     *
     * @since 1.20.0
     */
    @Experimental
    default CompletableFuture<ChatResponse> chatAsync(ChatMessage... messages) {

        ChatRequest chatRequest = ChatRequest.builder().messages(messages).build();

        return chatAsync(chatRequest);
    }

    /**
     * Non-blocking convenience counterpart of {@link #chat(List)}.
     *
     * @since 1.20.0
     */
    @Experimental
    default CompletableFuture<ChatResponse> chatAsync(List<ChatMessage> messages) {

        ChatRequest chatRequest = ChatRequest.builder().messages(messages).build();

        return chatAsync(chatRequest);
    }

    default Set<Capability> supportedCapabilities() {
        return Set.of();
    }
}
