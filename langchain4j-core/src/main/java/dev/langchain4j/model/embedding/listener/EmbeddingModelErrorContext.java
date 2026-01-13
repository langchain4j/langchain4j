package dev.langchain4j.model.embedding.listener;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import java.util.List;
import java.util.Map;

/**
 * The embedding model error context.
 * It contains the error, corresponding input, the {@link EmbeddingModel} and attributes.
 * The attributes can be used to pass data between methods of an {@link EmbeddingModelListener}
 * or between multiple {@link EmbeddingModelListener}s.
 */
public class EmbeddingModelErrorContext {

    private final Throwable error;
    private final List<TextSegment> textSegments;
    private final EmbeddingModel embeddingModel;
    private final Map<Object, Object> attributes;

    public EmbeddingModelErrorContext(
            Throwable error,
            List<TextSegment> textSegments,
            EmbeddingModel embeddingModel,
            Map<Object, Object> attributes) {
        this.error = ensureNotNull(error, "error");
        this.textSegments = ensureNotNull(textSegments, "textSegments");
        this.embeddingModel = ensureNotNull(embeddingModel, "embeddingModel");
        this.attributes = ensureNotNull(attributes, "attributes");
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
