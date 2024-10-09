package dev.langchain4j.model.mistralai;

import dev.langchain4j.model.language.LanguageModel;
import dev.langchain4j.model.mistralai.internal.api.MistralAiChatCompletionChoice;
import dev.langchain4j.model.mistralai.internal.api.MistralAiChatCompletionResponse;
import dev.langchain4j.model.mistralai.internal.api.MistralAiFimCompletionRequest;
import dev.langchain4j.model.mistralai.internal.client.MistralAiClient;
import dev.langchain4j.model.mistralai.spi.MistralAiCompletionModelBuilderFactory;
import dev.langchain4j.model.output.Response;
import lombok.Builder;

import java.time.Duration;
import java.util.List;

import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.model.mistralai.internal.mapper.MistralAiMapper.*;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;

/**
 *  Represents a Mistral AI FIM Completion Model with a language completion interface, users can define the starting point of the text/code using a prompt, and the ending point of the text/code using an optional suffix and an optional stop.
 *  <p>
 *  This model will then generate the text/code that fits in between.
 *  <p>
 *  You can find description of parameters <a href="https://docs.mistral.ai/api/#operation/createFIMCompletion">here</a>.
 */
public class MistralAiCompletionModel implements LanguageModel {

    private final MistralAiClient client;
    private final String modelName;
    private String suffix;
    private final Double temperature;
    private final Integer maxTokens;
    private final Integer minTokens;
    private final Double topP;
    private final Integer randomSeed;
    private final List<String> stops;

    private final Integer maxRetries;

    /**
     * Constructs a MistralAiCompletionModel with the specified parameters.
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
    @Builder
    public MistralAiCompletionModel(String baseUrl,
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
        this.modelName = getOrDefault(modelName, MistralAiCodeModelName.CODESTRAL_LATEST.toString());
        this.suffix = "";
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.minTokens = getOrDefault(minTokens,0);
        this.topP = topP;
        this.randomSeed = randomSeed;
        this.stops = stops;
        this.maxRetries = getOrDefault(maxRetries, 3);
    }

    /**
     * Creates a MistralAiCompletionModel with the specified API key.
     *
     * @param apiKey the API key for authentication
     * @return a MistralAiCompletionModel
     */
    public static MistralAiCompletionModel withApiKey(String apiKey) {
        return builder().apiKey(apiKey).build();
    }

    /**
     * Generates a completion for the given prompt and suffix.
     *
     * @param prompt the starting point of the text/code
     * @param suffix Optional text/code that adds more context for the model. When given a prompt and a suffix the model will fill what is between them.
     * @return a response containing the generated text/code
     */
    public Response<String> generate(String prompt, String suffix) {
        this.suffix = suffix;
        return generate(prompt);
    }

    /**
     * Generates a completion for the given prompt.
     *
     * @param prompt the starting point of the text/code
     * @return a response containing the generated text/code
     */
    @Override
    public Response<String> generate(String prompt) {
        ensureNotBlank(prompt, "Prompt");
        MistralAiFimCompletionRequest request = MistralAiFimCompletionRequest.builder()
                .model(this.modelName)
                .prompt(prompt)
                .suffix(this.suffix)
                .temperature(this.temperature)
                .maxTokens(this.maxTokens)
                .minTokens(this.minTokens)
                .topP(this.topP)
                .randomSeed(this.randomSeed)
                .stop(this.stops)
                .stream(false)
                .build();

        MistralAiChatCompletionResponse response = withRetry(() -> client.fimCompletion(request), maxRetries);
        MistralAiChatCompletionChoice responseChoice = response.getChoices().get(0);
        return Response.from(
                responseChoice.getMessage().getContent(),
                tokenUsageFrom(response.getUsage()),
                finishReasonFrom(responseChoice.getFinishReason())
        );
    }

    public static MistralAiCompletionModelBuilder builder() {
        for (MistralAiCompletionModelBuilderFactory factory : loadFactories(MistralAiCompletionModelBuilderFactory.class)) {
            return factory.get();
        }
        return new MistralAiCompletionModelBuilder();
    }

    public static class MistralAiCompletionModelBuilder {

        public MistralAiCompletionModelBuilder() {
            // This is public so it can be extended
            // By default with Lombok it becomes package private
        }

        public MistralAiCompletionModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public MistralAiCompletionModelBuilder modelName(MistralAiCodeModelName modelName) {
            this.modelName = modelName.toString();
            return this;
        }
    }
}
