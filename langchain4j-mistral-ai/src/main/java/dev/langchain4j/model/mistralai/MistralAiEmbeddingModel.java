package dev.langchain4j.model.mistralai;

import static dev.langchain4j.model.chat.policy.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.model.mistralai.internal.mapper.MistralAiMapper.tokenUsageFrom;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;
import static java.util.stream.Collectors.toList;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.DimensionAwareEmbeddingModel;
import dev.langchain4j.model.mistralai.internal.api.MistralAiEmbeddingRequest;
import dev.langchain4j.model.mistralai.internal.api.MistralAiEmbeddingResponse;
import dev.langchain4j.model.mistralai.internal.client.MistralAiClient;
import dev.langchain4j.model.mistralai.spi.MistralAiEmbeddingModelBuilderFactory;
import dev.langchain4j.model.output.Response;
import java.time.Duration;
import java.util.List;

/**
 * Represents a Mistral AI embedding model, such as mistral-embed.
 * You can find description of parameters <a href="https://docs.mistral.ai/api/#operation/createEmbedding">here</a>.
 */
public class MistralAiEmbeddingModel extends DimensionAwareEmbeddingModel {

    private static final String EMBEDDINGS_ENCODING_FORMAT = "float";
    private final MistralAiClient client;
    private final String modelName;
    private final Integer maxRetries;

    /**
     * Constructs a new MistralAiEmbeddingModel instance.
     *
     * @param baseUrl      the base URL of the Mistral AI API. It use a default value if not specified
     * @param apiKey       the API key for authentication
     * @param modelName    the name of the embedding model. It uses a default value if not specified
     * @param timeout      the timeout duration for API requests. It uses a default value of 60 seconds if not specified
     *                     <p>
     *                     The default value is 60 seconds
     * @param logRequests  a flag indicating whether to log API requests
     * @param logResponses a flag indicating whether to log API responses
     * @param maxRetries   the maximum number of retries for API requests. It uses a default value of 3 if not specified
     */
    public MistralAiEmbeddingModel(
            String baseUrl,
            String apiKey,
            String modelName,
            Duration timeout,
            Boolean logRequests,
            Boolean logResponses,
            Integer maxRetries) {
        this.client = MistralAiClient.builder()
                .baseUrl(getOrDefault(baseUrl, "https://api.mistral.ai/v1"))
                .apiKey(apiKey)
                .timeout(getOrDefault(timeout, Duration.ofSeconds(60)))
                .logRequests(getOrDefault(logRequests, false))
                .logResponses(getOrDefault(logResponses, false))
                .build();
        this.modelName = getOrDefault(modelName, MistralAiEmbeddingModelName.MISTRAL_EMBED.toString());
        this.maxRetries = getOrDefault(maxRetries, 3);
    }

    /**
     * @deprecated Please use {@code builder()} instead, and explicitly set the model name and,
     * if necessary, other parameters.
     * <b>The default value for the model name will be removed in future releases!</b>
     */
    @Deprecated(forRemoval = true)
    public static MistralAiEmbeddingModel withApiKey(String apiKey) {
        return builder().apiKey(apiKey).build();
    }

    /**
     * Embeds a list of text segments using the Mistral AI embedding model.
     *
     * @param textSegments the list of text segments to embed
     * @return a Response object containing the embeddings and token usage information
     */
    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {

        MistralAiEmbeddingRequest request = MistralAiEmbeddingRequest.builder()
                .model(modelName)
                .input(textSegments.stream().map(TextSegment::text).collect(toList()))
                .encodingFormat(EMBEDDINGS_ENCODING_FORMAT)
                .build();

        MistralAiEmbeddingResponse response = withRetry(() -> client.embedding(request), maxRetries);

        List<Embedding> embeddings = response.getData().stream()
                .map(mistralAiEmbedding -> Embedding.from(mistralAiEmbedding.getEmbedding()))
                .collect(toList());

        return Response.from(embeddings, tokenUsageFrom(response.getUsage()));
    }

    public static MistralAiEmbeddingModelBuilder builder() {
        for (MistralAiEmbeddingModelBuilderFactory factory :
                loadFactories(MistralAiEmbeddingModelBuilderFactory.class)) {
            return factory.get();
        }
        return new MistralAiEmbeddingModelBuilder();
    }

    public static class MistralAiEmbeddingModelBuilder {

        private String baseUrl;

        private String apiKey;

        private String modelName;

        private Duration timeout;

        private Boolean logRequests;

        private Boolean logResponses;

        private Integer maxRetries;

        public MistralAiEmbeddingModelBuilder() {}

        public MistralAiEmbeddingModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public MistralAiEmbeddingModelBuilder modelName(MistralAiEmbeddingModelName modelName) {
            this.modelName = modelName.toString();
            return this;
        }

        /**
         * @param baseUrl      the base URL of the Mistral AI API. It use a default value if not specified
         * @return {@code this}.
         */
        public MistralAiEmbeddingModelBuilder baseUrl(final String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        /**
         * @param apiKey       the API key for authentication
         * @return {@code this}.
         */
        public MistralAiEmbeddingModelBuilder apiKey(final String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /**
         * @param timeout      the timeout duration for API requests. It uses a default value of 60 seconds if not specified
         *                     <p>
         *                     The default value is 60 seconds
         * @return {@code this}.
         */
        public MistralAiEmbeddingModelBuilder timeout(final Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * @param logRequests  a flag indicating whether to log API requests
         * @return {@code this}.
         */
        public MistralAiEmbeddingModelBuilder logRequests(final Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        /**
         * @param logResponses a flag indicating whether to log API responses
         * @return {@code this}.
         */
        public MistralAiEmbeddingModelBuilder logResponses(final Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public MistralAiEmbeddingModelBuilder maxRetries(final Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public MistralAiEmbeddingModel build() {
            return new MistralAiEmbeddingModel(
                    this.baseUrl,
                    this.apiKey,
                    this.modelName,
                    this.timeout,
                    this.logRequests,
                    this.logResponses,
                    this.maxRetries);
        }

        @Override
        public String toString() {
            return "MistralAiEmbeddingModelBuilder(" + "baseUrl=" + this.baseUrl + ", apiKey=" + this.apiKey == null
                    ? ""
                    : "*****"
                            + ", modelName=" + this.modelName
                            + ", timeout=" + this.timeout
                            + ", logRequests=" + this.logRequests
                            + ", logResponses=" + this.logResponses
                            + ", maxRetries=" + this.maxRetries
                            + ")";
        }
    }
}
