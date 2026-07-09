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
 * The context passed to {@link EmbeddingModelListener#onError(EmbeddingModelErrorContext)} when an embedding
 * call fails. It gives access to the {@link Throwable} that was thrown, the {@link EmbeddingRequest} that was
 * being embedded, the {@link EmbeddingModel}, the {@link ModelProvider}, and the {@code attributes} map shared
 * with the request callback of the same listener.
 *
 * @since 1.11.0
 */
@Experimental
public class EmbeddingModelErrorContext {

    private final Throwable error;
    private final EmbeddingRequest embeddingRequest;
    private final EmbeddingModel embeddingModel;
    private final ModelProvider modelProvider;
    private final Map<Object, Object> attributes;

    private final List<TextSegment> textSegments;

    public EmbeddingModelErrorContext(Builder builder) {
        this.error = ensureNotNull(builder.error, "error");
        this.embeddingRequest = builder.embeddingRequest;
        this.embeddingModel = ensureNotNull(builder.embeddingModel, "embeddingModel");
        this.modelProvider = builder.modelProvider;
        this.attributes = ensureNotNull(builder.attributes, "attributes");
        this.textSegments = copy(ensureNotNull(builder.textSegments, "textSegments"));
    }

    /**
     * @return The error that occurred.
     */
    public Throwable error() {
        return error;
    }

    /**
     * @return the {@link EmbeddingRequest} that was being embedded, including its per-call parameters and
     * multimodal inputs. It is {@code null} when the embedding was triggered via one of the
     * {@link EmbeddingModel#embed(String)} / {@link EmbeddingModel#embedAll(List)} convenience methods.
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
     * @return the text of each input that was being embedded. For the full request, including per-call
     * parameters and multimodal inputs, use {@link #embeddingRequest()}.
     */
    public List<TextSegment> textSegments() {
        return textSegments;
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
        private EmbeddingRequest embeddingRequest;
        private EmbeddingModel embeddingModel;
        private ModelProvider modelProvider;
        private Map<Object, Object> attributes;
        private List<TextSegment> textSegments;

        Builder() {}

        public Builder error(Throwable error) {
            this.error = error;
            return this;
        }

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

        public Builder textSegments(List<TextSegment> textSegments) {
            this.textSegments = textSegments;
            return this;
        }

        public EmbeddingModelErrorContext build() {
            return new EmbeddingModelErrorContext(this);
        }
    }
}
