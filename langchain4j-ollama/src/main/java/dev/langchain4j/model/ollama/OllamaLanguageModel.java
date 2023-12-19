package dev.langchain4j.model.ollama;

import dev.langchain4j.model.language.LanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import lombok.Builder;

import java.time.Duration;

import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

/**
 * Represents an Ollama language model with a completion interface
 */
public class OllamaLanguageModel implements LanguageModel {

    private final OllamaClient client;
    private final String modelName;
    private final Double temperature;
    private final Integer maxRetries;

    @Builder
    public OllamaLanguageModel(String baseUrl, Duration timeout, String modelName,
                               Double temperature, Integer maxRetries) {
        this.client = OllamaClient.builder().baseUrl(baseUrl).timeout(timeout).build();
        this.modelName = ensureNotBlank(modelName, "modelName");
        this.temperature = getOrDefault(temperature, 0.7);
        this.maxRetries = getOrDefault(maxRetries, 3);
    }

    @Override
    public Response<String> generate(String prompt) {
        CompletionRequest request = CompletionRequest.builder()
                .model(modelName)
                .prompt(prompt)
                .options(Options.builder()
                        .temperature(temperature)
                        .build())
                .stream(false)
                .build();

        CompletionResponse response = withRetry(() -> client.completion(request), maxRetries);

        return Response.from(
                response.getResponse(),
                new TokenUsage(response.getPromptEvalCount(), response.getEvalCount())
        );
    }
}
