package dev.langchain4j.model.ollama;

import dev.langchain4j.model.language.LanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import lombok.Builder;

import java.time.Duration;

import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static java.time.Duration.ofSeconds;

/**
 * <a href="https://github.com/jmorganca/ollama/blob/main/docs/api.md">Ollama API reference</a>
 * <br>
 * <a href="https://github.com/jmorganca/ollama/blob/main/docs/modelfile.md#valid-parameters-and-values">Ollama API parameters</a>.
 */
public class OllamaLanguageModel implements LanguageModel {

    private final OllamaClient client;
    private final String modelName;
    private final Double temperature;
    private final Integer numPredict;
    private final String format;
    private final Integer maxRetries;

    @Builder
    public OllamaLanguageModel(String baseUrl,
                               String modelName,
                               Double temperature,
                               Integer numPredict,
                               String format,
                               Duration timeout,
                               Integer maxRetries) {
        this.client = OllamaClient.builder()
                .baseUrl(baseUrl)
                .timeout(getOrDefault(timeout, ofSeconds(60)))
                .build();
        this.modelName = ensureNotBlank(modelName, "modelName");
        this.temperature = temperature;
        this.numPredict = numPredict;
        this.format = format;
        this.maxRetries = getOrDefault(maxRetries, 3);
    }

    @Override
    public Response<String> generate(String prompt) {

        CompletionRequest request = CompletionRequest.builder()
                .model(modelName)
                .prompt(prompt)
                .options(Options.builder()
                        .temperature(temperature)
                        .numPredict(numPredict)
                        .build())
                .format(format)
                .stream(false)
                .build();

        CompletionResponse response = withRetry(() -> client.completion(request), maxRetries);

        return Response.from(
                response.getResponse(),
                new TokenUsage(response.getPromptEvalCount(), response.getEvalCount())
        );
    }
}
