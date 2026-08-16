package dev.langchain4j.model.watsonx;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.model.ModelProvider.WATSONX;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import com.ibm.watsonx.ai.gateway.embedding.ModelGatewayEmbeddingParameters;
import com.ibm.watsonx.ai.gateway.embedding.ModelGatewayEmbeddingParameters.EncodingFormat;
import com.ibm.watsonx.ai.gateway.embedding.ModelGatewayEmbeddingResponse;
import com.ibm.watsonx.ai.gateway.embedding.ModelGatewayEmbeddingService;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.listener.EmbeddingModelListener;
import dev.langchain4j.model.embedding.request.EmbeddingInput;
import dev.langchain4j.model.embedding.request.EmbeddingParameter;
import dev.langchain4j.model.embedding.request.EmbeddingRequest;
import dev.langchain4j.model.embedding.request.EmbeddingRequestParameters;
import dev.langchain4j.model.embedding.response.EmbeddingResponse;
import dev.langchain4j.model.output.Response;
import java.util.List;
import java.util.Set;

/**
 * A {@link EmbeddingModel} implementation that integrates the IBM watsonx.ai Model Gateway with LangChain4j.
 * <p>
 * <b>Example usage:</b>
 *
 * <pre>{@code
 * EmbeddingModel embeddingModel = WatsonxGatewayEmbeddingModel.builder()
 *     .baseUrl("https://...") // or use CloudRegion
 *     .apiKey("...")
 *     .modelName("text-embedding-3-small")
 *     .build();
 * }</pre>
 *
 */
public class WatsonxGatewayEmbeddingModel implements EmbeddingModel {

    private final ModelGatewayEmbeddingService modelGatewayEmbeddingService;
    private final ModelGatewayEmbeddingParameters defaultParameters;
    private final EmbeddingRequestParameters defaultRequestParameters;
    private final String modelName;
    private final String encodingFormat;
    private final String user;
    private final List<EmbeddingModelListener> listeners;

    private WatsonxGatewayEmbeddingModel(Builder builder) {

        var serviceBuilder = nonNull(builder.authenticator)
                ? ModelGatewayEmbeddingService.builder().authenticator(builder.authenticator)
                : ModelGatewayEmbeddingService.builder().apiKey(builder.apiKey);

        modelGatewayEmbeddingService = serviceBuilder
                .baseUrl(builder.baseUrl)
                .modelId(builder.modelName)
                .version(builder.version)
                .timeout(builder.timeout)
                .logRequests(builder.logRequests)
                .logResponses(builder.logResponses)
                .httpClient(builder.httpClient)
                .verifySsl(builder.verifySsl)
                .build();

        this.defaultParameters = ModelGatewayEmbeddingParameters.builder()
                .dimensions(builder.dimensions)
                .encodingFormat(builder.encodingFormat)
                .user(builder.user)
                .build();

        this.defaultRequestParameters = EmbeddingRequestParameters.builder()
                .dimensions(builder.dimensions)
                .build();

        this.modelName = builder.modelName;
        this.encodingFormat = builder.encodingFormat;
        this.user = builder.user;
        this.listeners = copy(builder.listeners);
    }

    @Override
    public List<EmbeddingModelListener> listeners() {
        return listeners;
    }

    @Override
    public ModelProvider provider() {
        return WATSONX;
    }

    @Override
    public String modelName() {
        return this.modelName;
    }

    @Override
    public EmbeddingRequestParameters defaultRequestParameters() {
        return defaultRequestParameters;
    }

    @Override
    public Set<EmbeddingParameter<?>> supportedParameters() {
        return Set.of(EmbeddingRequestParameters.DIMENSIONS);
    }

    @Override
    public EmbeddingResponse doEmbed(EmbeddingRequest request) {

        List<String> inputs =
                request.inputs().stream().map(EmbeddingInput::text).toList();

        ModelGatewayEmbeddingParameters parameters = ModelGatewayEmbeddingParameters.builder()
                .dimensions(request.parameters().dimensions())
                .encodingFormat(encodingFormat)
                .user(user)
                .build();

        return embed(inputs, parameters);
    }

