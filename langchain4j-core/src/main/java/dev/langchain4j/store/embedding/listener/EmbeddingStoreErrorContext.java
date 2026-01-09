package dev.langchain4j.store.embedding.listener;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.store.embedding.EmbeddingStore;
import java.util.Map;

/**
 * The embedding store error context.
 * It contains the error, corresponding request details, and attributes.
 * The attributes can be used to pass data between methods of an {@link EmbeddingStoreListener}
 * or between multiple {@link EmbeddingStoreListener}s.
 */
public class EmbeddingStoreErrorContext<Embedded> {

    private final Throwable error;
    private final EmbeddingStoreOperation operation;
    private final EmbeddingStore<Embedded> embeddingStore;
    private final Map<Object, Object> attributes;

    private final EmbeddingStoreRequestContext<Embedded> requestContext;

    public EmbeddingStoreErrorContext(
            Throwable error,
            EmbeddingStoreOperation operation,
            EmbeddingStore<Embedded> embeddingStore,
            Map<Object, Object> attributes,
            EmbeddingStoreRequestContext<Embedded> requestContext) {
        this.error = ensureNotNull(error, "error");
        this.operation = ensureNotNull(operation, "operation");
        this.embeddingStore = ensureNotNull(embeddingStore, "embeddingStore");
        this.attributes = ensureNotNull(attributes, "attributes");
        this.requestContext = ensureNotNull(requestContext, "requestContext");
    }

    /**
     * @return The error that occurred.
     */
    public Throwable error() {
        return error;
    }

    public EmbeddingStoreOperation operation() {
        return operation;
    }

    public EmbeddingStore<Embedded> embeddingStore() {
        return embeddingStore;
    }

    /**
     * @return The corresponding request context.
     */
    public EmbeddingStoreRequestContext<Embedded> requestContext() {
        return requestContext;
    }

    /**
     * @return The attributes map. It can be used to pass data between methods of an {@link EmbeddingStoreListener}
     * or between multiple {@link EmbeddingStoreListener}s.
     */
    public Map<Object, Object> attributes() {
        return attributes;
    }
}
