package dev.langchain4j.model.vertexai;

import com.google.cloud.vertexai.VertexAI;
import com.google.cloud.vertexai.api.Candidate;
import com.google.cloud.vertexai.api.Content;
import com.google.cloud.vertexai.api.GenerateContentResponse;
import com.google.cloud.vertexai.api.GenerationConfig;
import com.google.cloud.vertexai.api.Part;
import com.google.cloud.vertexai.generativeai.preview.GenerativeModel;
import com.google.cloud.vertexai.generativeai.preview.ResponseHandler;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import lombok.Builder;

import java.io.IOException;
import java.util.List;

import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static java.util.stream.Collectors.toList;

/**
 * Represents a Google Vertex AI Gemini language model with a chat completion interface, such as gemini-pro.
 * See details <a href="https://cloud.google.com/vertex-ai/docs/generative-ai/model-reference/gemini">here</a>.
 */
public class VertexAiGeminiChatModel implements ChatLanguageModel {

    private final Integer maxRetries;
    private final GenerationConfig generationConfig;
    private final GenerativeModel model;

    @Builder
    public VertexAiGeminiChatModel(String project,
                                   String location,
                                   String modelName,
                                   Float temperature,
                                   Integer maxOutputTokens,
                                   Integer topK,
                                   Float topP,
                                   Integer maxRetries) {

        GenerationConfig.Builder generationConfigBuilder = GenerationConfig.newBuilder()
                .setTemperature(getOrDefault(temperature, 0f));

        if (maxOutputTokens != null) {
            generationConfigBuilder.setMaxOutputTokens(maxOutputTokens);
        }
        if (maxOutputTokens != null) {
            generationConfigBuilder.setMaxOutputTokens(maxOutputTokens);
        }
        if (topK != null) {
            generationConfigBuilder.setTopK(topK);
        }
        if (topP != null) {
            generationConfigBuilder.setTopP(topP);
        }

        generationConfig = generationConfigBuilder.build();

        this.maxRetries = maxRetries == null ? 3 : maxRetries;
        ensureNotBlank(project, "project");
        ensureNotBlank(location, "location");
        ensureNotBlank(modelName, "modelName");

        try (final VertexAI vertexAI = new VertexAI(project, location)) {
            model = new GenerativeModel(modelName, vertexAI);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {
        final List<Content> contents = toContents(messages);

        final GenerateContentResponse response = withRetry(() -> model.generateContent(contents, generationConfig), maxRetries);

        final AiMessage aiMessage = AiMessage.from(ResponseHandler.getText(response));
        final TokenUsage tokenUsage = getTokenUsage(response);
        final FinishReason finishReason = toFinishReason(ResponseHandler.getFinishReason(response));

        return Response.from(aiMessage, tokenUsage, finishReason);
    }

    private List<Content> toContents(List<ChatMessage> messages) {
        return messages.stream()
                .map(chatMessage -> Content.newBuilder()
                        .setRole(mapRole(chatMessage.type()))
                        .addParts(Part.newBuilder()
                                .setText(chatMessage.text())
                                .build())
                        .build())
                .collect(toList());
    }

    private String mapRole(ChatMessageType type) {
        switch (type) {
            case SYSTEM:
            case USER:
                return "user";
            case AI:
                return "model";
        }
        throw new IllegalArgumentException(type + " is not allowed.");
    }

    private FinishReason toFinishReason(Candidate.FinishReason finishReason) {
        switch (finishReason) {
            case STOP:
                return FinishReason.STOP;
            case MAX_TOKENS:
                return FinishReason.LENGTH;
            case SAFETY:
                return FinishReason.CONTENT_FILTER;
        }
        return FinishReason.OTHER;
    }

    private TokenUsage getTokenUsage(GenerateContentResponse response) {
        final GenerateContentResponse.UsageMetadata usageMetadata = response.getUsageMetadata();
        final int inputTokenCount = usageMetadata.getPromptTokenCount();
        final int outputTokenCount = usageMetadata.getCandidatesTokenCount();
        final int totalTokenCount = usageMetadata.getTotalTokenCount();
        return new TokenUsage(inputTokenCount, outputTokenCount, totalTokenCount);
    }

}
