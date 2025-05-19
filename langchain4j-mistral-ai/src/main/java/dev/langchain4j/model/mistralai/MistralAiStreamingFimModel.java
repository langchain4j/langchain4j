package dev.langchain4j.model.mistralai;

import static dev.langchain4j.internal.Utils.copyIfNotNull;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;

import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.language.StreamingLanguageModel;
import dev.langchain4j.model.mistralai.internal.api.MistralAiFimCompletionRequest;
import dev.langchain4j.model.mistralai.internal.client.MistralAiClient;
import dev.langchain4j.model.mistralai.spi.MistralAiStreamingFimModelBuilderFactory;
import java.time.Duration;
import java.util.List;

/**
 *  Represents a Mistral AI FIM Completion Model with a language completion interface, users can define the starting point of the text/code using a prompt, and the ending point of the text/code using an optional suffix and an optional stop.
 *  <p>
 *  The model's response is streamed token by token and should be handled with {@link StreamingResponseHandler}.
 *  <p>
 *  You can find description of parameters <a href="https://docs.mistral.ai/api/#operation/createFIMCompletion">here</a>.
 */
public class MistralAiStreamingFimModel implements StreamingLanguageModel {

    private final MistralAiClient client;
    private final String modelName;
    private final Double temperature;
    private final Integer maxTokens;
    private final Integer minTokens;
    private final Double topP;
    private final Integer randomSeed;
    private final List<String> stops;

    /**
     * Constructs a MistralAiStreamingFimModel with the specified parameters.
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
     * @param logRequests   a flag indicating whether to log API requests
     * @param logResponses  a flag indicating whether to log API responses
     * @param timeout       the timeout duration for API requests
     *                      <p>
     *                      The default value is 60 seconds.
     */
    public MistralAiStreamingFimModel(
            String baseUrl,
            String apiKey,
            String modelName,
            Double temperature,
            Integer maxTokens,
            Integer minTokens,
            Double topP,
            Integer randomSeed,
            List<String> stops,
            Boolean logRequests,
            Boolean logResponses,
            Duration timeout) {

        this.client = MistralAiClient.builder()
                .baseUrl(getOrDefault(baseUrl, "https://api.mistral.ai/v1"))
                .apiKey(apiKey)
                .timeout(getOrDefault(timeout, Duration.ofSeconds(60)))
                .logRequests(getOrDefault(logRequests, false))
                .logResponses(getOrDefault(logResponses, false))
                .build();
        this.modelName = getOrDefault(modelName, MistralAiFimModelName.CODESTRAL_LATEST.toString());
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.minTokens = getOrDefault(minTokens, 0);
        this.topP = topP;
        this.randomSeed = randomSeed;
        this.stops = copyIfNotNull(stops);
    }

    /**
     * Generates a completion for the given prompt and suffix.
     *
     * @param prompt  the prompt to generate a completion for
     * @param suffix  Optional text/code that adds more context for the model. When given a prompt and a suffix the model will fill what is between them.
     * @param handler the handler to process the completion response
     */
    public void generate(String prompt, String suffix, StreamingResponseHandler<String> handler) {
        completion(prompt, suffix, handler);
    }

    /**
     * Generates a completion for the given prompt.
     *
     * @param prompt  the prompt to generate a completion for
     * @param handler the handler to process the completion response
     */
    @Override
    public void generate(String prompt, StreamingResponseHandler<String> handler) {
        generate(prompt, null, handler);
    }

    private void completion(String prompt, String suffix, StreamingResponseHandler<String> handler) {
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
                .stream(true)
                .build();

        client.streamingFimCompletion(request, handler);
    }

    public static MistralAiStreamingFimModelBuilder builder() {
        for (MistralAiStreamingFimModelBuilderFactory factory :
                loadFactories(MistralAiStreamingFimModelBuilderFactory.class)) {
            return factory.get();
        }
        return new MistralAiStreamingFimModelBuilder();
    }

    public static class MistralAiStreamingFimModelBuilder {

        private String baseUrl;
        private String apiKey;
        private String modelName;
        private Double temperature;
        private Integer maxTokens;
        private Integer minTokens;
        private Double topP;
        private Integer randomSeed;
        private List<String> stops;
        private Boolean logRequests;
        private Boolean logResponses;
        private Duration timeout;

        public MistralAiStreamingFimModelBuilder() {}

        public MistralAiStreamingFimModelBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public MistralAiStreamingFimModelBuilder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public MistralAiStreamingFimModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public MistralAiStreamingFimModelBuilder modelName(MistralAiFimModelName modelName) {
            this.modelName = modelName.toString();
            return this;
        }

        public MistralAiStreamingFimModelBuilder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public MistralAiStreamingFimModelBuilder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public MistralAiStreamingFimModelBuilder minTokens(Integer minTokens) {
            this.minTokens = minTokens;
            return this;
        }

        public MistralAiStreamingFimModelBuilder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        public MistralAiStreamingFimModelBuilder randomSeed(Integer randomSeed) {
            this.randomSeed = randomSeed;
            return this;
        }

        public MistralAiStreamingFimModelBuilder stops(List<String> stops) {
            this.stops = stops;
            return this;
        }

        public MistralAiStreamingFimModelBuilder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public MistralAiStreamingFimModelBuilder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public MistralAiStreamingFimModelBuilder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public MistralAiStreamingFimModel build() {
            return new MistralAiStreamingFimModel(
                    this.baseUrl,
                    this.apiKey,
                    this.modelName,
                    this.temperature,
                    this.maxTokens,
                    this.minTokens,
                    this.topP,
                    this.randomSeed,
                    this.stops,
                    this.logRequests,
                    this.logResponses,
                    this.timeout);
        }

        @Override
        public String toString() {
            return "MistralAiStreamingFimModelBuilder(" + "baseUrl=" + this.baseUrl + ", apiKey=" + this.apiKey == null
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
                            + ")";
        }
    }
}
