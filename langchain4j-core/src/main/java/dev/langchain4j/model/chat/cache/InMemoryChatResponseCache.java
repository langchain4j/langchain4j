package dev.langchain4j.model.chat.cache;

import static dev.langchain4j.internal.ValidationUtils.ensureGreaterThanZero;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A {@link ChatResponseCache} that keeps up to {@code maxSize} responses in memory and evicts the least recently used
 * one when full. It is thread-safe.
 *
 * @since 1.23.0
 */
@Experimental
public class InMemoryChatResponseCache implements ChatResponseCache {

    private final Map<ChatRequest, ChatResponse> responses;

    public InMemoryChatResponseCache(int maxSize) {
        ensureGreaterThanZero(maxSize, "maxSize");
        this.responses = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<ChatRequest, ChatResponse> eldest) {
                return size() > maxSize;
            }
        };
    }

    @Override
    public synchronized ChatResponse get(ChatRequest request) {
        return responses.get(request);
    }

    @Override
    public synchronized void put(ChatRequest request, ChatResponse response) {
        responses.put(request, response);
    }
}
