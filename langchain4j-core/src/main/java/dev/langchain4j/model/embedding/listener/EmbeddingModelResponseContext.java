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
 * The context passed to {@link EmbeddingModelListener#onResponse(EmbeddingModelResponseContext)} after a
 * successful embedding call. It gives access to the {@link EmbeddingRequest} that was embedded, the resulting
 * {@link EmbeddingResponse} (the embeddings and response metadata), the {@link EmbeddingModel}, the
 * {@link ModelProvider}, and the {@code attributes} map shared with the request callback of the same listener.
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
     * @return the {@link EmbeddingRequest} that was embedded, including its per-call parameters and multimodal
     * inputs. It is {@code null} when the embedding was triggered via one of the
     * {@link EmbeddingModel#embed(String)} / {@link EmbeddingModel#embedAll(List)} convenience methods. TODO same
     */
    public EmbeddingRequest embeddingRequest() {
        return embeddingRequest;
    }

    /**
     * @return the {@link EmbeddingResponse}, including the embeddings and the response metadata. It is
     * {@code null} when the embedding was triggered via one of the {@link EmbeddingModel#embed(String)} /
     * {@link EmbeddingModel#embedAll(List)} convenience methods.
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
        return modelProvider; // TODO take from embeddingModel.provider()?
    }

    /**
     * @return The attributes map. It can be used to pass data between methods of an {@link EmbeddingModelListener}
     * or between multiple {@link EmbeddingModelListener}s.
     */
    public Map<Object, Object> attributes() {
        return attributes;
    }

    /**
     * @return the embeddings and token usage. For the full response, including all response metadata, use
     * {@link #embeddingResponse()}.
     */
    public Response<List<Embedding>> response() {
        return response;
    }

    /**
     * @return the text of each input that was embedded. For the full request, including per-call parameters and
     * multimodal inputs, use {@link #embeddingRequest()}.
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

        public Builder response(Response<List<Embedding>> response) {
            this.response = response;
            return this;
        }

        public Builder textSegments(List<TextSegment> textSegments) {
            this.textSegments = textSegments;
            return this;
        }

        public EmbeddingModelResponseContext build() {
            return new EmbeddingModelResponseContext(this);
        }
    }
}
