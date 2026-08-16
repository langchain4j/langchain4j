package dev.langchain4j.model.watsonx;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.model.ModelProvider.WATSONX;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import com.ibm.watsonx.ai.embedding.EmbeddingParameters;
import com.ibm.watsonx.ai.embedding.EmbeddingService;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.listener.EmbeddingModelListener;
import dev.langchain4j.model.embedding.request.EmbeddingInput;
import dev.langchain4j.model.embedding.request.EmbeddingRequest;
import dev.langchain4j.model.embedding.response.EmbeddingResponse;
import dev.langchain4j.model.output.Response;
import java.util.List;

/**
 * A {@link EmbeddingModel} implementation that integrates IBM watsonx.ai with LangChain4j.
 * <p>
 * <b>Example usage:</b>
 *
 * <pre>{@code
 * EmbeddingModel embeddingModel = WatsonxEmbeddingModel.builder()
 *     .baseUrl("https://...") // or use CloudRegion
 *     .apiKey("...")
 *     .projectId("...")
 *     .modelName("ibm/granite-embedding-278m-multilingual")
 *     .build();
 * }</pre>
 *
 */
public class WatsonxEmbeddingModel implements EmbeddingModel {

    private final EmbeddingService embeddingService;
    private final String modelName;
    private final List<EmbeddingModelListener> listeners;

    private WatsonxEmbeddingModel(Builder builder) {

        var embeddingServiceBuilder = nonNull(builder.authenticator)
                ? EmbeddingService.builder().authenticator(builder.authenticator)
                : EmbeddingService.builder().apiKey(builder.apiKey);

        embeddingService = embeddingServiceBuilder
                .baseUrl(builder.baseUrl)
                .modelId(builder.modelName)
                .version(builder.version)
                .projectId(builder.projectId)
                .spaceId(builder.spaceId)
                .timeout(builder.timeout)
                .logRequests(builder.logRequests)
                .logResponses(builder.logResponses)
                .httpClient(builder.httpClient)
                .verifySsl(builder.verifySsl)
                .build();
        this.modelName = builder.modelName;
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
    public EmbeddingResponse doEmbed(EmbeddingRequest request) {
        List<String> inputs =
                request.inputs().stream().map(EmbeddingInput::text).toList();
        return embed(inputs, null);
    }

    /**
     * Embeds the text content of a list of TextSegment using the specified {@link EmbeddingParameters}.
     *
     * @param textSegments the text segments to embed.
     * @param parameters the embedding parameters to use.
     * @return the embeddings.
     */
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments, EmbeddingParameters parameters) {

        if (isNull(textSegments) || textSegments.isEmpty()) return Response.from(List.of());

        List<String> inputs = textSegments.stream().map(TextSegment::text).toList();
        EmbeddingResponse response = embed(inputs, parameters);

        return Response.from(response.embeddings(), response.metadata().tokenUsage());
    }

    private EmbeddingResponse embed(List<String> inputs, EmbeddingParameters parameters) {

        com.ibm.watsonx.ai.embedding.EmbeddingResponse response =
                WatsonxExceptionMapper.INSTANCE.withExceptionMapper(() -> embeddingService.embed(inputs, parameters));

        return Converter.toEmbeddingResponse(response, modelName);
    }

    /**
     * Returns a new {@link Builder} instance.
     * <p>
     * <b>Example usage:</b>
     *
     * <pre>{@code
     * EmbeddingModel embeddingModel = WatsonxEmbeddingModel.builder()
     *     .baseUrl("https://...") // or use CloudRegion
     *     .apiKey("...")
     *     .projectId("...")
     *     .modelName("ibm/granite-embedding-278m-multilingual")
     *     .build();
     * }</pre>
     *
     * @return {@link Builder} instance.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder class for constructing {@link WatsonxEmbeddingModel} instances with configurable parameters.
     */
    public static class Builder extends WatsonxBuilder<Builder> {
        private String modelName;
        private List<EmbeddingModelListener> listeners;

        private Builder() {}

        /**
         * Sets the watsonx.ai embedding model ID, e.g. {@code "ibm/slate-125m-english-rtrvr"}.
         *
         * @param modelName the model ID
         * @return {@code this}
         */
        public Builder modelName(String modelName) {
            this.modelName = modelName;
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

        public WatsonxEmbeddingModel build() {
            return new WatsonxEmbeddingModel(this);
        }
    }
}
