package dev.langchain4j.model.ollama;

import dev.langchain4j.model.output.Response;
import lombok.Builder;

import java.time.Duration;
import java.util.List;

import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;

public class OllamaModels {

    private final DefaultOllamaClient client;
    private final Integer maxRetries;

    @Builder
    public OllamaModels(String baseUrl,
                        Duration timeout,
                        Integer maxRetries,
                        Boolean logRequests,
                        Boolean logResponses
                        ) {
        this.client = DefaultOllamaClient.builder()
                .baseUrl(baseUrl)
                .timeout((getOrDefault(timeout, Duration.ofSeconds(60))))
                .logRequests(logRequests)
                .logResponses(logResponses)
                .build();
        this.maxRetries = getOrDefault(maxRetries, 3);
    }

    public Response<List<OllamaModel>> availableModels() {
        ModelsListResponse response = withRetry(client::listModels, maxRetries);
        return Response.from(response.getModels());
    }

    public Response<OllamaModelCard> modelCard(OllamaModel ollamaModel) {
        return modelCard(ollamaModel.getName());
    }

    public Response<OllamaModelCard> modelCard(String modelName) {
        OllamaModelCard response = withRetry(() -> client.showInformation(
                ShowModelInformationRequest.builder()
                        .name(modelName)
                        .build()
        ), maxRetries);
        return Response.from(response);
    }
}
