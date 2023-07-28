package dev.langchain4j.model.localai;

import dev.ai4j.openai4j.OpenAiClient;
import dev.ai4j.openai4j.chat.ChatCompletionRequest;
import dev.ai4j.openai4j.chat.ChatCompletionResponse;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.Builder;

import java.time.Duration;
import java.util.List;

import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.model.openai.InternalOpenAiHelper.*;
import static java.time.Duration.ofSeconds;

public class LocalAiChatModel implements ChatLanguageModel {

    private final OpenAiClient client;
    private final String modelName;
    private final Double temperature;
    private final Double topP;
    private final Integer maxTokens;
    private final Integer maxRetries;

    @Builder
    public LocalAiChatModel(String baseUrl,
                            String modelName,
                            Double temperature,
                            Double topP,
                            Integer maxTokens,
                            Duration timeout,
                            Integer maxRetries,
                            Boolean logRequests,
                            Boolean logResponses) {

        temperature = temperature == null ? 0.7 : temperature;
        timeout = timeout == null ? ofSeconds(60) : timeout;
        maxRetries = maxRetries == null ? 3 : maxRetries;

        this.client = OpenAiClient.builder()
                .apiKey("ignored")
                .url(ensureNotBlank(baseUrl, "baseUrl"))
                .callTimeout(timeout)
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .writeTimeout(timeout)
                .logRequests(logRequests)
                .logResponses(logResponses)
                .build();
        this.modelName = ensureNotBlank(modelName, "modelName");
        this.temperature = temperature;
        this.topP = topP;
        this.maxTokens = maxTokens;
        this.maxRetries = maxRetries;
    }

    @Override
    public AiMessage sendMessages(List<ChatMessage> messages) {
        return sendMessages(messages, null);
    }

    @Override
    public AiMessage sendMessages(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {

        ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model(modelName)
                .messages(toOpenAiMessages(messages))
                .functions(toFunctions(toolSpecifications))
                .temperature(temperature)
                .topP(topP)
                .maxTokens(maxTokens)
                .build();

        ChatCompletionResponse response = withRetry(() -> client.chatCompletion(request).execute(), maxRetries);

        return aiMessageFrom(response);
    }
}
