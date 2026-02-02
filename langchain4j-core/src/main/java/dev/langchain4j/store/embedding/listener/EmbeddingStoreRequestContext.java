package dev.langchain4j.store.embedding.listener;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import java.util.List;
import java.util.Map;

/**
 * The embedding store request context.
 * It contains operation details and attributes.
 * The attributes can be used to pass data between methods of an {@link EmbeddingStoreListener}
 * or between multiple {@link EmbeddingStoreListener}s.
 *
 * @since 1.11.0
 */
@Experimental
public abstract class EmbeddingStoreRequestContext<Embedded> {

    private final EmbeddingStore<Embedded> embeddingStore;
    private final Map<Object, Object> attributes;

    protected EmbeddingStoreRequestContext(EmbeddingStore<Embedded> embeddingStore, Map<Object, Object> attributes) {
        this.embeddingStore = ensureNotNull(embeddingStore, "embeddingStore");
        this.attributes = ensureNotNull(attributes, "attributes");
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
     * The {@code add(...)} request context.
     *
     * @since 1.11.0
     */
    @Experimental
    public static final class Add<Embedded> extends EmbeddingStoreRequestContext<Embedded> {

        private final String id;
        private final Embedding embedding;
        private final Embedded embedded;

        public Add(EmbeddingStore<Embedded> embeddingStore, Map<Object, Object> attributes, Embedding embedding) {
            this(embeddingStore, attributes, null, embedding, null);
        }

        public Add(
                EmbeddingStore<Embedded> embeddingStore,
                Map<Object, Object> attributes,
                String id,
                Embedding embedding) {
            this(embeddingStore, attributes, id, embedding, null);
        }

        public Add(
                EmbeddingStore<Embedded> embeddingStore,
                Map<Object, Object> attributes,
                String id,
                Embedding embedding,
                Embedded embedded) {
            super(embeddingStore, attributes);
            this.id = id;
            this.embedding = embedding;
            this.embedded = embedded;
        }

        /**
         * @return The ID argument for operations like {@code add(String, Embedding)} (if applicable).
         */
        public String id() {
            return id;
        }

        /**
         * @return The embedding argument for {@code add(...)} operations.
         */
        public Embedding embedding() {
            return embedding;
        }

        /**
         * @return The original embedded content for {@code add(Embedding, Embedded)} (if applicable).
         */
        public Embedded embedded() {
            return embedded;
        }
    }

    /**
     * The {@code addAll(...)} request context.
     *
     * @since 1.11.0
     */
    @Experimental
    public static final class AddAll<Embedded> extends EmbeddingStoreRequestContext<Embedded> {

        private final List<String> ids;
        private final List<Embedding> embeddings;
        private final List<Embedded> embeddedList;

        public AddAll(
                EmbeddingStore<Embedded> embeddingStore,
                Map<Object, Object> attributes,
                List<String> ids,
                List<Embedding> embeddings,
                List<Embedded> embeddedList) {
            super(embeddingStore, attributes);
            this.ids = copy(ids);
            this.embeddings = copy(embeddings);
            this.embeddedList = copy(embeddedList);
        }

        /**
         * @return The IDs argument for operations like {@code addAll(ids, ...)} (if applicable).
         */
        public List<String> ids() {
            return ids;
        }

        /**
         * @return The embeddings argument for {@code addAll(...)} operations.
         */
        public List<Embedding> embeddings() {
            return embeddings;
        }

        /**
         * @return The list of embedded contents for {@code addAll(..., embedded)} (if applicable).
         */
        public List<Embedded> embeddedList() {
            return embeddedList;
        }
    }

    /**
     * The {@code search(...)} request context.
     *
     * @since 1.11.0
     */
    @Experimental
    public static final class Search<Embedded> extends EmbeddingStoreRequestContext<Embedded> {

        private final EmbeddingSearchRequest searchRequest;

        public Search(
                EmbeddingStore<Embedded> embeddingStore,
                Map<Object, Object> attributes,
                EmbeddingSearchRequest searchRequest) {
            super(embeddingStore, attributes);
            this.searchRequest = searchRequest;
        }

        /**
         * @return The search request for {@code search(...)}.
         */
        public EmbeddingSearchRequest searchRequest() {
            return searchRequest;
        }
    }

    /**
     * The {@code remove(String)} request context.
     *
     * @since 1.11.0
     */
    @Experimental
    public static final class Remove<Embedded> extends EmbeddingStoreRequestContext<Embedded> {

        private final String id;

        public Remove(EmbeddingStore<Embedded> embeddingStore, Map<Object, Object> attributes, String id) {
            super(embeddingStore, attributes);
            this.id = id;
        }

        /**
         * @return The ID argument for operations like {@code remove(String)}.
         */
        public String id() {
            return id;
        }
    }

    /**
     * The {@code removeAll(ids)} request context.
     *
     * @since 1.11.0
     */
    @Experimental
    public static final class RemoveAllIds<Embedded> extends EmbeddingStoreRequestContext<Embedded> {

        private final List<String> ids;

        public RemoveAllIds(EmbeddingStore<Embedded> embeddingStore, Map<Object, Object> attributes, List<String> ids) {
            super(embeddingStore, attributes);
            this.ids = copy(ids);
        }

        /**
         * @return The IDs argument for operations like {@code removeAll(ids)}.
         */
        public List<String> ids() {
            return ids;
        }
    }

    /**
     * The {@code removeAll(Filter)} request context.
     *
     * @since 1.11.0
     */
    @Experimental
    public static final class RemoveAllFilter<Embedded> extends EmbeddingStoreRequestContext<Embedded> {

        private final Filter filter;

        public RemoveAllFilter(EmbeddingStore<Embedded> embeddingStore, Map<Object, Object> attributes, Filter filter) {
            super(embeddingStore, attributes);
            this.filter = filter;
        }

        /**
         * @return The filter argument for {@code removeAll(Filter)}.
         */
        public Filter filter() {
            return filter;
        }
    }

    /**
     * The {@code removeAll()} request context.
     *
     * @since 1.11.0
     */
    @Experimental
    public static final class RemoveAll<Embedded> extends EmbeddingStoreRequestContext<Embedded> {

        public RemoveAll(EmbeddingStore<Embedded> embeddingStore, Map<Object, Object> attributes) {
            super(embeddingStore, attributes);
        }
    }
}
