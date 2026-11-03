package dev.langchain4j.store.embedding.listener;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
     * @return the id of the AI Service invocation this call belongs to: the one whose work (a tool, a guardrail,
     * retrieval, ...) makes the call, otherwise {@code null}. It is the {@link InvocationContext#invocationId()} of the
     * AI Service invocation, not an id of this call.
     * @since 1.24.0
     */
    public UUID invocationId() {
        return requestContext.invocationId();
    }

    /**
     * The {@code add(...)} response context.
     *
     * @since 1.11.0
     */
    @Experimental
    public static final class Add<Embedded> extends EmbeddingStoreResponseContext<Embedded> {

        private final String returnedId;

        /**
         * @deprecated use {@link #builder()} instead
         */
        @Deprecated(since = "1.24.0")
        public Add(
                EmbeddingStoreRequestContext<Embedded> requestContext,
                Map<Object, Object> attributes,
                String returnedId) {
            this(Add.<Embedded>builder()
                    .requestContext(requestContext)
                    .attributes(attributes)
                    .returnedId(returnedId));
        }
        private Add(Builder<Embedded> builder) {
            super(builder.requestContext, builder.attributes);
            this.returnedId = builder.returnedId;
        }

        /**
         * @return The returned ID for operations like {@code add(Embedding)} and {@code add(Embedding, Embedded)} (if applicable).
         */
        public String returnedId() {
            return returnedId;
        }

        /**
         * @since 1.24.0
         */
        public static <Embedded> Builder<Embedded> builder() {
            return new Builder<>();
        }

        /**
         * @since 1.24.0
         */
        public static final class Builder<Embedded> {

            private EmbeddingStoreRequestContext<Embedded> requestContext;
            private Map<Object, Object> attributes;
            private String returnedId;

            Builder() {}

            /**
             * Required: {@link #build()} fails if it is not set.
             */
            public Builder<Embedded> requestContext(EmbeddingStoreRequestContext<Embedded> requestContext) {
                this.requestContext = requestContext;
                return this;
            }

            /**
             * Required: {@link #build()} fails if it is not set.
             */
            public Builder<Embedded> attributes(Map<Object, Object> attributes) {
                this.attributes = attributes;
                return this;
            }

            public Builder<Embedded> returnedId(String returnedId) {
                this.returnedId = returnedId;
                return this;
            }

            public Add<Embedded> build() {
                return new Add<>(this);
            }
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

        /**
         * @deprecated use {@link #builder()} instead
         */
        @Deprecated(since = "1.24.0")
        public AddAll(
                EmbeddingStoreRequestContext<Embedded> requestContext,
                Map<Object, Object> attributes,
                List<String> returnedIds) {
            this(AddAll.<Embedded>builder()
                    .requestContext(requestContext)
                    .attributes(attributes)
                    .returnedIds(returnedIds));
        }
        private AddAll(Builder<Embedded> builder) {
            super(builder.requestContext, builder.attributes);
            this.returnedIds = copy(builder.returnedIds);
        }

        /**
         * @return The returned IDs for operations like {@code addAll(List<Embedding>)} and {@code addAll(List<Embedding>, List<Embedded>)} (if applicable).
         */
        public List<String> returnedIds() {
            return returnedIds;
        }

        /**
         * @since 1.24.0
         */
        public static <Embedded> Builder<Embedded> builder() {
            return new Builder<>();
        }

        /**
         * @since 1.24.0
         */
        public static final class Builder<Embedded> {

            private EmbeddingStoreRequestContext<Embedded> requestContext;
            private Map<Object, Object> attributes;
            private List<String> returnedIds;

            Builder() {}

            /**
             * Required: {@link #build()} fails if it is not set.
             */
            public Builder<Embedded> requestContext(EmbeddingStoreRequestContext<Embedded> requestContext) {
                this.requestContext = requestContext;
                return this;
            }

            /**
             * Required: {@link #build()} fails if it is not set.
             */
            public Builder<Embedded> attributes(Map<Object, Object> attributes) {
                this.attributes = attributes;
                return this;
            }

            public Builder<Embedded> returnedIds(List<String> returnedIds) {
                this.returnedIds = returnedIds;
                return this;
            }

            public AddAll<Embedded> build() {
                return new AddAll<>(this);
            }
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

        /**
         * @deprecated use {@link #builder()} instead
         */
        @Deprecated(since = "1.24.0")
        public Search(
                EmbeddingStoreRequestContext<Embedded> requestContext,
                Map<Object, Object> attributes,
                EmbeddingSearchResult<Embedded> searchResult) {
            this(Search.<Embedded>builder()
                    .requestContext(requestContext)
                    .attributes(attributes)
                    .searchResult(searchResult));
        }
        private Search(Builder<Embedded> builder) {
            super(builder.requestContext, builder.attributes);
            this.searchResult = builder.searchResult;
        }

        /**
         * @return The search result for {@code search(...)}.
         */
        public EmbeddingSearchResult<Embedded> searchResult() {
            return searchResult;
        }

        /**
         * @since 1.24.0
         */
        public static <Embedded> Builder<Embedded> builder() {
            return new Builder<>();
        }

        /**
         * @since 1.24.0
         */
        public static final class Builder<Embedded> {

            private EmbeddingStoreRequestContext<Embedded> requestContext;
            private Map<Object, Object> attributes;
            private EmbeddingSearchResult<Embedded> searchResult;

            Builder() {}

            /**
             * Required: {@link #build()} fails if it is not set.
             */
            public Builder<Embedded> requestContext(EmbeddingStoreRequestContext<Embedded> requestContext) {
                this.requestContext = requestContext;
                return this;
            }

            /**
             * Required: {@link #build()} fails if it is not set.
             */
            public Builder<Embedded> attributes(Map<Object, Object> attributes) {
                this.attributes = attributes;
                return this;
            }

            public Builder<Embedded> searchResult(EmbeddingSearchResult<Embedded> searchResult) {
                this.searchResult = searchResult;
                return this;
            }

            public Search<Embedded> build() {
                return new Search<>(this);
            }
        }
    }

    /**
     * The {@code remove(String)} response context.
     *
     * @since 1.11.0
     */
    @Experimental
    public static final class Remove<Embedded> extends EmbeddingStoreResponseContext<Embedded> {

        /**
         * @deprecated use {@link #builder()} instead
         */
        @Deprecated(since = "1.24.0")
        public Remove(EmbeddingStoreRequestContext.Remove<Embedded> requestContext, Map<Object, Object> attributes) {
            this(Remove.<Embedded>builder()
                    .requestContext(requestContext)
                    .attributes(attributes));
        }
        private Remove(Builder<Embedded> builder) {
            super(builder.requestContext, builder.attributes);
        }

        /**
         * @since 1.24.0
         */
        public static <Embedded> Builder<Embedded> builder() {
            return new Builder<>();
        }

        /**
         * @since 1.24.0
         */
        public static final class Builder<Embedded> {

            private EmbeddingStoreRequestContext.Remove<Embedded> requestContext;
            private Map<Object, Object> attributes;

            Builder() {}

            /**
             * Required: {@link #build()} fails if it is not set.
             */
            public Builder<Embedded> requestContext(EmbeddingStoreRequestContext.Remove<Embedded> requestContext) {
                this.requestContext = requestContext;
                return this;
            }

            /**
             * Required: {@link #build()} fails if it is not set.
             */
            public Builder<Embedded> attributes(Map<Object, Object> attributes) {
                this.attributes = attributes;
                return this;
            }

            public Remove<Embedded> build() {
                return new Remove<>(this);
            }
        }
    }

    /**
     * The {@code removeAll(ids)} response context.
     *
     * @since 1.11.0
     */
    @Experimental
    public static final class RemoveAllIds<Embedded> extends EmbeddingStoreResponseContext<Embedded> {

        /**
         * @deprecated use {@link #builder()} instead
         */
        @Deprecated(since = "1.24.0")
        public RemoveAllIds(
                EmbeddingStoreRequestContext.RemoveAllIds<Embedded> requestContext, Map<Object, Object> attributes) {
            this(RemoveAllIds.<Embedded>builder()
                    .requestContext(requestContext)
                    .attributes(attributes));
        }
        private RemoveAllIds(Builder<Embedded> builder) {
            super(builder.requestContext, builder.attributes);
        }

        /**
         * @since 1.24.0
         */
        public static <Embedded> Builder<Embedded> builder() {
            return new Builder<>();
        }

        /**
         * @since 1.24.0
         */
        public static final class Builder<Embedded> {

            private EmbeddingStoreRequestContext.RemoveAllIds<Embedded> requestContext;
            private Map<Object, Object> attributes;

            Builder() {}

            /**
             * Required: {@link #build()} fails if it is not set.
             */
            public Builder<Embedded> requestContext(EmbeddingStoreRequestContext.RemoveAllIds<Embedded> requestContext) {
                this.requestContext = requestContext;
                return this;
            }

            /**
             * Required: {@link #build()} fails if it is not set.
             */
            public Builder<Embedded> attributes(Map<Object, Object> attributes) {
                this.attributes = attributes;
                return this;
            }

            public RemoveAllIds<Embedded> build() {
                return new RemoveAllIds<>(this);
            }
        }
    }

    /**
     * The {@code removeAll(Filter)} response context.
     *
     * @since 1.11.0
     */
    @Experimental
    public static final class RemoveAllFilter<Embedded> extends EmbeddingStoreResponseContext<Embedded> {

        /**
         * @deprecated use {@link #builder()} instead
         */
        @Deprecated(since = "1.24.0")
        public RemoveAllFilter(
                EmbeddingStoreRequestContext.RemoveAllFilter<Embedded> requestContext, Map<Object, Object> attributes) {
            this(RemoveAllFilter.<Embedded>builder()
                    .requestContext(requestContext)
                    .attributes(attributes));
        }
        private RemoveAllFilter(Builder<Embedded> builder) {
            super(builder.requestContext, builder.attributes);
        }

        /**
         * @since 1.24.0
         */
        public static <Embedded> Builder<Embedded> builder() {
            return new Builder<>();
        }

        /**
         * @since 1.24.0
         */
        public static final class Builder<Embedded> {

            private EmbeddingStoreRequestContext.RemoveAllFilter<Embedded> requestContext;
            private Map<Object, Object> attributes;

            Builder() {}

            /**
             * Required: {@link #build()} fails if it is not set.
             */
            public Builder<Embedded> requestContext(EmbeddingStoreRequestContext.RemoveAllFilter<Embedded> requestContext) {
                this.requestContext = requestContext;
                return this;
            }

            /**
             * Required: {@link #build()} fails if it is not set.
             */
            public Builder<Embedded> attributes(Map<Object, Object> attributes) {
                this.attributes = attributes;
                return this;
            }

            public RemoveAllFilter<Embedded> build() {
                return new RemoveAllFilter<>(this);
            }
        }
    }

    /**
     * The {@code removeAll()} response context.
     *
     * @since 1.11.0
     */
    @Experimental
    public static final class RemoveAll<Embedded> extends EmbeddingStoreResponseContext<Embedded> {

        /**
         * @deprecated use {@link #builder()} instead
         */
        @Deprecated(since = "1.24.0")
        public RemoveAll(
                EmbeddingStoreRequestContext.RemoveAll<Embedded> requestContext, Map<Object, Object> attributes) {
            this(RemoveAll.<Embedded>builder()
                    .requestContext(requestContext)
                    .attributes(attributes));
        }
        private RemoveAll(Builder<Embedded> builder) {
            super(builder.requestContext, builder.attributes);
        }

        /**
         * @since 1.24.0
         */
        public static <Embedded> Builder<Embedded> builder() {
            return new Builder<>();
        }

        /**
         * @since 1.24.0
         */
        public static final class Builder<Embedded> {

            private EmbeddingStoreRequestContext.RemoveAll<Embedded> requestContext;
            private Map<Object, Object> attributes;

            Builder() {}

            /**
             * Required: {@link #build()} fails if it is not set.
             */
            public Builder<Embedded> requestContext(EmbeddingStoreRequestContext.RemoveAll<Embedded> requestContext) {
                this.requestContext = requestContext;
                return this;
            }

            /**
             * Required: {@link #build()} fails if it is not set.
             */
            public Builder<Embedded> attributes(Map<Object, Object> attributes) {
                this.attributes = attributes;
                return this;
            }

            public RemoveAll<Embedded> build() {
                return new RemoveAll<>(this);
            }
        }
    }
}
