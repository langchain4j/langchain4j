package dev.langchain4j.model.mistralai;

import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptions;
import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.model.mistralai.internal.mapper.MistralAiMapper.finishReasonFrom;
import static dev.langchain4j.model.mistralai.internal.mapper.MistralAiMapper.tokenUsageFrom;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;

import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.model.language.LanguageModel;
import dev.langchain4j.model.mistralai.internal.api.MistralAiChatCompletionChoice;
import dev.langchain4j.model.mistralai.internal.api.MistralAiChatCompletionResponse;
import dev.langchain4j.model.mistralai.internal.api.MistralAiFimCompletionRequest;
import dev.langchain4j.model.mistralai.internal.client.MistralAiClient;
import dev.langchain4j.model.mistralai.spi.MistralAiFimModelBuilderFactory;
import dev.langchain4j.model.output.Response;
import java.time.Duration;
import java.util.List;

/**
 * Represents a Mistral AI FIM Completion Model with a language completion interface, users can define the starting point of the text/code using a prompt, and the ending point of the text/code using an optional suffix and an optional stop.
 * <p>
 * This model will then generate the text/code that fits in between.
 * <p>
 * You can find description of parameters <a href="https://docs.mistral.ai/api/#operation/createFIMCompletion">here</a>.
 */
public class MistralAiFimModel implements LanguageModel {

    private final MistralAiClient client;
    private final String modelName;
    private final Double temperature;
    private final Integer maxTokens;
    private final Integer minTokens;
    private final Double topP;
    private final Integer randomSeed;
    private final List<String> stop;
    private final Integer maxRetries;

    public MistralAiFimModel(Builder builder) {
        this.client = MistralAiClient.builder()
                .httpClientBuilder(builder.httpClientBuilder)
                .baseUrl(getOrDefault(builder.baseUrl, "https://api.mistral.ai/v1"))
                .apiKey(builder.apiKey)
                .timeout(builder.timeout)
                .logRequests(getOrDefault(builder.logRequests, false))
                .logResponses(getOrDefault(builder.logResponses, false))
                .build();
        this.modelName = ensureNotBlank(builder.modelName, "modelName");
        this.temperature = builder.temperature;
        this.maxTokens = builder.maxTokens;
        this.minTokens = builder.minTokens;
        this.topP = builder.topP;
        this.randomSeed = builder.randomSeed;
        this.stop = copy(builder.stop);
        this.maxRetries = getOrDefault(builder.maxRetries, 2);
    }

    /**
     * Generates a completion for the given prompt and suffix.
     *
     * @param prompt the starting point of the text/code
     * @param suffix Optional text/code that adds more context for the model. When given a prompt and a suffix the model will fill what is between them.
     * @return a response containing the generated text/code
     */
    public Response<String> generate(String prompt, String suffix) {
        return completion(prompt, suffix);
    }

    /**
     * Generates a completion for the given prompt.
     *
     * @param prompt the starting point of the text/code
     * @return a response containing the generated text/code
     */
    @Override
    public Response<String> generate(String prompt) {
        return generate(prompt, null);
    }

    private Response<String> completion(String prompt, String suffix) {
        ensureNotBlank(prompt, "Prompt");
        MistralAiFimCompletionRequest request = MistralAiFimCompletionRequest.builder()
                .model(this.modelName)
                .prompt(prompt)
                .suffix(suffix)
                .temperature(this.temperature)
                .maxTokens(this.maxTokens)
                .minTokens(this.minTokens)
                .topP(this.topP)
                .randomSeed(this.randomSeed)
                .stop(this.stop)
                .stream(false)
                .build();

        MistralAiChatCompletionResponse response =
                withRetryMappingExceptions(() -> client.fimCompletion(request), maxRetries);

        MistralAiChatCompletionChoice responseChoice = response.getChoices().get(0);
        return Response.from(
                responseChoice.getMessage().asText(),
                tokenUsageFrom(response.getUsage()),
                finishReasonFrom(responseChoice.getFinishReason()));
    }

    public static Builder builder() {
        for (MistralAiFimModelBuilderFactory factory : loadFactories(MistralAiFimModelBuilderFactory.class)) {
            return factory.get();
        }
        return new Builder();
    }

    public static class Builder {

        private HttpClientBuilder httpClientBuilder;
        private String baseUrl;
        private String apiKey;
        private String modelName;
        private Double temperature;
        private Integer maxTokens;
        private Integer minTokens;
        private Double topP;
        private Integer randomSeed;
        private List<String> stop;
        private Duration timeout;
        private Boolean logRequests;
        private Boolean logResponses;
        private Integer maxRetries;

        public Builder() {}

        /**
         * @param httpClientBuilder the HTTP client builder to use for creating the HTTP client
         * @return {@code this}.
         */
        public Builder httpClientBuilder(HttpClientBuilder httpClientBuilder) {
            this.httpClientBuilder = httpClientBuilder;
            return this;
        }

        /**
         * @param baseUrl the base URL of the Mistral AI API. It uses the default value if not specified.
         * @return builder
         */
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        /**
         * @param apiKey the API key for authentication
         * @return builder
         */
        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /**
         * @param modelName the name of the Mistral AI model to use
         * @return builder
         */
        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        /**
         * @param modelName the name of the Mistral AI model to use
         * @return builder
         */
        public Builder modelName(MistralAiFimModelName modelName) {
            this.modelName = modelName.toString();
            return this;
        }

        /**
         * @param temperature the temperature parameter for generating responses
         * @return builder
         */
        public Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        /**
         * @param maxTokens the maximum number of tokens to generate in a response
         * @return builder
         */
        public Builder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        /**
         * @param minTokens the minimum number of tokens to generate in a response
         * @return builder
         */
        public Builder minTokens(Integer minTokens) {
            this.minTokens = minTokens;
            return this;
        }

        /**
         * @param topP the top-p parameter for generating responses
         * @return builder
         */
        public Builder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        /**
         * @param randomSeed the random seed for generating responses
         * @return builder
         */
        public Builder randomSeed(Integer randomSeed) {
            this.randomSeed = randomSeed;
            return this;
        }

        /**
         * @param stop a list of tokens at which the model should stop generating tokens
         * @return builder
         */
        public Builder stop(List<String> stop) {
            this.stop = stop;
            return this;
        }

        /**
         * @param timeout the timeout duration for API requests. The default value is 60 seconds.
         * @return builder
         */
        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * @param logRequests a flag indicating whether to log API requests
         * @return builder
         */
        public Builder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        /**
         * @param logResponses a flag indicating whether to log API responses
         * @return builder
         */
        public Builder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        /**
         * @param maxRetries the maximum number of retries for API requests. It uses the default value 2 if not specified.
         * @return builder
         */
        public Builder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public MistralAiFimModel build() {
            return new MistralAiFimModel(this);
        }
    }
}
