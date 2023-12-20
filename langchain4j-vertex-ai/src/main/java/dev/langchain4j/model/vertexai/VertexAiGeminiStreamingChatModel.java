package dev.langchain4j.model.vertexai;

import com.google.cloud.vertexai.VertexAI;
import com.google.cloud.vertexai.api.Content;
import com.google.cloud.vertexai.api.GenerationConfig;
import com.google.cloud.vertexai.generativeai.preview.GenerativeModel;
import com.google.cloud.vertexai.generativeai.preview.ResponseHandler;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;
import lombok.Builder;

import java.io.IOException;
import java.util.List;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

/**
 * Represents a Google Vertex AI Gemini language model with a stream chat completion interface, such as gemini-pro.
 * See details <a href="https://cloud.google.com/vertex-ai/docs/generative-ai/model-reference/gemini">here</a>.
 */
public class VertexAiGeminiStreamingChatModel implements StreamingChatLanguageModel {

    private final GenerationConfig generationConfig;
    private final GenerativeModel model;

    @Builder
    public VertexAiGeminiStreamingChatModel(String project,
                                            String location,
                                            String modelName,
                                            Float temperature,
                                            Integer maxOutputTokens,
                                            Integer topK,
                                            Float topP) {

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
    public void generate(List<ChatMessage> messages, StreamingResponseHandler<AiMessage> handler) {
        final List<Content> contents = VertexAiGeminiContentMapper.map(messages);
        final VertexAiGeminiStreamingChatResponseBuilder responseBuilder = new VertexAiGeminiStreamingChatResponseBuilder();

        try {
            model.generateContentStream(contents, generationConfig)
                    .stream()
                    .forEach(generateContentResponse -> {
                        responseBuilder.append(generateContentResponse);
                        handler.onNext(ResponseHandler.getText(generateContentResponse));
                    });
            Response<AiMessage> response = responseBuilder.build();
            handler.onComplete(response);
        } catch (Exception exception) {
            handler.onError(exception);
        }
    }

}