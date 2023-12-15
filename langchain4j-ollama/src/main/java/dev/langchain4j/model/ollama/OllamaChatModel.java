package dev.langchain4j.model.ollama;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import lombok.Builder;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

/**
 * Ollama chat model implementation.
 */
public class OllamaChatModel implements ChatLanguageModel {

    private final OllamaClient client;
    private final Double temperature;
    private final String modelName;
    private final Integer maxRetries;

    @Builder
    public OllamaChatModel(String baseUrl, Duration timeout,
                           String modelName, Double temperature, Integer maxRetries) {
        this.client = OllamaClient.builder().baseUrl(baseUrl).timeout(timeout).build();
        this.modelName = ensureNotBlank(modelName, "modelName");
        this.temperature = getOrDefault(temperature, 0.7);
        this.maxRetries = getOrDefault(maxRetries, 3);
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            throw new IllegalArgumentException("messages must not be null or empty");
        }

        ArrayList<Message> messageList = new ArrayList<>();

        messages.forEach(message -> {
            Role role;

            if(message instanceof SystemMessage) {
                role = Role.SYSTEM;
            } else if (message instanceof AiMessage) {
                role = Role.ASSISTANT;
            } else {
                role = Role.USER;
            }

            messageList.add(Message.builder()
                    .role(role)
                    .content(message.text())
                    .build());
        });

        ChatRequest request = ChatRequest.builder()
                .model(modelName)
                .messages(messageList)
                .options(Options.builder()
                        .temperature(temperature)
                        .build())
                .stream(false)
                .build();

        ChatResponse response = withRetry(() -> client.completion(request), maxRetries);

        return Response.from(AiMessage.from(String.valueOf(response.getMessage())));
    }
}
