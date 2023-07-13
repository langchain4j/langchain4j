package dev.langchain4j.model.openai;

import dev.ai4j.openai4j.OpenAiClient;
import dev.ai4j.openai4j.chat.ChatCompletionRequest;
import dev.ai4j.openai4j.chat.ChatCompletionResponse;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.TokenCountEstimator;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.output.Result;
import lombok.Builder;

import java.time.Duration;
import java.util.List;

import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.model.input.structured.StructuredPromptProcessor.toPrompt;
import static dev.langchain4j.model.openai.OpenAiConverters.aiMessageFrom;
import static dev.langchain4j.model.openai.OpenAiConverters.toFunctions;
import static dev.langchain4j.model.openai.OpenAiModelName.GPT_3_5_TURBO;
import static dev.langchain4j.model.openai.OpenAiModelName.GPT_4;
import static dev.langchain4j.internal.RetryUtils.withRetry;
import static java.time.Duration.ofSeconds;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

public class OpenAiChatModel implements ChatLanguageModel, TokenCountEstimator {

    private final OpenAiClient client;
    private final String modelName;
    private final Double temperature;
    private final Integer maxRetries;
    private final OpenAiTokenizer tokenizer;

    @Builder
    public OpenAiChatModel(String apiKey,
                           String modelName,
                           Double temperature,
                           Duration timeout,
                           Integer maxRetries,
                           Boolean logRequests,
                           Boolean logResponses) {

        modelName = modelName == null ? GPT_3_5_TURBO : modelName;
        temperature = temperature == null ? 0.7 : temperature;
        timeout = timeout == null ? defaultTimeoutFor(modelName) : timeout;
        maxRetries = maxRetries == null ? 3 : maxRetries;

        this.client = OpenAiClient.builder()
                .apiKey(apiKey)
                .callTimeout(timeout)
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .writeTimeout(timeout)
                .logRequests(logRequests)
                .logResponses(logResponses)
                .build();
        this.modelName = modelName;
        this.temperature = temperature;
        this.maxRetries = maxRetries;
        this.tokenizer = new OpenAiTokenizer(this.modelName);
    }

    private static Duration defaultTimeoutFor(String modelName) {
        if (modelName.startsWith(GPT_3_5_TURBO)) {
            return ofSeconds(7);
        } else if (modelName.startsWith(GPT_4)) {
            return ofSeconds(20);
        }

        return ofSeconds(10);
    }

    @Override
    public Result<AiMessage> sendUserMessage(String text) {
        return sendMessages(userMessage(text));
    }

    @Override
    public Result<AiMessage> sendUserMessage(Prompt userMessage) {
        return sendUserMessage(userMessage.text());
    }

    @Override
    public Result<AiMessage> sendUserMessage(Object structuredPrompt) {
        return sendUserMessage(toPrompt(structuredPrompt));
    }

    @Override
    public Result<AiMessage> sendMessages(ChatMessage... messages) {
        return sendMessages(asList(messages));
    }

    @Override
    public Result<AiMessage> sendMessages(List<ChatMessage> messages) {
        return sendMessages(messages, null);
    }

    @Override
    public Result<AiMessage> sendMessages(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {

        ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model(modelName)
                .messages(OpenAiConverters.toOpenAiMessages(messages))
                .functions(toFunctions(toolSpecifications))
                .temperature(temperature)
                .build();

        ChatCompletionResponse response = withRetry(() -> client.chatCompletion(request).execute(), maxRetries);

        return Result.from(aiMessageFrom(response));
    }

    @Override
    public int estimateTokenCount(String text) {
        return estimateTokenCount(userMessage(text));
    }

    @Override
    public int estimateTokenCount(UserMessage userMessage) {
        return estimateTokenCount(singletonList(userMessage));
    }

    @Override
    public int estimateTokenCount(Prompt prompt) {
        return estimateTokenCount(prompt.text());
    }

    @Override
    public int estimateTokenCount(Object structuredPrompt) {
        return estimateTokenCount(toPrompt(structuredPrompt));
    }

    @Override
    public int estimateTokenCount(List<ChatMessage> messages) {
        return tokenizer.countTokens(messages);
    }

    @Override
    public int estimateTokenCount(TextSegment textSegment) {
        return estimateTokenCount(textSegment.text());
    }

    public static OpenAiChatModel withApiKey(String apiKey) {
        return builder().apiKey(apiKey).build();
    }
}
