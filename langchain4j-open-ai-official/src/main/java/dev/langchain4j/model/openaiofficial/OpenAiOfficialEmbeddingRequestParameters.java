package dev.langchain4j.model.openaiofficial;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.embedding.request.DefaultEmbeddingRequestParameters;
import dev.langchain4j.model.embedding.request.EmbeddingParameter;
import dev.langchain4j.model.embedding.request.EmbeddingRequestParameters;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * OpenAI-specific {@link dev.langchain4j.model.embedding.request.EmbeddingRequestParameters}, adding the
 * parameters accepted by the OpenAI embeddings API on top of the common {@code modelName}/{@code dimensions}.
 * <p>
 * {@link #USER} is a stable identifier for the end user the call is made on behalf of, sent as the request's
 * {@code user} field.
 * <p>
 * {@link #ENCODING_FORMAT} selects how the embeddings are encoded in the response. The SDK requests
 * {@code "base64"} when nothing is set and decodes it, so the embeddings handed to the caller are the same
 * whichever value is used and only the size of the response changes; setting {@code "float"} asks for the
 * plain JSON numbers instead.
 * <p>
 * {@link #CUSTOM_PARAMETERS} is the passthrough for provider extensions that ride on the OpenAI wire format
 * but are not first-class OpenAI parameters; each entry is sent as an additional property of the request body.
 *
 * @since 1.21.0
 */
@Experimental
public class OpenAiOfficialEmbeddingRequestParameters extends DefaultEmbeddingRequestParameters {

    public static final EmbeddingParameter<String> USER = new EmbeddingParameter<>("openaiofficial.user", String.class);

    public static final EmbeddingParameter<String> ENCODING_FORMAT =
            new EmbeddingParameter<>("openaiofficial.encodingFormat", String.class);

    @SuppressWarnings("rawtypes")
    public static final EmbeddingParameter<Map> CUSTOM_PARAMETERS =
            new EmbeddingParameter<>("openaiofficial.customParameters", Map.class);

    protected OpenAiOfficialEmbeddingRequestParameters(Builder builder) {
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
    public OpenAiOfficialEmbeddingRequestParameters overrideWith(EmbeddingRequestParameters that) {
        if (that == null || that.presentParameters().isEmpty()) {
            return this;
        }
        return OpenAiOfficialEmbeddingRequestParameters.builder()
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
        public OpenAiOfficialEmbeddingRequestParameters build() {
            return new OpenAiOfficialEmbeddingRequestParameters(this);
        }
    }
}
