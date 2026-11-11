package dev.langchain4j.model.chat.cache;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

/**
 * Stores {@link ChatResponse}s by the {@link ChatRequest} that produced them, for {@link CachingChatModel}.
 * Implement it to keep responses in an external store.
 *
 * @see InMemoryChatResponseCache
 * @since 1.23.0
 */
@Experimental
public interface ChatResponseCache {

    /**
     * @return the response stored for this request, or {@code null} if there is none.
     */
    ChatResponse get(ChatRequest request);

    void put(ChatRequest request, ChatResponse response);
}
