package dev.langchain4j.model.mistralai;

import static dev.langchain4j.model.chat.policy.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;

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

    /**
     * Constructs a new instance of MistralAiModels.
     *
     * @param baseUrl      the base URL of the Mistral AI API. It uses the default value if not specified
     * @param apiKey       the API key for authentication
     * @param timeout      the timeout duration for API requests. It uses the default value of 60 seconds if not specified
     * @param logRequests  a flag whether to log raw HTTP requests
     * @param logResponses a flag whether to log raw HTTP responses
     * @param maxRetries   the maximum number of retries for API requests. It uses the default value of 3 if not specified
     */
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
                .timeout(getOrDefault(timeout, Duration.ofSeconds(60)))
                .logRequests(getOrDefault(logRequests, false))
                .logResponses(getOrDefault(logResponses, false))
                .build();
        this.maxRetries = getOrDefault(maxRetries, 3);
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
        MistralAiModelResponse response = withRetry(client::listModels, maxRetries);
        return Response.from(response.getData());
    }

    public static MistralAiModelsBuilder builder() {
        for (MistralAiModelsBuilderFactory factory : loadFactories(MistralAiModelsBuilderFactory.class)) {
            return factory.get();
        }
        return new MistralAiModelsBuilder();
    }

    public static class MistralAiModelsBuilder {

        private String baseUrl;

        private String apiKey;

        private Duration timeout;

        private Boolean logRequests;

        private Boolean logResponses;

        private Integer maxRetries;

        public MistralAiModelsBuilder() {}

        /**
         * @param baseUrl the base URL of the Mistral AI API. It uses the default value if not specified
         * @return {@code this}.
         */
        public MistralAiModelsBuilder baseUrl(final String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        /**
         * @param apiKey the API key for authentication
         * @return {@code this}.
         */
        public MistralAiModelsBuilder apiKey(final String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /**
         * @param timeout the timeout duration for API requests. It uses the default value of 60 seconds if not
         * specified
         * @return {@code this}.
         */
        public MistralAiModelsBuilder timeout(final Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * @param logRequests a flag whether to log raw HTTP requests
         * @return {@code this}.
         */
        public MistralAiModelsBuilder logRequests(final Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        /**
         * @param logResponses a flag whether to log raw HTTP responses
         * @return {@code this}.
         */
        public MistralAiModelsBuilder logResponses(final Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public MistralAiModelsBuilder maxRetries(final Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public MistralAiModels build() {
            return new MistralAiModels(
                    this.baseUrl, this.apiKey, this.timeout, this.logRequests, this.logResponses, this.maxRetries);
        }

        @Override
        public String toString() {
            return "MistralAiModelsBuilder(" + "baseUrl=" + this.baseUrl + ", apiKey=" + this.apiKey == null
                    ? ""
                    : "*****"
                            + ", timeout=" + this.timeout
                            + ", logRequests=" + this.logRequests
                            + ", logResponses=" + this.logResponses
                            + ", maxRetries=" + this.maxRetries
                            + ")";
        }
    }
}
