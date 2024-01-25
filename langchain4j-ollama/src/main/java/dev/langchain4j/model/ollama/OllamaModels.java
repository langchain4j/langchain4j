package dev.langchain4j.model.ollama;

import dev.langchain4j.model.output.Response;
import lombok.Builder;

import java.time.Duration;
import java.util.List;

import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;

public class OllamaModels {
    private final OllamaClient client;
    private final Integer maxRetries;

    @Builder
    public OllamaModels(String baseUrl,
                        Duration timeout,
                        Integer maxRetries) {
        this.client = OllamaClient.builder()
                .baseUrl(baseUrl)
                .timeout((getOrDefault(timeout, Duration.ofSeconds(60))))
                .build();
        this.maxRetries = getOrDefault(maxRetries, 3);
    }

    public Response<List<OllamaModel>> availableModels() {
        ModelsListResponse response = withRetry(client::listModels, maxRetries);
        return Response.from(response.getModels());
    }

    public Response<OllamaModelInfo> fetchModelInfo(OllamaModel ollamaModel) {
        return fetchModelInfo(ollamaModel.getName());
    }

    public Response<OllamaModelInfo> fetchModelInfo(String modelName) {
        ShowModelInformationResponse response = withRetry(() -> client.showInformation(
                ShowModelInformationRequest.builder()
                        .name(modelName)
                        .build()
        ), maxRetries);
        return Response.from(mapToModelInformation(response));
    }

    private OllamaModelInfo mapToModelInformation(ShowModelInformationResponse showInformationResponse) {
        return OllamaModelInfo.builder()
                .modelfile(showInformationResponse.getModelfile())
                .parameters(showInformationResponse.getParameters())
                .template(showInformationResponse.getTemplate())
                .details(showInformationResponse.getDetails())
                .build();
    }
}
