package dev.langchain4j.model.mistralai;

import dev.langchain4j.model.mistralai.internal.api.MistralAiModelCard;
import dev.langchain4j.model.mistralai.internal.api.MistralAiModelResponse;
import dev.langchain4j.model.mistralai.internal.client.MistralAiClient;
import dev.langchain4j.model.mistralai.spi.MistralAiModelsBuilderFactory;
import dev.langchain4j.model.output.Response;
import lombok.Builder;

import java.time.Duration;
import java.util.List;

import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;

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
    @Builder
    public MistralAiModels(String baseUrl,
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

    /**
     * Creates a new instance of MistralAiModels with the specified API key.
     *
     * @param apiKey the API key for authentication
     * @return a new instance of MistralAiModels
     */
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
        for (MistralAiModelsBuilderFactory factory : loadFactories(MistralAiModelsBuilderFactory.class)){
            return factory.get();
        }
        return new MistralAiModelsBuilder();
    }

    public static class MistralAiModelsBuilder {
        public MistralAiModelsBuilder(){
        }
    }
}
