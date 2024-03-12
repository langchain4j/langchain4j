package dev.langchain4j.model.vertexai;

import com.google.cloud.vertexai.VertexAI;
import com.google.cloud.vertexai.api.Content;
import com.google.cloud.vertexai.api.GenerationConfig;
import com.google.cloud.vertexai.api.Tool;
import com.google.cloud.vertexai.generativeai.GenerateContentConfig;
import com.google.cloud.vertexai.generativeai.GenerativeModel;
import com.google.cloud.vertexai.generativeai.ResponseHandler;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.vertexai.spi.VertexAiGeminiStreamingChatModelBuilderFactory;
import lombok.Builder;

import java.util.Collections;
import java.util.List;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;

/**
 * Represents a Google Vertex AI Gemini language model with a stream chat completion interface, such as gemini-pro.
 * See details <a href="https://cloud.google.com/vertex-ai/docs/generative-ai/model-reference/gemini">here</a>.
 */
public class VertexAiGeminiStreamingChatModel implements StreamingChatLanguageModel {

    private final GenerativeModel generativeModel;
    private final GenerationConfig generationConfig;

    @Builder
    public VertexAiGeminiStreamingChatModel(String project,
                                            String location,
                                            String modelName,
                                            Float temperature,
                                            Integer maxOutputTokens,
                                            Integer topK,
                                            Float topP) {
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
            this.generativeModel = new GenerativeModel(
                ensureNotBlank(modelName, "modelName"), generationConfig, vertexAI);
        }
    }

    public VertexAiGeminiStreamingChatModel(GenerativeModel generativeModel,
                                            GenerationConfig generationConfig) {
        this.generativeModel = ensureNotNull(generativeModel, "generativeModel");
        this.generationConfig = ensureNotNull(generationConfig, "generationConfig");
    }

    @Override
    public void generate(List<ChatMessage> messages, StreamingResponseHandler<AiMessage> handler) {
        generate(messages, Collections.emptyList(), handler);
    }

    @Override
    public void generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications, StreamingResponseHandler<AiMessage> handler) {
        List<Content> contents = ContentsMapper.map(messages);

        GenerateContentConfig.Builder generateContentConfigBuilder = GenerateContentConfig.newBuilder()
            .setGenerationConfig(generationConfig);

        if (toolSpecifications != null && !toolSpecifications.isEmpty()) {
            Tool tool = FunctionCallHelper.convertToolSpecifications(toolSpecifications);
            generateContentConfigBuilder.setTools(Collections.singletonList(tool));
        }
        GenerateContentConfig generateContentConfig = generateContentConfigBuilder.build();
        StreamingChatResponseBuilder responseBuilder = new StreamingChatResponseBuilder();

        try {
            generativeModel.generateContentStream(contents, generateContentConfig)
                .stream()
                .forEach(partialResponse -> {
                    responseBuilder.append(partialResponse);
                    handler.onNext(ResponseHandler.getText(partialResponse));
                });
            handler.onComplete(responseBuilder.build());
        } catch (Exception exception) {
            handler.onError(exception);
        }

    }

    @Override
    public void generate(List<ChatMessage> messages, ToolSpecification toolSpecification, StreamingResponseHandler<AiMessage> handler) {
        if (toolSpecification == null) {
            generate(messages, Collections.emptyList(), handler);
        } else {
            generate(messages, Collections.singletonList(toolSpecification), handler);
        }
    }

    public static VertexAiGeminiStreamingChatModelBuilder builder() {
        for (VertexAiGeminiStreamingChatModelBuilderFactory factory : loadFactories(VertexAiGeminiStreamingChatModelBuilderFactory.class)) {
            return factory.get();
        }
        return new VertexAiGeminiStreamingChatModelBuilder();
    }

    public static class VertexAiGeminiStreamingChatModelBuilder {
        public VertexAiGeminiStreamingChatModelBuilder() {
            // This is public so it can be extended
            // By default with Lombok it becomes package private
        }
    }
}