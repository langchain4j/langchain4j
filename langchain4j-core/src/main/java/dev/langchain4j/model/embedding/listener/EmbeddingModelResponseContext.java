package dev.langchain4j.model.embedding.listener;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import java.util.List;
import java.util.Map;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * The embedding model response context.
 * It contains the {@link Response}, corresponding input, the {@link EmbeddingModel} and attributes.
 * The attributes can be used to pass data between methods of an {@link EmbeddingModelListener}
 * or between multiple {@link EmbeddingModelListener}s.
 */
public class EmbeddingModelResponseContext {

    private final Response<List<Embedding>> response;
    private final List<TextSegment> textSegments;
    private final EmbeddingModel embeddingModel;
    private final Map<Object, Object> attributes;

    public EmbeddingModelResponseContext(
            Response<List<Embedding>> response,
            List<TextSegment> textSegments,
            EmbeddingModel embeddingModel,
            Map<Object, Object> attributes) {
        this.response = ensureNotNull(response, "response");
        this.textSegments = ensureNotNull(textSegments, "textSegments");
        this.embeddingModel = ensureNotNull(embeddingModel, "embeddingModel");
        this.attributes = ensureNotNull(attributes, "attributes");
    }

    public Response<List<Embedding>> response() {
        return response;
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



