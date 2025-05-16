package dev.langchain4j.model.mistralai;

import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptions;
import static dev.langchain4j.internal.Utils.copyIfNotNull;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.model.mistralai.internal.mapper.MistralAiMapper.*;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;

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
 *  Represents a Mistral AI FIM Completion Model with a language completion interface, users can define the starting point of the text/code using a prompt, and the ending point of the text/code using an optional suffix and an optional stop.
 *  <p>
 *  This model will then generate the text/code that fits in between.
 *  <p>
 *  You can find description of parameters <a href="https://docs.mistral.ai/api/#operation/createFIMCompletion">here</a>.
 */
public class MistralAiFimModel implements LanguageModel {

    private final MistralAiClient client;
    private final String modelName;
    private final Double temperature;
    private final Integer maxTokens;
    private final Integer minTokens;
    private final Double topP;
    private final Integer randomSeed;
    private final List<String> stops;
    private final Integer maxRetries;

    /**
     * Constructs a MistralAiFimModel with the specified parameters.
     *
     * @param baseUrl       the base URL of the Mistral AI API. It uses the default value if not specified.
     * @param apiKey        the API key for authentication
     * @param modelName     the name of the Mistral AI model to use
     * @param temperature   the temperature parameter for generating responses
     * @param maxTokens     the maximum number of tokens to generate in a response
     * @param minTokens     the minimum number of tokens to generate in a response
     * @param topP          the top-p parameter for generating responses
     * @param randomSeed    the random seed for generating responses
     * @param stops         a list of tokens at which the model should stop generating tokens
     * @param timeout       the timeout duration for API requests
     *                      <p>
     *                      The default value is 60 seconds.
     * @param logRequests   a flag indicating whether to log API requests
     * @param logResponses  a flag indicating whether to log API responses
     * @param maxRetries    the maximum number of retries for API requests. It uses the default value 3 if not specified.
     */
    public MistralAiFimModel(
            String baseUrl,
            String apiKey,
            String modelName,
            Double temperature,
            Integer maxTokens,
            Integer minTokens,
            Double topP,
            Integer randomSeed,
            List<String> stops,
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
        this.modelName = ensureNotBlank(modelName, "modelName");
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.minTokens = getOrDefault(minTokens, 0);
        this.topP = topP;
        this.randomSeed = randomSeed;
        this.stops = copyIfNotNull(stops);
        this.maxRetries = getOrDefault(maxRetries, 2);
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
                .stop(this.stops)
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

    public static MistralAiFimModelBuilder builder() {
        for (MistralAiFimModelBuilderFactory factory : loadFactories(MistralAiFimModelBuilderFactory.class)) {
            return factory.get();
        }
        return new MistralAiFimModelBuilder();
    }

    public static class MistralAiFimModelBuilder {

        private String baseUrl;
        private String apiKey;
        private String modelName;
        private Double temperature;
        private Integer maxTokens;
        private Integer minTokens;
        private Double topP;
        private Integer randomSeed;
        private List<String> stops;
        private Duration timeout;
        private Boolean logRequests;
        private Boolean logResponses;
        private Integer maxRetries;

        public MistralAiFimModelBuilder() {}

        public MistralAiFimModelBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public MistralAiFimModelBuilder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public MistralAiFimModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public MistralAiFimModelBuilder modelName(MistralAiFimModelName modelName) {
            this.modelName = modelName.toString();
            return this;
        }

        public MistralAiFimModelBuilder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public MistralAiFimModelBuilder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public MistralAiFimModelBuilder minTokens(Integer minTokens) {
            this.minTokens = minTokens;
            return this;
        }

        public MistralAiFimModelBuilder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        public MistralAiFimModelBuilder randomSeed(Integer randomSeed) {
            this.randomSeed = randomSeed;
            return this;
        }

        public MistralAiFimModelBuilder stops(List<String> stops) {
            this.stops = stops;
            return this;
        }

        public MistralAiFimModelBuilder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public MistralAiFimModelBuilder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public MistralAiFimModelBuilder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public MistralAiFimModelBuilder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public MistralAiFimModel build() {
            return new MistralAiFimModel(
                    baseUrl,
                    apiKey,
                    modelName,
                    temperature,
                    maxTokens,
                    minTokens,
                    topP,
                    randomSeed,
                    stops,
                    timeout,
                    logRequests,
                    logResponses,
                    maxRetries);
        }

        @Override
        public String toString() {
            return "MistralAiFimModelBuilder(" + "baseUrl=" + this.baseUrl + ", apiKey=" + this.apiKey == null
                    ? ""
                    : "*****"
                            + ", modelName=" + this.modelName
                            + ", temperature=" + this.temperature
                            + ", topP=" + this.topP
                            + ", maxTokens=" + this.maxTokens
                            + ", minTokens=" + this.minTokens
                            + ", randomSeed=" + this.randomSeed
                            + ", stops=" + this.stops
                            + ", timeout=" + this.timeout
                            + ", logRequests=" + this.logRequests
                            + ", logResponses=" + this.logResponses
                            + ", maxRetries=" + this.maxRetries
                            + ")";
        }
    }
}
