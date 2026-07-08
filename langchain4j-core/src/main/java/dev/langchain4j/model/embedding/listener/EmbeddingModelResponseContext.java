package dev.langchain4j.model.embedding.listener;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.request.EmbeddingRequest;
import dev.langchain4j.model.embedding.response.EmbeddingResponse;
import dev.langchain4j.model.output.Response;
import java.util.List;
import java.util.Map;

/**
 * The embedding model response context.
 * It contains the {@link EmbeddingRequest}, the {@link EmbeddingResponse}, the {@link EmbeddingModel}, the
 * {@link ModelProvider} and attributes. The attributes can be used to pass data between methods of an
 * {@link EmbeddingModelListener} or between multiple {@link EmbeddingModelListener}s.
 * <p>
 * Prefer {@link #embeddingRequest()} and {@link #embeddingResponse()} (which expose per-call parameters and
 * multimodal inputs). The legacy {@link #textSegments()} and {@link #response()} accessors are retained for
 * backward compatibility and will be deprecated and removed in 2.0.
 *
 * @since 1.11.0
 */
@Experimental
public class EmbeddingModelResponseContext {

    private final EmbeddingRequest embeddingRequest;
    private final EmbeddingResponse embeddingResponse;
    private final EmbeddingModel embeddingModel;
    private final ModelProvider modelProvider;
    private final Map<Object, Object> attributes;

    // Legacy fields, retained for backward compatibility; to be removed in 2.0.
    private final Response<List<Embedding>> response;
    private final List<TextSegment> textSegments;

    public EmbeddingModelResponseContext(Builder builder) {
        this.embeddingRequest = builder.embeddingRequest;
        this.embeddingResponse = builder.embeddingResponse;
        this.embeddingModel = ensureNotNull(builder.embeddingModel, "embeddingModel");
        this.modelProvider = builder.modelProvider;
        this.attributes = ensureNotNull(builder.attributes, "attributes");
        this.response = ensureNotNull(builder.response, "response");
        this.textSegments = copy(ensureNotNull(builder.textSegments, "textSegments"));
    }

    /**
     * @return the full {@link EmbeddingRequest} (including per-call parameters and multimodal inputs), or
     * {@code null} when the embedding was triggered via a legacy convenience method.
     */
    public EmbeddingRequest embeddingRequest() {
        return embeddingRequest;
    }

    /**
     * @return the {@link EmbeddingResponse} (including the response metadata), or {@code null} when the
     * embedding was triggered via a legacy convenience method.
     */
    public EmbeddingResponse embeddingResponse() {
        return embeddingResponse;
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
     * Legacy accessor; prefer {@link #embeddingResponse()}. Will be deprecated and removed in 2.0.
     */
    public Response<List<Embedding>> response() {
        return response;
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
     * Builder for {@link EmbeddingModelResponseContext}.
     *
     * @since 1.11.0
     */
    @Experimental
    public static class Builder {

        private EmbeddingRequest embeddingRequest;
        private EmbeddingResponse embeddingResponse;
        private EmbeddingModel embeddingModel;
        private ModelProvider modelProvider;
        private Map<Object, Object> attributes;
        private Response<List<Embedding>> response;
        private List<TextSegment> textSegments;

        Builder() {}

        public Builder embeddingRequest(EmbeddingRequest embeddingRequest) {
            this.embeddingRequest = embeddingRequest;
            return this;
        }

        public Builder embeddingResponse(EmbeddingResponse embeddingResponse) {
            this.embeddingResponse = embeddingResponse;
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
         * Legacy; prefer {@link #embeddingResponse(EmbeddingResponse)}. Will be removed in 2.0.
         */
        public Builder response(Response<List<Embedding>> response) {
            this.response = response;
            return this;
        }

        /**
         * Legacy; prefer {@link #embeddingRequest(EmbeddingRequest)}. Will be removed in 2.0.
         */
        public Builder textSegments(List<TextSegment> textSegments) {
            this.textSegments = textSegments;
            return this;
        }

        public EmbeddingModelResponseContext build() {
            return new EmbeddingModelResponseContext(this);
        }
    }
}
