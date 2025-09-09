package dev.langchain4j.model.watsonx;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import com.ibm.watsonx.ai.CloudRegion;
import com.ibm.watsonx.ai.core.auth.iam.IAMAuthenticator;
import com.ibm.watsonx.ai.embedding.EmbeddingParameters;
import com.ibm.watsonx.ai.embedding.EmbeddingResponse;
import com.ibm.watsonx.ai.embedding.EmbeddingResponse.Result;
import com.ibm.watsonx.ai.embedding.EmbeddingService;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import java.time.Duration;
import java.util.List;

/**
 * A {@link EmbeddingModel} implementation that integrates IBM watsonx.ai with LangChain4j.
 * <p>
 * <b>Example usage:</b>
 *
 * <pre>{@code
 * EmbeddingModel embeddingModel = WatsonxEmbeddingModel.builder()
 *     .url("https://...") // or use CloudRegion
 *     .apiKey("...")
 *     .projectId("...")
 *     .modelName("ibm/granite-embedding-278m-multilingual")
 *     .build();
 * }</pre>
 *
 */
public class WatsonxEmbeddingModel implements EmbeddingModel {

    private final EmbeddingService embeddingService;

    private WatsonxEmbeddingModel(Builder builder) {
        var embeddingServiceBuilder = EmbeddingService.builder();
        if (nonNull(builder.authenticationProvider)) {
            embeddingServiceBuilder.authenticationProvider(builder.authenticationProvider);
        } else {
            embeddingServiceBuilder.authenticationProvider(
                    IAMAuthenticator.builder().apiKey(builder.apiKey).build());
        }

        embeddingService = embeddingServiceBuilder
                .url(builder.url)
                .modelId(builder.modelName)
                .version(builder.version)
                .projectId(builder.projectId)
                .spaceId(builder.spaceId)
                .timeout(builder.timeout)
                .logRequests(builder.logRequests)
                .logResponses(builder.logResponses)
                .build();
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
     * EmbeddingModel embeddingModel = WatsonxEmbeddingModel.builder()
     *     .url("https://...") // or use CloudRegion
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
        private String projectId;
        private String spaceId;
        private Duration timeout;

        private Builder() {}

        public Builder url(CloudRegion cloudRegion) {
            return super.url(cloudRegion.getMlEndpoint());
        }

        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public Builder projectId(String projectId) {
            this.projectId = projectId;
            return this;
        }

        public Builder spaceId(String spaceId) {
            this.spaceId = spaceId;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public WatsonxEmbeddingModel build() {
            return new WatsonxEmbeddingModel(this);
        }
    }
}
