package dev.langchain4j.model.chat.cache;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.ChatRequestOptions;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.Set;

/**
 * A {@link ChatModel} that returns a cached {@link ChatResponse} when it receives a request equal to an earlier one,
 * and otherwise calls the wrapped model and caches its response:
 * <pre>{@code
 * ChatModel chatModel = new CachingChatModel(openAiModel, new InMemoryChatResponseCache(1000));
 * }</pre>
 * Two requests are equal when their messages and parameters are equal. The default request parameters of the wrapped
 * model, including the model name, are part of the cache key, so one cache can be shared by several models.
 * <p>
 * Failed calls are not cached. A cache hit does not call the wrapped model, so its listeners are not notified.
 * {@link #chatAsync(ChatRequest)} and streaming are not supported.
 *
 * @since 1.23.0
 */
@Experimental
public class CachingChatModel implements ChatModel {

    private final ChatModel delegate;
    private final ChatResponseCache cache;

    public CachingChatModel(ChatModel delegate, ChatResponseCache cache) {
        this.delegate = ensureNotNull(delegate, "delegate");
        this.cache = ensureNotNull(cache, "cache");
    }

    @Override
    public ChatResponse chat(ChatRequest chatRequest, ChatRequestOptions options) {
        ensureNotNull(chatRequest, "chatRequest");
        ChatRequest key = chatRequest.toBuilder()
                .parameters(delegate.defaultRequestParameters().overrideWith(chatRequest.parameters()))
                .build();
        ChatResponse cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        ChatResponse response = delegate.chat(chatRequest, options);
        cache.put(key, response);
        return response;
    }

    @Override
    public ChatResponse doChat(ChatRequest chatRequest) {
        return chat(chatRequest, ChatRequestOptions.EMPTY);
    }

    @Override
    public ChatRequestParameters defaultRequestParameters() {
        return delegate.defaultRequestParameters();
    }

    @Override
    public Set<Capability> supportedCapabilities() {
        return delegate.supportedCapabilities();
    }

    @Override
    public ModelProvider provider() {
        return delegate.provider();
    }
}