    /**
     * Embeds the text content of a list of TextSegment using the specified {@link ModelGatewayEmbeddingParameters}.
     *
     * <p>The given parameters replace the ones set on the builder, they are not merged with them.
     *
     * @param textSegments the text segments to embed.
     * @param parameters the embedding parameters to use, or {@code null} to use the ones set on the builder.
     * @return the embeddings.
     */
    public Response<List<Embedding>> embedAll(
            List<TextSegment> textSegments, ModelGatewayEmbeddingParameters parameters) {

        if (isNull(textSegments) || textSegments.isEmpty()) return Response.from(List.of());

        List<String> inputs = textSegments.stream().map(TextSegment::text).toList();
        EmbeddingResponse response = embed(inputs, getOrDefault(parameters, defaultParameters));

        return Response.from(response.embeddings(), response.metadata().tokenUsage());
    }

    private EmbeddingResponse embed(List<String> inputs, ModelGatewayEmbeddingParameters parameters) {

        ModelGatewayEmbeddingResponse response = WatsonxExceptionMapper.INSTANCE.withExceptionMapper(
                () -> modelGatewayEmbeddingService.embed(inputs, parameters));

        return Converter.toEmbeddingResponse(response, modelName);
    }

    /**
     * Returns a new {@link Builder} instance.
     * <p>
     * <b>Example usage:</b>
     *
     * <pre>{@code
     * EmbeddingModel embeddingModel = WatsonxGatewayEmbeddingModel.builder()
     *     .baseUrl("https://...") // or use CloudRegion
     *     .apiKey("...")
     *     .modelName("text-embedding-3-small")
     *     .build();
     * }</pre>
     *
     * @return {@link Builder} instance.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder class for constructing {@link WatsonxGatewayEmbeddingModel} instances with configurable parameters.
     */
    public static class Builder extends WatsonxConnectionBuilder<Builder> {
        private String modelName;
        private Integer dimensions;
        private String encodingFormat;
        private String user;
        private List<EmbeddingModelListener> listeners;

        private Builder() {}

        /**
         * Sets the gateway embedding model id, e.g. {@code "text-embedding-3-small"}.
         *
         * @param modelName the model id
         * @return {@code this}
         */
        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        /**
         * Sets the number of dimensions of the returned embeddings. Only the models that support it accept this value.
         *
         * @param dimensions the number of dimensions
         * @return {@code this}
         */
        public Builder dimensions(Integer dimensions) {
            this.dimensions = dimensions;
            return this;
        }

        /**
         * Sets the wire format of the returned embeddings. The SDK decodes
         * {@link EncodingFormat#BASE64} for you, so both formats give the same vectors.
         *
         * @param encodingFormat the {@link EncodingFormat}
         * @return {@code this}
         */
        public Builder encodingFormat(EncodingFormat encodingFormat) {
            this.encodingFormat = isNull(encodingFormat) ? null : encodingFormat.value();
            return this;
        }

        /**
         * Sets the wire format of the returned embeddings as a raw string, for the formats that
         * {@link EncodingFormat} does not cover yet.
         *
         * @param encodingFormat the encoding format
         * @return {@code this}
         */
        public Builder encodingFormat(String encodingFormat) {
            this.encodingFormat = encodingFormat;
            return this;
        }

        /**
         * Sets the identifier of the end user, used for abuse monitoring.
         *
         * @param user the user identifier
         * @return {@code this}
         */
        public Builder user(String user) {
            this.user = user;
            return this;
        }

        /**
         * Sets the {@link EmbeddingModelListener}s notified around every embedding request.
         *
         * @param listeners the listeners to register
         * @return {@code this}
         */
        public Builder listeners(List<EmbeddingModelListener> listeners) {
            this.listeners = listeners;
            return this;
        }

        public WatsonxGatewayEmbeddingModel build() {
            return new WatsonxGatewayEmbeddingModel(this);
        }
    }
}
