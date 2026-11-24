package dev.langchain4j.store.embedding.listener;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
    private final UUID invocationId;

    /**
     * @deprecated use {@link #EmbeddingStoreRequestContext(EmbeddingStore, Map, UUID)} instead
     */
    @Deprecated(since = "1.24.0")
    protected EmbeddingStoreRequestContext(EmbeddingStore<Embedded> embeddingStore, Map<Object, Object> attributes) {
        this(embeddingStore, attributes, null);
    }

    /**
     * @since 1.24.0
     */
    protected EmbeddingStoreRequestContext(
            EmbeddingStore<Embedded> embeddingStore, Map<Object, Object> attributes, UUID invocationId) {
        this.embeddingStore = ensureNotNull(embeddingStore, "embeddingStore");
        this.attributes = ensureNotNull(attributes, "attributes");
        this.invocationId = invocationId;
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
     * @return the id of the AI Service invocation in which this call is made (the calls made by its tools, guardrails,
     * retrieval, moderation, chat memory, ...), otherwise {@code null}, for example when it is called outside of an AI
     * Service.
     * It is the {@link InvocationContext#invocationId()} of the AI Service invocation, not an id of this call.
     * @since 1.24.0
     */
    public UUID invocationId() {
        return invocationId;
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

        /**
         * @deprecated use {@link #builder()} instead
         */
        @Deprecated(since = "1.24.0")
        public Add(EmbeddingStore<Embedded> embeddingStore, Map<Object, Object> attributes, Embedding embedding) {
            this(Add.<Embedded>builder()
                    .embeddingStore(embeddingStore)
                    .attributes(attributes)
                    .embedding(embedding));
        }

        /**
         * @deprecated use {@link #builder()} instead
         */
        @Deprecated(since = "1.24.0")
        public Add(
                EmbeddingStore<Embedded> embeddingStore,
                Map<Object, Object> attributes,
                String id,
                Embedding embedding) {
            this(Add.<Embedded>builder()
                    .embeddingStore(embeddingStore)
                    .attributes(attributes)
                    .id(id)
                    .embedding(embedding));
        }

        /**
         * @deprecated use {@link #builder()} instead
         */
        @Deprecated(since = "1.24.0")
        public Add(
                EmbeddingStore<Embedded> embeddingStore,
                Map<Object, Object> attributes,
                String id,
                Embedding embedding,
                Embedded embedded) {
            this(Add.<Embedded>builder()
                    .embeddingStore(embeddingStore)
                    .attributes(attributes)
                    .id(id)
                    .embedding(embedding)
                    .embedded(embedded));
        }
        private Add(Builder<Embedded> builder) {
            super(builder.embeddingStore, builder.attributes, builder.invocationId);
            this.id = builder.id;
            this.embedding = builder.embedding;
            this.embedded = builder.embedded;
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

            private EmbeddingStore<Embedded> embeddingStore;
            private Map<Object, Object> attributes;
            private UUID invocationId;
            private String id;
            private Embedding embedding;
            private Embedded embedded;

            Builder() {}

            /**
             * Required: {@link #build()} fails if it is not set.
             */
            public Builder<Embedded> embeddingStore(EmbeddingStore<Embedded> embeddingStore) {
                this.embeddingStore = embeddingStore;
                return this;
            }

            /**
             * Required: {@link #build()} fails if it is not set.
             */
            public Builder<Embedded> attributes(Map<Object, Object> attributes) {
                this.attributes = attributes;
                return this;
            }

            /**
             * Sets the id of the AI Service invocation in which the call is made, see {@link #invocationId()} of the
             * context. LangChain4j sets it for the listeners; set it yourself only when you create the context yourself.
             *
             * @since 1.24.0
             */
            @Experimental
            public Builder<Embedded> invocationId(UUID invocationId) {
                this.invocationId = invocationId;
                return this;
            }

            public Builder<Embedded> id(String id) {
                this.id = id;
                return this;
            }

            public Builder<Embedded> embedding(Embedding embedding) {
                this.embedding = embedding;
                return this;
            }

            public Builder<Embedded> embedded(Embedded embedded) {
                this.embedded = embedded;
                return this;
            }

            public Add<Embedded> build() {
                return new Add<>(this);
            }
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

        /**
         * @deprecated use {@link #builder()} instead
         */
        @Deprecated(since = "1.24.0")
        public AddAll(
                EmbeddingStore<Embedded> embeddingStore,
                Map<Object, Object> attributes,
                List<String> ids,
                List<Embedding> embeddings,
                List<Embedded> embeddedList) {
            this(AddAll.<Embedded>builder()
                    .embeddingStore(embeddingStore)
                    .attributes(attributes)
                    .ids(ids)
                    .embeddings(embeddings)
                    .embeddedList(embeddedList));
        }
        private AddAll(Builder<Embedded> builder) {
            super(builder.embeddingStore, builder.attributes, builder.invocationId);
            this.ids = copy(builder.ids);
            this.embeddings = copy(builder.embeddings);
            this.embeddedList = copy(builder.embeddedList);
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

            private EmbeddingStore<Embedded> embeddingStore;
            private Map<Object, Object> attributes;
            private UUID invocationId;
            private List<String> ids;
            private List<Embedding> embeddings;
            private List<Embedded> embeddedList;

            Builder() {}

            /**
             * Required: {@link #build()} fails if it is not set.
             */
            public Builder<Embedded> embeddingStore(EmbeddingStore<Embedded> embeddingStore) {
                this.embeddingStore = embeddingStore;
                return this;
            }

            /**
             * Required: {@link #build()} fails if it is not set.
             */
            public Builder<Embedded> attributes(Map<Object, Object> attributes) {
                this.attributes = attributes;
                return this;
            }

            /**
             * Sets the id of the AI Service invocation in which the call is made, see {@link #invocationId()} of the
             * context. LangChain4j sets it for the listeners; set it yourself only when you create the context yourself.
             *
             * @since 1.24.0
             */
            @Experimental
            public Builder<Embedded> invocationId(UUID invocationId) {
                this.invocationId = invocationId;
                return this;
            }

            public Builder<Embedded> ids(List<String> ids) {
                this.ids = ids;
                return this;
            }

            public Builder<Embedded> embeddings(List<Embedding> embeddings) {
                this.embeddings = embeddings;
                return this;
            }

            public Builder<Embedded> embeddedList(List<Embedded> embeddedList) {
                this.embeddedList = embeddedList;
                return this;
            }

            public AddAll<Embedded> build() {
                return new AddAll<>(this);
            }
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

        /**
         * @deprecated use {@link #builder()} instead
         */
        @Deprecated(since = "1.24.0")
        public Search(
                EmbeddingStore<Embedded> embeddingStore,
                Map<Object, Object> attributes,
                EmbeddingSearchRequest searchRequest) {
            this(Search.<Embedded>builder()
                    .embeddingStore(embeddingStore)
                    .attributes(attributes)
                    .searchRequest(searchRequest));
        }
        private Search(Builder<Embedded> builder) {
            super(builder.embeddingStore, builder.attributes, builder.invocationId);
            this.searchRequest = builder.searchRequest;
        }

        /**
         * @return The search request for {@code search(...)}.
         */
        public EmbeddingSearchRequest searchRequest() {
            return searchRequest;
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

            private EmbeddingStore<Embedded> embeddingStore;
            private Map<Object, Object> attributes;
            private UUID invocationId;
            private EmbeddingSearchRequest searchRequest;

            Builder() {}

            /**
             * Required: {@link #build()} fails if it is not set.
             */
            public Builder<Embedded> embeddingStore(EmbeddingStore<Embedded> embeddingStore) {
                this.embeddingStore = embeddingStore;
                return this;
            }

            /**
             * Required: {@link #build()} fails if it is not set.
             */
            public Builder<Embedded> attributes(Map<Object, Object> attributes) {
                this.attributes = attributes;
                return this;
            }

            /**
             * Sets the id of the AI Service invocation in which the call is made, see {@link #invocationId()} of the
             * context. LangChain4j sets it for the listeners; set it yourself only when you create the context yourself.
             *
             * @since 1.24.0
             */
            @Experimental
            public Builder<Embedded> invocationId(UUID invocationId) {
                this.invocationId = invocationId;
                return this;
            }

            public Builder<Embedded> searchRequest(EmbeddingSearchRequest searchRequest) {
                this.searchRequest = searchRequest;
                return this;
            }

            public Search<Embedded> build() {
                return new Search<>(this);
            }
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

        /**
         * @deprecated use {@link #builder()} instead
         */
        @Deprecated(since = "1.24.0")
        public Remove(EmbeddingStore<Embedded> embeddingStore, Map<Object, Object> attributes, String id) {
            this(Remove.<Embedded>builder()
                    .embeddingStore(embeddingStore)
                    .attributes(attributes)
                    .id(id));
        }
        private Remove(Builder<Embedded> builder) {
            super(builder.embeddingStore, builder.attributes, builder.invocationId);
            this.id = builder.id;
        }

        /**
         * @return The ID argument for operations like {@code remove(String)}.
         */
        public String id() {
            return id;
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

            private EmbeddingStore<Embedded> embeddingStore;
            private Map<Object, Object> attributes;
            private UUID invocationId;
            private String id;

            Builder() {}

            /**
             * Required: {@link #build()} fails if it is not set.
             */
            public Builder<Embedded> embeddingStore(EmbeddingStore<Embedded> embeddingStore) {
                this.embeddingStore = embeddingStore;
                return this;
            }

            /**
             * Required: {@link #build()} fails if it is not set.
             */
            public Builder<Embedded> attributes(Map<Object, Object> attributes) {
                this.attributes = attributes;
                return this;
            }

            /**
             * Sets the id of the AI Service invocation in which the call is made, see {@link #invocationId()} of the
             * context. LangChain4j sets it for the listeners; set it yourself only when you create the context yourself.
             *
             * @since 1.24.0
             */
            @Experimental
            public Builder<Embedded> invocationId(UUID invocationId) {
                this.invocationId = invocationId;
                return this;
            }

            public Builder<Embedded> id(String id) {
                this.id = id;
                return this;
            }

            public Remove<Embedded> build() {
                return new Remove<>(this);
            }
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

        /**
         * @deprecated use {@link #builder()} instead
         */
        @Deprecated(since = "1.24.0")
        public RemoveAllIds(EmbeddingStore<Embedded> embeddingStore, Map<Object, Object> attributes, List<String> ids) {
            this(RemoveAllIds.<Embedded>builder()
                    .embeddingStore(embeddingStore)
                    .attributes(attributes)
                    .ids(ids));
        }
        private RemoveAllIds(Builder<Embedded> builder) {
            super(builder.embeddingStore, builder.attributes, builder.invocationId);
            this.ids = copy(builder.ids);
        }

        /**
         * @return The IDs argument for operations like {@code removeAll(ids)}.
         */
        public List<String> ids() {
            return ids;
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

            private EmbeddingStore<Embedded> embeddingStore;
            private Map<Object, Object> attributes;
            private UUID invocationId;
            private List<String> ids;

            Builder() {}

            /**
             * Required: {@link #build()} fails if it is not set.
             */
            public Builder<Embedded> embeddingStore(EmbeddingStore<Embedded> embeddingStore) {
                this.embeddingStore = embeddingStore;
                return this;
            }

            /**
             * Required: {@link #build()} fails if it is not set.
             */
            public Builder<Embedded> attributes(Map<Object, Object> attributes) {
                this.attributes = attributes;
                return this;
            }

            /**
             * Sets the id of the AI Service invocation in which the call is made, see {@link #invocationId()} of the
             * context. LangChain4j sets it for the listeners; set it yourself only when you create the context yourself.
             *
             * @since 1.24.0
             */
            @Experimental
            public Builder<Embedded> invocationId(UUID invocationId) {
                this.invocationId = invocationId;
                return this;
            }

            public Builder<Embedded> ids(List<String> ids) {
                this.ids = ids;
                return this;
            }

            public RemoveAllIds<Embedded> build() {
                return new RemoveAllIds<>(this);
            }
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

        /**
         * @deprecated use {@link #builder()} instead
         */
        @Deprecated(since = "1.24.0")
        public RemoveAllFilter(EmbeddingStore<Embedded> embeddingStore, Map<Object, Object> attributes, Filter filter) {
            this(RemoveAllFilter.<Embedded>builder()
                    .embeddingStore(embeddingStore)
                    .attributes(attributes)
                    .filter(filter));
        }
        private RemoveAllFilter(Builder<Embedded> builder) {
            super(builder.embeddingStore, builder.attributes, builder.invocationId);
            this.filter = builder.filter;
        }

        /**
         * @return The filter argument for {@code removeAll(Filter)}.
         */
        public Filter filter() {
            return filter;
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

            private EmbeddingStore<Embedded> embeddingStore;
            private Map<Object, Object> attributes;
            private UUID invocationId;
            private Filter filter;

            Builder() {}

            /**
             * Required: {@link #build()} fails if it is not set.
             */
            public Builder<Embedded> embeddingStore(EmbeddingStore<Embedded> embeddingStore) {
                this.embeddingStore = embeddingStore;
                return this;
            }

            /**
             * Required: {@link #build()} fails if it is not set.
             */
            public Builder<Embedded> attributes(Map<Object, Object> attributes) {
                this.attributes = attributes;
                return this;
            }

            /**
             * Sets the id of the AI Service invocation in which the call is made, see {@link #invocationId()} of the
             * context. LangChain4j sets it for the listeners; set it yourself only when you create the context yourself.
             *
             * @since 1.24.0
             */
            @Experimental
            public Builder<Embedded> invocationId(UUID invocationId) {
                this.invocationId = invocationId;
                return this;
            }

            public Builder<Embedded> filter(Filter filter) {
                this.filter = filter;
                return this;
            }

            public RemoveAllFilter<Embedded> build() {
                return new RemoveAllFilter<>(this);
            }
        }
    }

    /**
     * The {@code removeAll()} request context.
     *
     * @since 1.11.0
     */
    @Experimental
    public static final class RemoveAll<Embedded> extends EmbeddingStoreRequestContext<Embedded> {

        /**
         * @deprecated use {@link #builder()} instead
         */
        @Deprecated(since = "1.24.0")
        public RemoveAll(EmbeddingStore<Embedded> embeddingStore, Map<Object, Object> attributes) {
            this(RemoveAll.<Embedded>builder()
                    .embeddingStore(embeddingStore)
                    .attributes(attributes));
        }
        private RemoveAll(Builder<Embedded> builder) {
            super(builder.embeddingStore, builder.attributes, builder.invocationId);
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

            private EmbeddingStore<Embedded> embeddingStore;
            private Map<Object, Object> attributes;
            private UUID invocationId;

            Builder() {}

            /**
             * Required: {@link #build()} fails if it is not set.
             */
            public Builder<Embedded> embeddingStore(EmbeddingStore<Embedded> embeddingStore) {
                this.embeddingStore = embeddingStore;
                return this;
            }

            /**
             * Required: {@link #build()} fails if it is not set.
             */
            public Builder<Embedded> attributes(Map<Object, Object> attributes) {
                this.attributes = attributes;
                return this;
            }

            /**
             * Sets the id of the AI Service invocation in which the call is made, see {@link #invocationId()} of the
             * context. LangChain4j sets it for the listeners; set it yourself only when you create the context yourself.
             *
             * @since 1.24.0
             */
            @Experimental
            public Builder<Embedded> invocationId(UUID invocationId) {
                this.invocationId = invocationId;
                return this;
            }

            public RemoveAll<Embedded> build() {
                return new RemoveAll<>(this);
            }
        }
    }
}
