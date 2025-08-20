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
 * EmbeddingModel embeddingModel = WatsonxEmbedding.builder()
 *     .service(embeddingService)
 *     .build();
 * }</pre>
 *
 *
 * @see EmbeddingService
 */
public class WatsonxEmbeddingModel implements EmbeddingModel {

    private final EmbeddingService embeddingService;

    private WatsonxEmbeddingModel(Builder builder) {
        requireNonNull(builder, "builder is required");
        this.embeddingService = builder.embeddingService;
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
        return embedAll(textSegments, null);
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

        EmbeddingResponse response = WatsonxExceptionMapper.INSTANCE.withExceptionMapper(
                () -> embeddingService.embedding(inputs, parameters));
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
     *
     * EmbeddingModel embeddingModel = WatsonxEmbedding.builder()
     *     .service(embeddingService)
     *     .build();
     * }</pre>
     *
     *
     * @see EmbeddingService
     * @return {@link Builder} instance.
     *
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder class for constructing {@link WatsonxEmbeddingModel} instances with configurable parameters.
     */
    public static class Builder {
        private EmbeddingService embeddingService;

        public Builder service(EmbeddingService embeddingService) {
            this.embeddingService = embeddingService;
            return this;
        }

        public WatsonxEmbeddingModel build() {
            return new WatsonxEmbeddingModel(this);
        }
    }
}
