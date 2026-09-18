package dev.langchain4j.model.openai;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.embedding.request.DefaultEmbeddingRequestParameters;
import dev.langchain4j.model.embedding.request.EmbeddingParameter;
import dev.langchain4j.model.embedding.request.EmbeddingRequestParameters;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * OpenAI-specific {@link dev.langchain4j.model.embedding.request.EmbeddingRequestParameters}, adding the
 * parameters supported by the OpenAI embeddings API on top of the common {@code modelName}/{@code dimensions}.
 * <p>
 * The {@link #CUSTOM_PARAMETERS} map is the passthrough for provider extensions that ride on the OpenAI wire
 * format but are not first-class OpenAI parameters — most notably NVIDIA's {@code input_type}, which enables
 * asymmetric query-vs-passage encoding on a per-call basis.
 *
 * @since 1.18.0
 */
@Experimental
public class OpenAiEmbeddingRequestParameters extends DefaultEmbeddingRequestParameters {

    public static final EmbeddingParameter<String> USER = new EmbeddingParameter<>("openai.user", String.class);

    public static final EmbeddingParameter<String> ENCODING_FORMAT =
            new EmbeddingParameter<>("openai.encodingFormat", String.class);

    @SuppressWarnings("rawtypes")
    public static final EmbeddingParameter<Map> CUSTOM_PARAMETERS =
            new EmbeddingParameter<>("openai.customParameters", Map.class);

    protected OpenAiEmbeddingRequestParameters(Builder builder) {
        super(builder);
    }

    public String user() {
        return parameter(USER);
    }

    public String encodingFormat() {
        return parameter(ENCODING_FORMAT);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> customParameters() {
        return parameter(CUSTOM_PARAMETERS);
    }

    @Override
    public OpenAiEmbeddingRequestParameters overrideWith(EmbeddingRequestParameters that) {
        if (that == null || that.presentParameters().isEmpty()) {
            return this;
        }
        return OpenAiEmbeddingRequestParameters.builder()
                .overrideWith(this)
                .overrideWith(that)
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends DefaultEmbeddingRequestParameters.Builder<Builder> {

        public Builder user(String user) {
            return set(USER, user);
        }

        public Builder encodingFormat(String encodingFormat) {
            return set(ENCODING_FORMAT, encodingFormat);
        }

        public Builder customParameters(Map<String, Object> customParameters) {
            return set(CUSTOM_PARAMETERS, customParameters);
        }

        /**
         * Adds a single custom parameter, merging it into any previously set {@link #CUSTOM_PARAMETERS} map.
         */
        @SuppressWarnings("unchecked")
        public Builder customParameter(String name, Object value) {
            Map<String, Object> current = (Map<String, Object>) values.get(CUSTOM_PARAMETERS);
            Map<String, Object> merged = current == null ? new LinkedHashMap<>() : new LinkedHashMap<>(current);
            merged.put(name, value);
            return set(CUSTOM_PARAMETERS, merged);
        }

        @Override
        public OpenAiEmbeddingRequestParameters build() {
            return new OpenAiEmbeddingRequestParameters(this);
        }
    }
}
