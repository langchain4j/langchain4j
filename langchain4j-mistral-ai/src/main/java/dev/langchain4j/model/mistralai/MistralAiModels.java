package dev.langchain4j.model.mistralai;

import dev.langchain4j.model.output.Response;
import lombok.Builder;

import java.time.Duration;
import java.util.List;

import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.model.mistralai.DefaultMistralAiHelper.*;
import static java.util.stream.Collectors.toList;

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
     * @param baseUrl    the base URL of the Mistral AI API. It uses the default value if not specified
     * @param apiKey     the API key for authentication
     * @param timeout    the timeout duration for API requests. It uses the default value of 60 seconds if not specified
     * @param maxRetries the maximum number of retries for API requests. It uses the default value of 3 if not specified
     */
    @Builder
    public MistralAiModels(String baseUrl,
                           String apiKey,
                           Duration timeout,
                           Integer maxRetries) {
        this.client = MistralAiClient.builder()
                .baseUrl(formattedURLForRetrofit(getOrDefault(baseUrl, MISTRALAI_API_URL)))
                .apiKey(ensureNotBlankApiKey(apiKey))
                .timeout(getOrDefault(timeout, Duration.ofSeconds(60)))
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
     * Retrieves the details of a specific model.
     *
     * @param modelId the ID of the model
     * @return the response containing the model details
     */
    public Response<MistralModelCard> getModelDetails(String modelId){
        return Response.from(
                this.getModels().content().stream().filter(modelCard -> modelCard.getId().equals(modelId)).findFirst().orElse(null)
        );
    }

    /**
     * Retrieves the IDs of all available models.
     *
     * @return the response containing the list of model IDs
     */
    public Response<List<String>> get(){
        return Response.from(
                this.getModels().content().stream().map(MistralModelCard::getId).collect(toList())
        );
    }

    /**
     * Retrieves the list of all available models.
     *
     * @return the response containing the list of models
     */
    public Response<List<MistralModelCard>> getModels(){
        MistralModelResponse response = withRetry(client::listModels, maxRetries);
        return Response.from(
                response.getData()
        );
    }
}
