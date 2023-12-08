package dev.langchain4j.model.ollama;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import lombok.Builder;

import java.time.Duration;
import java.util.List;

import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static java.util.stream.Collectors.joining;

public class OllamaChatModel implements ChatLanguageModel {

    private final OllamaClient client;
    private final String modelName;
    private final Double temperature;
    private final Integer maxRetries;

    @Builder
    public OllamaChatModel(String baseUrl,
                           String modelName,
                           Double temperature,
                           Duration timeout,
                           Integer maxRetries) {
        this.client = OllamaClient.builder().baseUrl(baseUrl).timeout(timeout).build();
        this.modelName = modelName;
        this.temperature = getOrDefault(temperature, 0.7);
        this.maxRetries = getOrDefault(maxRetries, 3);
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {
        CompletionRequest request = CompletionRequest.builder()
                .model(modelName)
                .prompt(messages.stream()
                        .map(ChatMessage::text)
                        .collect(joining("\n")))
                .options(Options.builder()
                        .temperature(temperature)
                        .build())
                .build();

        CompletionResponse textGenerationResponse = withRetry(() -> client.completion(request), maxRetries);

        return Response.from(AiMessage.from(textGenerationResponse.getResponse()));

    }
}
