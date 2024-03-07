package dev.langchain4j.model.vertexai;

import com.google.cloud.vertexai.VertexAI;
import com.google.cloud.vertexai.api.Content;
import com.google.cloud.vertexai.api.GenerationConfig;
import com.google.cloud.vertexai.api.Tool;
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

    private String project;
    private String location;
    private String modelName;
    private Float temperature;
    private Integer maxOutputTokens;
    private Integer topK;
    private Float topP;

    @Builder
    public VertexAiGeminiStreamingChatModel(String project,
                                            String location,
                                            String modelName,
                                            Float temperature,
                                            Integer maxOutputTokens,
                                            Integer topK,
                                            Float topP) {
        try (VertexAI vertexAI = new VertexAI(
                ensureNotBlank(project, "project"),
                ensureNotBlank(location, "location"))
        ) {
            this.generativeModel = new GenerativeModel(ensureNotBlank(modelName, "modelName"), vertexAI);
            this.project = project;
            this.location = location;
            this.modelName = modelName;
        }

        GenerationConfig.Builder generationConfigBuilder = GenerationConfig.newBuilder();
        if (temperature != null) {
            generationConfigBuilder.setTemperature(temperature);
            this.temperature = temperature;
        }
        if (maxOutputTokens != null) {
            generationConfigBuilder.setMaxOutputTokens(maxOutputTokens);
            this.maxOutputTokens = maxOutputTokens;
        }
        if (topK != null) {
            generationConfigBuilder.setTopK(topK);
            this.topK = topK;
        }
        if (topP != null) {
            generationConfigBuilder.setTopP(topP);
            this.topP = topP;
        }
        this.generationConfig = generationConfigBuilder.build();
    }

    public VertexAiGeminiStreamingChatModel(GenerativeModel generativeModel,
                                            GenerationConfig generationConfig) {
        this.generativeModel = ensureNotNull(generativeModel, "generativeModel");
        this.generationConfig = ensureNotNull(generationConfig, "generationConfig");
    }

    @Override
    public void generate(List<ChatMessage> messages, StreamingResponseHandler<AiMessage> handler) {

        List<Content> contents = ContentsMapper.map(messages);
        StreamingChatResponseBuilder responseBuilder = new StreamingChatResponseBuilder();

        try {
            generativeModel.generateContentStream(contents, generationConfig)
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

    private VertexAiGeminiStreamingChatModel copyModel() {
        return new VertexAiGeminiStreamingChatModel(
            this.project, this.location, this.modelName, this.temperature,
            this.maxOutputTokens, this.topK, this.topP
        );
    }

    @Override
    public void generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications, StreamingResponseHandler<AiMessage> handler) {
        Tool tool = FunctionCallHelper.convertToolSpecifications(toolSpecifications);
        VertexAiGeminiStreamingChatModel copiedModel = copyModel();
        copiedModel.generativeModel.setTools(Collections.singletonList(tool));
        copiedModel.generate(messages, handler);
    }

    @Override
    public void generate(List<ChatMessage> messages, ToolSpecification toolSpecification, StreamingResponseHandler<AiMessage> handler) {
        if (toolSpecification == null) {
            generate(messages, handler);
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