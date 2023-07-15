package dev.langchain4j.model.openai;

import dev.ai4j.openai4j.OpenAiClient;
import dev.ai4j.openai4j.chat.ChatCompletionRequest;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.StreamingResultHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.TokenCountEstimator;
import dev.langchain4j.model.input.Prompt;
import lombok.Builder;

import java.time.Duration;
import java.util.List;

import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.model.input.structured.StructuredPromptProcessor.toPrompt;
import static dev.langchain4j.model.openai.OpenAiModelName.GPT_3_5_TURBO;
import static java.util.Collections.singletonList;

public class OpenAiStreamingChatModel implements StreamingChatLanguageModel, TokenCountEstimator {

    private static final double DEFAULT_TEMPERATURE = 0.7;
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(15);

    private final OpenAiClient client;
    private final String modelName;
    private final Double temperature;
    private final OpenAiTokenizer tokenizer;

    @Builder
    public OpenAiStreamingChatModel(String apiKey,
                                    String modelName,
                                    Double temperature,
                                    Duration timeout,
                                    Boolean logRequests,
                                    Boolean logResponses) {
        this.client = OpenAiClient.builder()
                .apiKey(apiKey)
                .callTimeout(timeout == null ? DEFAULT_TIMEOUT : timeout)
                .connectTimeout(timeout == null ? DEFAULT_TIMEOUT : timeout)
                .readTimeout(timeout == null ? DEFAULT_TIMEOUT : timeout)
                .writeTimeout(timeout == null ? DEFAULT_TIMEOUT : timeout)
                .logRequests(logRequests)
                .logResponses(logResponses)
                .build();
        this.modelName = modelName == null ? GPT_3_5_TURBO : modelName;
        this.temperature = temperature == null ? DEFAULT_TEMPERATURE : temperature;
        this.tokenizer = new OpenAiTokenizer(this.modelName);
    }

    @Override
    public void sendUserMessage(String text, StreamingResultHandler handler) {
        sendUserMessage(userMessage(text), handler);
    }

    @Override
    public void sendUserMessage(UserMessage userMessage, StreamingResultHandler handler) {
        sendMessages(singletonList(userMessage), handler);
    }

    @Override
    public void sendUserMessage(Prompt prompt, StreamingResultHandler handler) {
        sendUserMessage(prompt.text(), handler);
    }

    @Override
    public void sendUserMessage(Object structuredPrompt, StreamingResultHandler handler) {
        sendUserMessage(toPrompt(structuredPrompt), handler);
    }

    @Override
    public void sendMessages(List<ChatMessage> messages, StreamingResultHandler handler) {

        ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model(modelName)
                .messages(OpenAiConverters.toOpenAiMessages(messages))
                .temperature(temperature)
                .stream(true)
                .build();

        client.chatCompletion(request)
                .onPartialResponse(partialResponse -> {
                    String content = partialResponse.choices().get(0).delta().content();
                    if (content != null) {
                        handler.onPartialResult(content);
                    }
                })
                .onComplete(handler::onComplete)
                .onError(handler::onError)
                .execute();
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

    public static OpenAiStreamingChatModel withApiKey(String apiKey) {
        return builder().apiKey(apiKey).build();
    }
}
