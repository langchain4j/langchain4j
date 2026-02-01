package dev.langchain4j.store.embedding.listener;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import java.util.List;
import java.util.Map;

/**
 * The embedding store response context.
 * It contains the response details, corresponding request details, and attributes.
 * The attributes can be used to pass data between methods of an {@link EmbeddingStoreListener}
 * or between multiple {@link EmbeddingStoreListener}s.
 *
 * @since 1.11.0
 */
@Experimental
public abstract class EmbeddingStoreResponseContext<Embedded> {

    private final EmbeddingStoreRequestContext<Embedded> requestContext;
    private final Map<Object, Object> attributes;

    protected EmbeddingStoreResponseContext(
            EmbeddingStoreRequestContext<Embedded> requestContext, Map<Object, Object> attributes) {
        this.requestContext = ensureNotNull(requestContext, "requestContext");
        this.attributes = ensureNotNull(attributes, "attributes");
    }

    public EmbeddingStore<Embedded> embeddingStore() {
        return requestContext.embeddingStore();
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
     * The {@code add(...)} response context.
     *
     * @since 1.11.0
     */
    @Experimental
    public static final class Add<Embedded> extends EmbeddingStoreResponseContext<Embedded> {

        private final String returnedId;

        public Add(
                EmbeddingStoreRequestContext<Embedded> requestContext,
                Map<Object, Object> attributes,
                String returnedId) {
            super(requestContext, attributes);
            this.returnedId = returnedId;
        }

        /**
         * @return The returned ID for operations like {@code add(Embedding)} and {@code add(Embedding, Embedded)} (if applicable).
         */
        public String returnedId() {
            return returnedId;
        }
    }

    /**
     * The {@code addAll(...)} response context.
     *
     * @since 1.11.0
     */
    @Experimental
    public static final class AddAll<Embedded> extends EmbeddingStoreResponseContext<Embedded> {

        private final List<String> returnedIds;

        public AddAll(
                EmbeddingStoreRequestContext<Embedded> requestContext,
                Map<Object, Object> attributes,
                List<String> returnedIds) {
            super(requestContext, attributes);
            this.returnedIds = copy(returnedIds);
        }

        /**
         * @return The returned IDs for operations like {@code addAll(List<Embedding>)} and {@code addAll(List<Embedding>, List<Embedded>)} (if applicable).
         */
        public List<String> returnedIds() {
            return returnedIds;
        }
    }

    /**
     * The {@code search(...)} response context.
     *
     * @since 1.11.0
     */
    @Experimental
    public static final class Search<Embedded> extends EmbeddingStoreResponseContext<Embedded> {

        private final EmbeddingSearchResult<Embedded> searchResult;

        public Search(
                EmbeddingStoreRequestContext<Embedded> requestContext,
                Map<Object, Object> attributes,
                EmbeddingSearchResult<Embedded> searchResult) {
            super(requestContext, attributes);
            this.searchResult = searchResult;
        }

        /**
         * @return The search result for {@code search(...)}.
         */
        public EmbeddingSearchResult<Embedded> searchResult() {
            return searchResult;
        }
    }

    /**
     * The {@code remove(String)} response context.
     *
     * @since 1.11.0
     */
    @Experimental
    public static final class Remove<Embedded> extends EmbeddingStoreResponseContext<Embedded> {

        public Remove(EmbeddingStoreRequestContext.Remove<Embedded> requestContext, Map<Object, Object> attributes) {
            super(requestContext, attributes);
        }
    }

    /**
     * The {@code removeAll(ids)} response context.
     *
     * @since 1.11.0
     */
    @Experimental
    public static final class RemoveAllIds<Embedded> extends EmbeddingStoreResponseContext<Embedded> {

        public RemoveAllIds(
                EmbeddingStoreRequestContext.RemoveAllIds<Embedded> requestContext, Map<Object, Object> attributes) {
            super(requestContext, attributes);
        }
    }

    /**
     * The {@code removeAll(Filter)} response context.
     *
     * @since 1.11.0
     */
    @Experimental
    public static final class RemoveAllFilter<Embedded> extends EmbeddingStoreResponseContext<Embedded> {

        public RemoveAllFilter(
                EmbeddingStoreRequestContext.RemoveAllFilter<Embedded> requestContext, Map<Object, Object> attributes) {
            super(requestContext, attributes);
        }
    }

    /**
     * The {@code removeAll()} response context.
     *
     * @since 1.11.0
     */
    @Experimental
    public static final class RemoveAll<Embedded> extends EmbeddingStoreResponseContext<Embedded> {

        public RemoveAll(
                EmbeddingStoreRequestContext.RemoveAll<Embedded> requestContext, Map<Object, Object> attributes) {
            super(requestContext, attributes);
        }
    }
}
