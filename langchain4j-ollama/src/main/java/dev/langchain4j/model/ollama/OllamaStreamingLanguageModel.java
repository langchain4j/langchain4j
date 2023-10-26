package dev.langchain4j.model.ollama;

import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.language.StreamingLanguageModel;
import lombok.Builder;

import java.time.Duration;

import static dev.langchain4j.internal.Utils.getOrDefault;

/**
 * TODO
 */
public class OllamaStreamingLanguageModel implements StreamingLanguageModel {

    private final OllamaClient client;
    private final String modelName;
    private final Double temperature;

    @Builder
    public OllamaStreamingLanguageModel(String baseUrl, Duration timeout,
                                        String modelName, Double temperature) {
        this.client = OllamaClient.builder().baseUrl(baseUrl).timeout(timeout).build();
        this.modelName = modelName;
        this.temperature = getOrDefault(temperature, 0.7);
    }

    @Override
    public void generate(String prompt, StreamingResponseHandler<String> handler) {
        CompletionRequest request = CompletionRequest.builder()
                .model(modelName)
                .prompt(prompt)
                .options(Options.builder()
                        .temperature(temperature)
                        .build())
                .stream(true)
                .build();

        client.streamingCompletion(request, handler);
    }
}
