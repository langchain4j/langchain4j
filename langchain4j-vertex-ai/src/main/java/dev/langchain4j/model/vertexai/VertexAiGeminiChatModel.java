package dev.langchain4j.model.vertexai;

import com.google.cloud.vertexai.VertexAI;
import com.google.cloud.vertexai.api.Content;
import com.google.cloud.vertexai.api.GenerateContentResponse;
import com.google.cloud.vertexai.api.GenerationConfig;
import com.google.cloud.vertexai.generativeai.preview.GenerativeModel;
import com.google.cloud.vertexai.generativeai.preview.ResponseHandler;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
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
        final List<Content> contents = VertexAiGeminiContentMapper.map(messages);

        final GenerateContentResponse response = withRetry(() -> model.generateContent(contents, generationConfig), maxRetries);

        final AiMessage aiMessage = AiMessage.from(ResponseHandler.getText(response));
        final TokenUsage tokenUsage = getTokenUsage(response.getUsageMetadata());
        final FinishReason finishReason = VertexAiGeminiFinishReasonMapper.map(ResponseHandler.getFinishReason(response));

        return Response.from(aiMessage, tokenUsage, finishReason);
    }

    private TokenUsage getTokenUsage(final GenerateContentResponse.UsageMetadata usageMetadata) {
        final int inputTokenCount = usageMetadata.getPromptTokenCount();
        final int outputTokenCount = usageMetadata.getCandidatesTokenCount();
        final int totalTokenCount = usageMetadata.getTotalTokenCount();
        return new TokenUsage(inputTokenCount, outputTokenCount, totalTokenCount);
    }

}
