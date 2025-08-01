package dev.langchain4j.model.watsonx;

import static java.util.Objects.isNull;
import static java.util.Objects.requireNonNull;

import com.ibm.watsonx.ai.embedding.EmbeddingParameters;
import com.ibm.watsonx.ai.embedding.EmbeddingResponse;
import com.ibm.watsonx.ai.embedding.EmbeddingResponse.Result;
import com.ibm.watsonx.ai.embedding.EmbeddingService;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import java.util.List;

/**
 * A {@link EmbeddingModel} implementation that integrates IBM watsonx.ai with LangChain4j.
 * <p>
 * <b>Example usage:</b>
 *
 * <pre>{@code
 * EmbeddingService embeddingService = EmbeddingService.builder()
 *     .url("https://...") // or use CloudRegion
 *     .authenticationProvider(authProvider)
 *     .projectId("my-project-id")
 *     .modelId("ibm/granite-embedding-278m-multilingual")
 *     .build();
 *
 * EmbeddingParameters parameters = EmbeddingParameters.builder()
 *     .truncateInputTokens(512)
 *     .build();
 *
 * EmbeddingModel embeddingModel = WatsonxEmbedding.builder()
 *     .service(embeddingService)
 *     .parameters(parameters)
 *     .build();
 * }</pre>
 *
 *
 * @see EmbeddingService
 * @see EmbeddingParameters
 */
public class WatsonxEmbeddingModel implements EmbeddingModel {

    private final EmbeddingService embeddingService;
    private EmbeddingParameters embeddingParameters;

    protected WatsonxEmbeddingModel(Builder builder) {
        requireNonNull(builder, "builder is required");
        this.embeddingService = builder.embeddingService;
        this.embeddingParameters = builder.embeddingParameters;
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {

        if (isNull(textSegments) || textSegments.isEmpty()) return Response.from(List.of());

        List<String> inputs = textSegments.stream().map(TextSegment::text).toList();

        EmbeddingResponse response = embeddingService.embedding(inputs, embeddingParameters);
        return Response.from(response.results().stream()
                .map(Result::embedding)
                .map(Embedding::from)
                .toList());
    }

    /**
     * Returns a new {@link Builder} instance.
     * <p>
     * <b>Example usage:</b>
     *
     * <pre>{@code
     * EmbeddingService embeddingService = EmbeddingService.builder()
     *     .url("https://...") // or use CloudRegion
     *     .authenticationProvider(authProvider)
     *     .projectId("my-project-id")
     *     .modelId("ibm/granite-embedding-278m-multilingual")
     *     .build();
     *
     * EmbeddingParameters parameters = EmbeddingParameters.builder()
     *     .truncateInputTokens(512)
     *     .build();
     *
     * EmbeddingModel embeddingModel = WatsonxEmbedding.builder()
     *     .service(embeddingService)
     *     .parameters(parameters)
     *     .build();
     * }</pre>
     *
     *
     * @see EmbeddingService
     * @see EmbeddingParameters
     * @return {@link Builder} instance.
     *
     */
    public static Builder builder() {
        return new Builder();
    }

    public void setParameters(EmbeddingParameters embeddingParameters) {
        this.embeddingParameters = embeddingParameters;
    }

    /**
     * Builder class for constructing {@link WatsonxEmbeddingModel} instances with configurable parameters.
     */
    public static class Builder {
        private EmbeddingService embeddingService;
        private EmbeddingParameters embeddingParameters;

        public Builder service(EmbeddingService embeddingService) {
            this.embeddingService = embeddingService;
            return this;
        }

        public Builder parameters(EmbeddingParameters embeddingParameters) {
            this.embeddingParameters = embeddingParameters;
            return this;
        }

        public WatsonxEmbeddingModel build() {
            return new WatsonxEmbeddingModel(this);
        }
    }
}
