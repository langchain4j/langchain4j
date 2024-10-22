package dev.langchain4j.model.mistralai;

import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.language.StreamingLanguageModel;
import dev.langchain4j.model.mistralai.internal.api.MistralAiFimCompletionRequest;
import dev.langchain4j.model.mistralai.internal.client.MistralAiClient;
import dev.langchain4j.model.mistralai.spi.MistralAiStreamingCompletionModelBuilderFactory;
import lombok.Builder;

import java.time.Duration;
import java.util.List;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;

/**
 *  Represents a Mistral AI FIM Completion Model with a language completion interface, users can define the starting point of the text/code using a prompt, and the ending point of the text/code using an optional suffix and an optional stop.
 *  <p>
 *  The model's response is streamed token by token and should be handled with {@link StreamingResponseHandler}.
 *  <p>
 *  You can find description of parameters <a href="https://docs.mistral.ai/api/#operation/createFIMCompletion">here</a>.
 */
public class MistralAiStreamingCompletionModel implements StreamingLanguageModel {

    private final MistralAiClient client;
    private final String modelName;
    private String suffix;
    private final Double temperature;
    private final Integer maxTokens;
    private final Integer minTokens;
    private final Double topP;
    private final Integer randomSeed;
    private final List<String> stops;

    /**
     * Constructs a MistralAiStreamingCompletionModel with the specified parameters.
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
    @Builder
    public MistralAiStreamingCompletionModel(String baseUrl,
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
        this.modelName = getOrDefault(modelName, MistralAiCodeModelName.CODESTRAL_LATEST.toString());
        this.suffix = "";
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.minTokens = getOrDefault(minTokens,0);
        this.topP = topP;
        this.randomSeed = randomSeed;
        this.stops = stops;
    }

    /**
     * Creates a MistralAiStreamingCompletionModel with the specified API key.
     *
     * @param apiKey the API key for authentication
     * @return a MistralAiStreamingCompletionModel
     */
    public static MistralAiStreamingCompletionModel withApiKey(String apiKey) {
        return builder().apiKey(apiKey).build();
    }

    /**
     * Generates a completion for the given prompt and suffix.
     *
     * @param prompt  the prompt to generate a completion for
     * @param suffix  Optional text/code that adds more context for the model. When given a prompt and a suffix the model will fill what is between them.
     * @param handler the handler to process the completion response
     */
    public void generate(String prompt, String suffix, StreamingResponseHandler<String> handler) {
        this.suffix = suffix;
        generate(prompt, handler);
    }

    /**
     * Generates a completion for the given prompt.
     *
     * @param prompt  the prompt to generate a completion for
     * @param handler the handler to process the completion response
     */
    @Override
    public void generate(String prompt,
                         StreamingResponseHandler<String> handler) {
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
                .stream(true)
                .build();

        client.streamingFimCompletion(request, handler);
    }

    public static MistralAiStreamingCompletionModelBuilder builder() {
        for (MistralAiStreamingCompletionModelBuilderFactory factory : loadFactories(MistralAiStreamingCompletionModelBuilderFactory.class)) {
            return factory.get();
        }
        return new MistralAiStreamingCompletionModelBuilder();
    }

    public static class MistralAiStreamingCompletionModelBuilder {

        public MistralAiStreamingCompletionModelBuilder() {
        }

        public MistralAiStreamingCompletionModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public MistralAiStreamingCompletionModelBuilder modelName(MistralAiCodeModelName modelName) {
            this.modelName = modelName.toString();
            return this;
        }
    }
}
