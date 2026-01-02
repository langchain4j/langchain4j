package dev.langchain4j.store.embedding.listener;

import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import java.util.List;
import java.util.Map;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * The embedding store response context.
 * It contains the response details, corresponding request details, and attributes.
 * The attributes can be used to pass data between methods of an {@link EmbeddingStoreListener}
 * or between multiple {@link EmbeddingStoreListener}s.
 */
public class EmbeddingStoreResponseContext<Embedded> {

    private final EmbeddingStoreOperation operation;
    private final EmbeddingStore<Embedded> embeddingStore;
    private final Map<Object, Object> attributes;

    private final EmbeddingStoreRequestContext<Embedded> requestContext;

    private final String returnedId;
    private final List<String> returnedIds;
    private final EmbeddingSearchResult<Embedded> searchResult;

    public EmbeddingStoreResponseContext(
            EmbeddingStoreOperation operation,
            EmbeddingStore<Embedded> embeddingStore,
            Map<Object, Object> attributes,
            EmbeddingStoreRequestContext<Embedded> requestContext,
            String returnedId,
            List<String> returnedIds,
            EmbeddingSearchResult<Embedded> searchResult) {
        this.operation = ensureNotNull(operation, "operation");
        this.embeddingStore = ensureNotNull(embeddingStore, "embeddingStore");
        this.attributes = ensureNotNull(attributes, "attributes");
        this.requestContext = ensureNotNull(requestContext, "requestContext");
        this.returnedId = returnedId;
        this.returnedIds = returnedIds;
        this.searchResult = searchResult;
    }

    public EmbeddingStoreOperation operation() {
        return operation;
    }

    public EmbeddingStore<Embedded> embeddingStore() {
        return embeddingStore;
    }

    /**
     * @return The attributes map. It can be used to pass data between methods of an {@link EmbeddingStoreListener}
     * or between multiple {@link EmbeddingStoreListener}s.
     */
    public Map<Object, Object> attributes() {
        return attributes;
    }

    /**
     * @return The corresponding request context.
     */
    public EmbeddingStoreRequestContext<Embedded> requestContext() {
        return requestContext;
    }

    /**
     * @return The returned ID for operations like {@code add(Embedding)} and {@code add(Embedding, Embedded)} (if applicable).
     */
    public String returnedId() {
        return returnedId;
    }

    /**
     * @return The returned IDs for operations like {@code addAll(List<Embedding>)} and {@code addAll(List<Embedding>, List<Embedded>)} (if applicable).
     */
    public List<String> returnedIds() {
        return returnedIds;
    }

    /**
     * @return The search result for {@code search(...)} (if applicable).
     */
    public EmbeddingSearchResult<Embedded> searchResult() {
        return searchResult;
    }
}


