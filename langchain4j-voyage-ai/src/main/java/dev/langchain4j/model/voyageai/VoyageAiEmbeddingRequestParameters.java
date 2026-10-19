package dev.langchain4j.model.voyageai;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.embedding.request.DefaultEmbeddingRequestParameters;
import dev.langchain4j.model.embedding.request.EmbeddingParameter;
import dev.langchain4j.model.embedding.request.EmbeddingRequestParameters;

/**
 * Voyage AI-specific {@link dev.langchain4j.model.embedding.request.EmbeddingRequestParameters}, adding the
 * parameters accepted by the Voyage AI embeddings API on top of the common {@code inputType}.
 * <p>
 * {@link #TRUNCATION} controls what happens to an input that is longer than the model's context: it is
 * truncated ({@code true}, the default on Voyage's side) or the call fails ({@code false}).
 * <p>
 * {@link #ENCODING_FORMAT} ({@code "base64"}) only asks Voyage to compress the embeddings in the response;
 * the embeddings handed back to the caller are the same either way. It is carried only by the text
 * embeddings request - Voyage's multimodal endpoint has an equivalent {@code output_encoding} field, which
 * this integration does not send - so a multimodal model does not declare it in
 * {@link dev.langchain4j.model.embedding.EmbeddingModel#supportedParameters()} and rejects it rather than
 * accepting a value that would be dropped.
 *
 * @since 1.21.0
 */
@Experimental
public class VoyageAiEmbeddingRequestParameters extends DefaultEmbeddingRequestParameters {

    public static final EmbeddingParameter<Boolean> TRUNCATION =
            new EmbeddingParameter<>("voyageai.truncation", Boolean.class);

    public static final EmbeddingParameter<String> ENCODING_FORMAT =
            new EmbeddingParameter<>("voyageai.encodingFormat", String.class);

    protected VoyageAiEmbeddingRequestParameters(Builder builder) {
        super(builder);
    }

    public Boolean truncation() {
        return parameter(TRUNCATION);
    }

    public String encodingFormat() {
        return parameter(ENCODING_FORMAT);
    }

    @Override
    public VoyageAiEmbeddingRequestParameters overrideWith(EmbeddingRequestParameters that) {
        if (that == null || that.presentParameters().isEmpty()) {
            return this;
        }
        return VoyageAiEmbeddingRequestParameters.builder()
                .overrideWith(this)
                .overrideWith(that)
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends DefaultEmbeddingRequestParameters.Builder<Builder> {

        public Builder truncation(Boolean truncation) {
            return set(TRUNCATION, truncation);
        }

        public Builder encodingFormat(String encodingFormat) {
            return set(ENCODING_FORMAT, encodingFormat);
        }

        @Override
        public VoyageAiEmbeddingRequestParameters build() {
            return new VoyageAiEmbeddingRequestParameters(this);
        }
    }
}
