package dev.langchain4j.model.embedding.listener;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import java.util.List;
import java.util.Map;

/**
 * The embedding model error context.
 * It contains the error, corresponding input, the {@link EmbeddingModel} and attributes.
 * The attributes can be used to pass data between methods of an {@link EmbeddingModelListener}
 * or between multiple {@link EmbeddingModelListener}s.
 *
 * @since 1.11.0
 */
@Experimental
public class EmbeddingModelErrorContext {

    private final Throwable error;
    private final List<TextSegment> textSegments;
    private final EmbeddingModel embeddingModel;
    private final Map<Object, Object> attributes;

    public EmbeddingModelErrorContext(Builder builder) {
        this.error = ensureNotNull(builder.error, "error");
        this.textSegments = copy(ensureNotNull(builder.textSegments, "textSegments"));
        this.embeddingModel = ensureNotNull(builder.embeddingModel, "embeddingModel");
        this.attributes = ensureNotNull(builder.attributes, "attributes");
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link EmbeddingModelErrorContext}.
     *
     * @since 1.11.0
     */
    @Experimental
    public static class Builder {

        private Throwable error;
        private List<TextSegment> textSegments;
        private EmbeddingModel embeddingModel;
        private Map<Object, Object> attributes;

        Builder() {}

        public Builder error(Throwable error) {
            this.error = error;
            return this;
        }

        public Builder textSegments(List<TextSegment> textSegments) {
            this.textSegments = textSegments;
            return this;
        }

        public Builder embeddingModel(EmbeddingModel embeddingModel) {
            this.embeddingModel = embeddingModel;
            return this;
        }

        public Builder attributes(Map<Object, Object> attributes) {
            this.attributes = attributes;
            return this;
        }

        public EmbeddingModelErrorContext build() {
            return new EmbeddingModelErrorContext(this);
        }
    }

    /**
     * @return The error that occurred.
     */
    public Throwable error() {
        return error;
    }

    public List<TextSegment> textSegments() {
        return textSegments;
    }

    public EmbeddingModel embeddingModel() {
        return embeddingModel;
    }

    /**
     * @return The attributes map. It can be used to pass data between methods of an {@link EmbeddingModelListener}
     * or between multiple {@link EmbeddingModelListener}s.
     */
    public Map<Object, Object> attributes() {
        return attributes;
    }
}
