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
import dev.langchain4j.model.output.Response;
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

    private final GenerationConfig generationConfig;
    private final GenerativeModel model;
    private final Integer maxRetries;

    @Builder
    public VertexAiGeminiChatModel(String project,
                                   String location,
                                   String modelName,
                                   Float temperature,
                                   Integer maxOutputTokens,
                                   Integer topK,
                                   Float topP,
                                   Integer maxRetries) {

        GenerationConfig.Builder generationConfigBuilder = GenerationConfig.newBuilder();
        if (temperature != null) {
            generationConfigBuilder.setTemperature(temperature);
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
        this.generationConfig = generationConfigBuilder.build();

        try (VertexAI vertexAI = new VertexAI(
                ensureNotBlank(project, "project"),
                ensureNotBlank(location, "location"))
        ) {
            this.model = new GenerativeModel(ensureNotBlank(modelName, "modelName"), vertexAI);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        this.maxRetries = getOrDefault(maxRetries, 3);
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {

        List<Content> contents = ContentsMapper.map(messages);

        GenerateContentResponse response = withRetry(() -> model.generateContent(contents, generationConfig), maxRetries);

        return Response.from(
                AiMessage.from(ResponseHandler.getText(response)),
                TokenUsageMapper.map(response.getUsageMetadata()),
                FinishReasonMapper.map(ResponseHandler.getFinishReason(response))
        );
    }
}
