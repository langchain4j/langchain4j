package dev.langchain4j.model.mistralai;

import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptions;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;

import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.model.mistralai.internal.api.MistralAiModelCard;
import dev.langchain4j.model.mistralai.internal.api.MistralAiModelResponse;
import dev.langchain4j.model.mistralai.internal.client.MistralAiClient;
import dev.langchain4j.model.mistralai.spi.MistralAiModelsBuilderFactory;
import dev.langchain4j.model.output.Response;
import java.time.Duration;
import java.util.List;

/**
 * Represents a collection of Mistral AI models.
 * You can find description of parameters <a href="https://docs.mistral.ai/api/#operation/listModels">here</a>.
 */
public class MistralAiModels {

    private final MistralAiClient client;
    private final Integer maxRetries;

    public MistralAiModels(MistralAiModelsBuilder builder) {
        this.client = MistralAiClient.builder()
                .httpClientBuilder(builder.httpClientBuilder)
                .baseUrl(getOrDefault(builder.baseUrl, "https://api.mistral.ai/v1"))
                .apiKey(builder.apiKey)
                .timeout(builder.timeout)
                .logRequests(getOrDefault(builder.logRequests, false))
                .logResponses(getOrDefault(builder.logResponses, false))
                .build();
        this.maxRetries = getOrDefault(builder.maxRetries, 2);
    }

    /**
     * @deprecated please use {@link #MistralAiModels(MistralAiModelsBuilder)} instead
     */
    @Deprecated(forRemoval = true)
    public MistralAiModels(
            String baseUrl,
            String apiKey,
            Duration timeout,
            Boolean logRequests,
            Boolean logResponses,
            Integer maxRetries) {
        this.client = MistralAiClient.builder()
                .baseUrl(getOrDefault(baseUrl, "https://api.mistral.ai/v1"))
                .apiKey(apiKey)
                .timeout(timeout)
                .logRequests(getOrDefault(logRequests, false))
                .logResponses(getOrDefault(logResponses, false))
                .build();
        this.maxRetries = getOrDefault(maxRetries, 2);
    }

    public static MistralAiModels withApiKey(String apiKey) {
        return builder().apiKey(apiKey).build();
    }

    /**
     * Retrieves the list of all available models.
     *
     * @return the response containing the list of models
     */
    public Response<List<MistralAiModelCard>> availableModels() {
        MistralAiModelResponse response = withRetryMappingExceptions(client::listModels, maxRetries);
        return Response.from(response.getData());
    }

    public static MistralAiModelsBuilder builder() {
        for (MistralAiModelsBuilderFactory factory : loadFactories(MistralAiModelsBuilderFactory.class)) {
            return factory.get();
        }
        return new MistralAiModelsBuilder();
    }

    public static class MistralAiModelsBuilder {

        private HttpClientBuilder httpClientBuilder;
        private String baseUrl;
        private String apiKey;
        private Duration timeout;
        private Boolean logRequests;
        private Boolean logResponses;
        private Integer maxRetries;

        public MistralAiModelsBuilder() {}

        public MistralAiModelsBuilder httpClientBuilder(HttpClientBuilder httpClientBuilder) {
            this.httpClientBuilder = httpClientBuilder;
            return this;
        }

        /**
         * @param baseUrl the base URL of the Mistral AI API. It uses the default value if not specified
         * @return {@code this}.
         */
        public MistralAiModelsBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        /**
         * @param apiKey the API key for authentication
         * @return {@code this}.
         */
        public MistralAiModelsBuilder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /**
         * @param timeout the timeout duration for API requests. It uses the default value of 60 seconds if not
         * specified
         * @return {@code this}.
         */
        public MistralAiModelsBuilder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * @param logRequests a flag whether to log raw HTTP requests
         * @return {@code this}.
         */
        public MistralAiModelsBuilder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        /**
         * @param logResponses a flag whether to log raw HTTP responses
         * @return {@code this}.
         */
        public MistralAiModelsBuilder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public MistralAiModelsBuilder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public MistralAiModels build() {
            return new MistralAiModels(this);
        }
    }
}
