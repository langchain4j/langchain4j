package dev.langchain4j.model.embedding.listener;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.request.EmbeddingRequest;
import java.util.List;
import java.util.Map;

/**
 * The embedding model request context.
 * It contains the {@link EmbeddingRequest}, the {@link EmbeddingModel}, the {@link ModelProvider} and attributes.
 * The attributes can be used to pass data between methods of an {@link EmbeddingModelListener}
 * or between multiple {@link EmbeddingModelListener}s.
 * <p>
 * Prefer {@link #embeddingRequest()} (which exposes per-call parameters and multimodal inputs). The legacy
 * {@link #textSegments()} accessor is retained for backward compatibility and will be deprecated and removed
 * in 2.0.
 *
 * @since 1.11.0
 */
@Experimental
public class EmbeddingModelRequestContext {

    private final EmbeddingRequest embeddingRequest;
    private final EmbeddingModel embeddingModel;
    private final ModelProvider modelProvider;
    private final Map<Object, Object> attributes;

    // Legacy field, retained for backward compatibility; to be removed in 2.0.
    private final List<TextSegment> textSegments;

    public EmbeddingModelRequestContext(Builder builder) {
        this.embeddingRequest = builder.embeddingRequest;
        this.embeddingModel = ensureNotNull(builder.embeddingModel, "embeddingModel");
        this.modelProvider = builder.modelProvider;
        this.attributes = ensureNotNull(builder.attributes, "attributes");
        this.textSegments = copy(ensureNotNull(builder.textSegments, "textSegments"));
    }

    /**
     * @return the full {@link EmbeddingRequest} (including per-call parameters and multimodal inputs), or
     * {@code null} when the embedding was triggered via a legacy convenience method
     * ({@link EmbeddingModel#embed(String)} / {@link EmbeddingModel#embedAll(List)}).
     */
    public EmbeddingRequest embeddingRequest() {
        return embeddingRequest;
    }

    public EmbeddingModel embeddingModel() {
        return embeddingModel;
    }

    /**
     * @return the {@link ModelProvider} of the embedding model, or {@code null} if not set.
     */
    public ModelProvider modelProvider() {
        return modelProvider;
    }

    /**
     * @return The attributes map. It can be used to pass data between methods of an {@link EmbeddingModelListener}
     * or between multiple {@link EmbeddingModelListener}s.
     */
    public Map<Object, Object> attributes() {
        return attributes;
    }

    /**
     * Legacy accessor; prefer {@link #embeddingRequest()} (via {@code embeddingRequest().inputs()}).
     * Will be deprecated and removed in 2.0.
     */
    public List<TextSegment> textSegments() {
        return textSegments;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link EmbeddingModelRequestContext}.
     *
     * @since 1.11.0
     */
    @Experimental
    public static class Builder {

        private EmbeddingRequest embeddingRequest;
        private EmbeddingModel embeddingModel;
        private ModelProvider modelProvider;
        private Map<Object, Object> attributes;
        private List<TextSegment> textSegments;

        Builder() {}

        public Builder embeddingRequest(EmbeddingRequest embeddingRequest) {
            this.embeddingRequest = embeddingRequest;
            return this;
        }

        public Builder embeddingModel(EmbeddingModel embeddingModel) {
            this.embeddingModel = embeddingModel;
            return this;
        }

        public Builder modelProvider(ModelProvider modelProvider) {
            this.modelProvider = modelProvider;
            return this;
        }

        public Builder attributes(Map<Object, Object> attributes) {
            this.attributes = attributes;
            return this;
        }

        /**
         * Legacy; prefer {@link #embeddingRequest(EmbeddingRequest)}. Will be removed in 2.0.
         */
        public Builder textSegments(List<TextSegment> textSegments) {
            this.textSegments = textSegments;
            return this;
        }

        public EmbeddingModelRequestContext build() {
            return new EmbeddingModelRequestContext(this);
        }
    }
}
