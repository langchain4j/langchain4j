package dev.langchain4j.store.embedding.listener;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import java.util.List;
import java.util.Map;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * The embedding store request context.
 * It contains operation details and attributes.
 * The attributes can be used to pass data between methods of an {@link EmbeddingStoreListener}
 * or between multiple {@link EmbeddingStoreListener}s.
 */
public class EmbeddingStoreRequestContext<Embedded> {

    private final EmbeddingStoreOperation operation;
    private final EmbeddingStore<Embedded> embeddingStore;
    private final Map<Object, Object> attributes;

    private final String id;
    private final List<String> ids;

    private final Embedding embedding;
    private final List<Embedding> embeddings;

    private final Embedded embedded;
    private final List<Embedded> embeddedList;

    private final EmbeddingSearchRequest searchRequest;
    private final Filter filter;

    public EmbeddingStoreRequestContext(
            EmbeddingStoreOperation operation,
            EmbeddingStore<Embedded> embeddingStore,
            Map<Object, Object> attributes,
            String id,
            List<String> ids,
            Embedding embedding,
            List<Embedding> embeddings,
            Embedded embedded,
            List<Embedded> embeddedList,
            EmbeddingSearchRequest searchRequest,
            Filter filter) {
        this.operation = ensureNotNull(operation, "operation");
        this.embeddingStore = ensureNotNull(embeddingStore, "embeddingStore");
        this.attributes = ensureNotNull(attributes, "attributes");
        this.id = id;
        this.ids = ids;
        this.embedding = embedding;
        this.embeddings = embeddings;
        this.embedded = embedded;
        this.embeddedList = embeddedList;
        this.searchRequest = searchRequest;
        this.filter = filter;
    }

    public EmbeddingStoreOperation operation() {
        return operation;
    }

    public EmbeddingStore<Embedded> embeddingStore() {
        return embeddingStore;
    }

    /**
     * @return The attributes map. It can be used to pass data between methods of a {@link EmbeddingStoreListener}
     * or between multiple {@link EmbeddingStoreListener}s.
     */
    public Map<Object, Object> attributes() {
        return attributes;
    }

    /**
     * @return The ID argument for operations like {@code add(String, Embedding)} and {@code remove(String)} (if applicable).
     */
    public String id() {
        return id;
    }

    /**
     * @return The IDs argument for operations like {@code addAll(ids, ...)} and {@code removeAll(ids)} (if applicable).
     */
    public List<String> ids() {
        return ids;
    }

    /**
     * @return The embedding argument for {@code add(...)} operations (if applicable).
     */
    public Embedding embedding() {
        return embedding;
    }

    /**
     * @return The embeddings argument for {@code addAll(...)} operations (if applicable).
     */
    public List<Embedding> embeddings() {
        return embeddings;
    }

    /**
     * @return The original embedded content for {@code add(Embedding, Embedded)} (if applicable).
     */
    public Embedded embedded() {
        return embedded;
    }

    /**
     * @return The list of embedded contents for {@code addAll(..., embedded)} (if applicable).
     */
    public List<Embedded> embeddedList() {
        return embeddedList;
    }

    /**
     * @return The search request for {@code search(...)} (if applicable).
     */
    public EmbeddingSearchRequest searchRequest() {
        return searchRequest;
    }

    /**
     * @return The filter argument for {@code removeAll(Filter)} (if applicable).
     */
    public Filter filter() {
        return filter;
    }
}


