package dev.langchain4j.model.ollama;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import lombok.Builder;

import java.time.Duration;
import java.util.List;

import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static java.time.Duration.ofSeconds;
import static java.util.stream.Collectors.toList;

/**
 * <a href="https://github.com/jmorganca/ollama/blob/main/docs/api.md">Ollama API reference</a>
 * <br>
 * <a href="https://github.com/jmorganca/ollama/blob/main/docs/modelfile.md#valid-parameters-and-values">Ollama API parameters</a>.
 */
public class OllamaChatModel implements ChatLanguageModel {

    private final OllamaClient client;
    private final String modelName;
    private final Double temperature;
    private final Integer topK;
    private final Double topP;
    private final Double repeatPenalty;
    private final Integer seed;
    private final Integer numPredict;
    private final List<String> stop;
    private final String format;
    private final Integer maxRetries;

    @Builder
    public OllamaChatModel(String baseUrl,
                           String modelName,
                           Double temperature,
                           Integer topK,
                           Double topP,
                           Double repeatPenalty,
                           Integer seed,
                           Integer numPredict,
                           List<String> stop,
                           String format,
                           Duration timeout,
                           Integer maxRetries) {
        this.client = OllamaClient.builder()
                .baseUrl(baseUrl)
                .timeout(getOrDefault(timeout, ofSeconds(60)))
                .build();
        this.modelName = ensureNotBlank(modelName, "modelName");
        this.temperature = temperature;
        this.topK = topK;
        this.topP = topP;
        this.repeatPenalty = repeatPenalty;
        this.seed = seed;
        this.numPredict = numPredict;
        this.stop = stop;
        this.format = format;
        this.maxRetries = getOrDefault(maxRetries, 3);
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {
        ensureNotEmpty(messages, "messages");

        ChatRequest request = ChatRequest.builder()
                .model(modelName)
                .messages(toOllamaMessages(messages))
                .options(Options.builder()
                        .temperature(temperature)
                        .topK(topK)
                        .topP(topP)
                        .repeatPenalty(repeatPenalty)
                        .seed(seed)
                        .numPredict(numPredict)
                        .stop(stop)
                        .build())
                .format(format)
                .stream(false)
                .build();

        ChatResponse response = withRetry(() -> client.chat(request), maxRetries);

        return Response.from(
                AiMessage.from(response.getMessage().getContent()),
                new TokenUsage(response.getPromptEvalCount(), response.getEvalCount())
        );
    }

    static List<Message> toOllamaMessages(List<ChatMessage> messages) {
        return messages.stream()
                .map(message -> Message.builder()
                        .role(toOllamaRole(message.type()))
                        .content(message.text())
                        .build())
                .collect(toList());
    }

    private static Role toOllamaRole(ChatMessageType chatMessageType) {
        switch (chatMessageType) {
            case SYSTEM:
                return Role.SYSTEM;
            case USER:
                return Role.USER;
            case AI:
                return Role.ASSISTANT;
            default:
                throw new IllegalArgumentException("Unknown ChatMessageType: " + chatMessageType);
        }
    }
}
