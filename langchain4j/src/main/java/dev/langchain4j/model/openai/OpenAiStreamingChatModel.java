package dev.langchain4j.model.openai;

import dev.ai4j.openai4j.OpenAiClient;
import dev.ai4j.openai4j.chat.ChatCompletionRequest;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.StreamingResultHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.TokenCountEstimator;
import dev.langchain4j.model.input.Prompt;
import lombok.Builder;

import java.time.Duration;
import java.util.List;

import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.model.input.structured.StructuredPromptProcessor.toPrompt;
import static dev.langchain4j.model.openai.OpenAiHelper.toOpenAiMessages;
import static dev.langchain4j.model.openai.OpenAiModelName.GPT_3_5_TURBO;
import static java.time.Duration.ofSeconds;
import static java.util.Collections.singletonList;

public class OpenAiStreamingChatModel implements StreamingChatLanguageModel, TokenCountEstimator {

    private final OpenAiClient client;
    private final String modelName;
    private final Double temperature;
    private final Double topP;
    private final Integer maxTokens;
    private final Double presencePenalty;
    private final Double frequencyPenalty;
    private final OpenAiTokenizer tokenizer;

    @Builder
    public OpenAiStreamingChatModel(String apiKey,
                                    String modelName,
                                    Double temperature,
                                    Double topP,
                                    Integer maxTokens,
                                    Double presencePenalty,
                                    Double frequencyPenalty,
                                    Duration timeout,
                                    Boolean logRequests,
                                    Boolean logResponses) {

        modelName = modelName == null ? GPT_3_5_TURBO : modelName;
        temperature = temperature == null ? 0.7 : temperature;
        timeout = timeout == null ? ofSeconds(5) : timeout;

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
        this.topP = topP;
        this.maxTokens = maxTokens;
        this.presencePenalty = presencePenalty;
        this.frequencyPenalty = frequencyPenalty;
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
    public void sendUserMessage(Object structuredPrompt, StreamingResultHandler handler) {
        Prompt prompt = toPrompt(structuredPrompt);
        sendUserMessage(prompt.toUserMessage(), handler);
    }

    @Override
    public void sendMessages(List<ChatMessage> messages, StreamingResultHandler handler) {

        ChatCompletionRequest request = ChatCompletionRequest.builder()
                .stream(true)
                .model(modelName)
                .messages(toOpenAiMessages(messages))
                .temperature(temperature)
                .topP(topP)
                .maxTokens(maxTokens)
                .presencePenalty(presencePenalty)
                .frequencyPenalty(frequencyPenalty)
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
