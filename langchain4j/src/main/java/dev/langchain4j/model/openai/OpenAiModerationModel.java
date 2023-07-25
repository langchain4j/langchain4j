package dev.langchain4j.model.openai;

import dev.ai4j.openai4j.OpenAiClient;
import dev.ai4j.openai4j.moderation.ModerationRequest;
import dev.ai4j.openai4j.moderation.ModerationResponse;
import dev.ai4j.openai4j.moderation.ModerationResult;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.moderation.Moderation;
import dev.langchain4j.model.moderation.ModerationModel;
import lombok.Builder;

import java.time.Duration;
import java.util.List;

import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.model.input.structured.StructuredPromptProcessor.toPrompt;
import static dev.langchain4j.model.openai.InternalOpenAiHelper.*;
import static dev.langchain4j.model.openai.OpenAiModelName.TEXT_MODERATION_LATEST;
import static java.time.Duration.ofSeconds;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

public class OpenAiModerationModel implements ModerationModel {

    private final OpenAiClient client;
    private final String modelName;
    private final Integer maxRetries;

    @Builder
    public OpenAiModerationModel(String apiKey,
                                 String modelName,
                                 Duration timeout,
                                 Integer maxRetries,
                                 Boolean logRequests,
                                 Boolean logResponses) {

        modelName = modelName == null ? TEXT_MODERATION_LATEST : modelName;
        timeout = timeout == null ? ofSeconds(15) : timeout;
        maxRetries = maxRetries == null ? 3 : maxRetries;

        String baseUrl = OPENAI_URL;
        if (OPENAI_DEMO_API_KEY.equals(apiKey)) {
            baseUrl = OPENAI_DEMO_URL;
        }

        this.client = OpenAiClient.builder()
                .apiKey(apiKey)
                .url(baseUrl)
                .callTimeout(timeout)
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .writeTimeout(timeout)
                .logRequests(logRequests)
                .logResponses(logResponses)
                .build();
        this.modelName = modelName;
        this.maxRetries = maxRetries;
    }

    @Override
    public Moderation moderate(String text) {
        return moderateInternal(singletonList(text));
    }

    private Moderation moderateInternal(List<String> inputs) {

        ModerationRequest request = ModerationRequest.builder()
                .model(modelName)
                .input(inputs)
                .build();

        ModerationResponse response = withRetry(() -> client.moderation(request).execute(), maxRetries);

        int i = 0;
        for (ModerationResult moderationResult : response.results()) {
            if (moderationResult.isFlagged()) {
                return Moderation.flagged(inputs.get(i));
            }
            i++;
        }

        return Moderation.notFlagged();
    }

    @Override
    public Moderation moderate(Prompt prompt) {
        return moderate(prompt.text());
    }

    @Override
    public Moderation moderate(Object structuredPrompt) {
        return moderate(toPrompt(structuredPrompt));
    }

    @Override
    public Moderation moderate(ChatMessage message) {
        return moderate(message.text());
    }

    @Override
    public Moderation moderate(List<ChatMessage> messages) {
        List<String> inputs = messages.stream()
                .map(ChatMessage::text)
                .collect(toList());

        return moderateInternal(inputs);
    }

    @Override
    public Moderation moderate(TextSegment textSegment) {
        return moderate(textSegment.text());
    }

    public static OpenAiModerationModel withApiKey(String apiKey) {
        return builder().apiKey(apiKey).build();
    }
}
