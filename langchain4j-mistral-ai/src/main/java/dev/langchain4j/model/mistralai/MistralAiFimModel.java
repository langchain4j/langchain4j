package dev.langchain4j.model.mistralai;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.language.LanguageModel;
import dev.langchain4j.model.mistralai.internal.api.MistralAiChatCompletionChoice;
import dev.langchain4j.model.mistralai.internal.api.MistralAiChatCompletionResponse;
import dev.langchain4j.model.mistralai.internal.api.MistralAiFimCompletionRequest;
import dev.langchain4j.model.mistralai.internal.client.MistralAiClient;
import dev.langchain4j.model.mistralai.spi.MistralAiFimModelBuilderFactory;
import dev.langchain4j.model.output.Response;

import java.time.Duration;
import java.util.List;

import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptions;
import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.model.mistralai.internal.mapper.MistralAiMapper.finishReasonFrom;
import static dev.langchain4j.model.mistralai.internal.mapper.MistralAiMapper.tokenUsageFrom;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;

/**
 * Represents a Mistral AI FIM Completion Model with a language completion interface, users can define the starting point of the text/code using a prompt, and the ending point of the text/code using an optional suffix and an optional stop.
 * <p>
 * This model will then generate the text/code that fits in between.
 * <p>
 * You can find description of parameters <a href="https://docs.mistral.ai/api/#operation/createFIMCompletion">here</a>.
 */
@Experimental
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

    /**
     * Constructs a MistralAiFimModel with the specified parameters.
     *
     * @param baseUrl      the base URL of the Mistral AI API. It uses the default value if not specified.
     * @param apiKey       the API key for authentication
     * @param modelName    the name of the Mistral AI model to use
     * @param temperature  the temperature parameter for generating responses
     * @param maxTokens    the maximum number of tokens to generate in a response
     * @param minTokens    the minimum number of tokens to generate in a response
     * @param topP         the top-p parameter for generating responses
     * @param randomSeed   the random seed for generating responses
     * @param stop         a list of tokens at which the model should stop generating tokens
     * @param timeout      the timeout duration for API requests
     *                     <p>
     *                     The default value is 60 seconds.
     * @param logRequests  a flag indicating whether to log API requests
     * @param logResponses a flag indicating whether to log API responses
     * @param maxRetries   the maximum number of retries for API requests. It uses the default value 3 if not specified.
     */
    public MistralAiFimModel(Builder builder) {
        this.client = MistralAiClient.builder()
                .baseUrl(getOrDefault(builder.baseUrl, "https://api.mistral.ai/v1"))
                .apiKey(builder.apiKey)
                .timeout(getOrDefault(builder.timeout, Duration.ofSeconds(60)))
                .logRequests(getOrDefault(builder.logRequests, false))
                .logResponses(getOrDefault(builder.logResponses, false))
                .build();
        this.modelName = ensureNotBlank(builder.modelName, "modelName");
        this.temperature = builder.temperature;
        this.maxTokens = builder.maxTokens;
        this.minTokens = getOrDefault(builder.minTokens, 0);
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
                responseChoice.getMessage().getContent(),
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

        public Builder() {
        }

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public Builder modelName(MistralAiFimModelName modelName) {
            this.modelName = modelName.toString();
            return this;
        }

        public Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public Builder minTokens(Integer minTokens) {
            this.minTokens = minTokens;
            return this;
        }

        public Builder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        public Builder randomSeed(Integer randomSeed) {
            this.randomSeed = randomSeed;
            return this;
        }

        public Builder stop(List<String> stop) {
            this.stop = stop;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public Builder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public Builder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public MistralAiFimModel build() {
            return new MistralAiFimModel(this);
        }
    }
}
