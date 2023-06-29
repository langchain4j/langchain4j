package dev.langchain4j.model.openai;

import dev.ai4j.openai4j.OpenAiClient;
import dev.ai4j.openai4j.chat.ChatCompletionRequest;
import dev.ai4j.openai4j.chat.ChatCompletionResponse;
import dev.langchain4j.data.document.DocumentSegment;
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

import static dev.langchain4j.data.message.AiMessage.aiMessage;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.model.input.structured.StructuredPromptProcessor.toPrompt;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

public class OpenAiChatModel implements ChatLanguageModel, TokenCountEstimator {

    private static final double DEFAULT_TEMPERATURE = 0.7;
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);

    private final OpenAiClient client;
    private final String modelName;
    private final Double temperature;
    private final OpenAiTokenizer tokenizer;

    @Builder
    public OpenAiChatModel(String apiKey,
                           String modelName,
                           Double temperature,
                           Duration timeout,
                           boolean logRequests,
                           boolean logResponses) {
        this.client = OpenAiClient.builder()
                .apiKey(apiKey)
                .callTimeout(timeout == null ? DEFAULT_TIMEOUT : timeout)
                .connectTimeout(timeout == null ? DEFAULT_TIMEOUT : timeout)
                .readTimeout(timeout == null ? DEFAULT_TIMEOUT : timeout)
                .writeTimeout(timeout == null ? DEFAULT_TIMEOUT : timeout)
                .logRequests(logRequests)
                .logResponses(logResponses)
                .build();
        this.modelName = modelName == null ? OpenAiModelName.GPT_3_5_TURBO : modelName;
        this.temperature = temperature == null ? DEFAULT_TEMPERATURE : temperature;
        this.tokenizer = new OpenAiTokenizer(this.modelName);
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

        ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model(modelName)
                .messages(OpenAiConverters.toOpenAiMessages(messages))
                .temperature(temperature)
                .build();

        ChatCompletionResponse response = client.chatCompletion(request).execute();

        AiMessage aiMessage = aiMessage(response.content());

        return Result.from(aiMessage);
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
    public int estimateTokenCount(DocumentSegment documentSegment) {
        return estimateTokenCount(documentSegment.text());
    }
}
