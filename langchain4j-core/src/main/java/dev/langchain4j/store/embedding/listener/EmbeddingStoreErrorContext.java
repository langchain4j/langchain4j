package dev.langchain4j.store.embedding.listener;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.store.embedding.EmbeddingStore;
import java.util.Map;
import java.util.UUID;

/**
 * The embedding store error context.
 * It contains the error, corresponding request details, and attributes.
 * The attributes can be used to pass data between methods of an {@link EmbeddingStoreListener}
 * or between multiple {@link EmbeddingStoreListener}s.
 *
 * @since 1.11.0
 */
@Experimental
public class EmbeddingStoreErrorContext<Embedded> {

    private final Throwable error;
    private final EmbeddingStoreRequestContext<Embedded> requestContext;
    private final Map<Object, Object> attributes;

    /**
     * @deprecated use {@link #builder()} instead
     */
    @Deprecated(since = "1.24.0")
    public EmbeddingStoreErrorContext(
            Throwable error, EmbeddingStoreRequestContext<Embedded> requestContext, Map<Object, Object> attributes) {
        this(EmbeddingStoreErrorContext.<Embedded>builder()
                .error(error)
                .requestContext(requestContext)
                .attributes(attributes));
    }
    private EmbeddingStoreErrorContext(Builder<Embedded> builder) {
        this.error = ensureNotNull(builder.error, "error");
        this.requestContext = ensureNotNull(builder.requestContext, "requestContext");
        this.attributes = ensureNotNull(builder.attributes, "attributes");
    }

    /**
     * @return The error that occurred.
     */
    public Throwable error() {
        return error;
    }

    public EmbeddingStore<Embedded> embeddingStore() {
        return requestContext.embeddingStore();
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

    /**
     * @return the id of the AI Service invocation in which this call is made (the calls made by its tools, guardrails,
     * retrieval, moderation, chat memory, ...), otherwise {@code null}, for example when it is called outside of an AI
     * Service.
     * It is the {@link InvocationContext#invocationId()} of the AI Service invocation, not an id of this call.
     * @since 1.24.0
     */
    public UUID invocationId() {
        return requestContext.invocationId();
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

        private Throwable error;
        private EmbeddingStoreRequestContext<Embedded> requestContext;
        private Map<Object, Object> attributes;

        Builder() {}

        /**
         * Required: {@link #build()} fails if it is not set.
         */
        public Builder<Embedded> error(Throwable error) {
            this.error = error;
            return this;
        }

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

        public EmbeddingStoreErrorContext<Embedded> build() {
            return new EmbeddingStoreErrorContext<>(this);
        }
    }
}
