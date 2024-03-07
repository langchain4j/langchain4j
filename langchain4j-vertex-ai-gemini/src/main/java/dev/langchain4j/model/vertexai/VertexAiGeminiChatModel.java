package dev.langchain4j.model.vertexai;

import com.google.cloud.vertexai.VertexAI;
import com.google.cloud.vertexai.api.*;
import com.google.cloud.vertexai.generativeai.GenerativeModel;
import com.google.cloud.vertexai.generativeai.ResponseHandler;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.vertexai.spi.VertexAiGeminiChatModelBuilderFactory;
import lombok.Builder;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;

/**
 * Represents a Google Vertex AI Gemini language model with a chat completion interface, such as gemini-pro.
 * See details <a href="https://cloud.google.com/vertex-ai/docs/generative-ai/model-reference/gemini">here</a>.
 * <br>
 * Please follow these steps before using this model:
 * <br>
 * 1. <a href="https://github.com/googleapis/java-aiplatform?tab=readme-ov-file#authentication">Authentication</a>
 * <br>
 * When developing locally, you can use one of:
 * <br>
 * a) <a href="https://github.com/googleapis/google-cloud-java?tab=readme-ov-file#local-developmenttesting">Google Cloud SDK</a>
 * <br>
 * b) <a href="https://github.com/googleapis/google-cloud-java?tab=readme-ov-file#using-a-service-account-recommended">Service account</a>
 * When using service account, ensure that <code>GOOGLE_APPLICATION_CREDENTIALS</code> environment variable points to your JSON service account key.
 * <br>
 * 2. <a href="https://github.com/googleapis/java-aiplatform?tab=readme-ov-file#authorization">Authorization</a>
 * <br>
 * 3. <a href="https://github.com/googleapis/java-aiplatform?tab=readme-ov-file#prerequisites">Prerequisites</a>
 */
public class VertexAiGeminiChatModel implements ChatLanguageModel {

    private final GenerativeModel generativeModel;
    private final GenerationConfig generationConfig;
    private final Integer maxRetries;

    private String project;
    private String location;
    private String modelName;
    private Float temperature;
    private Integer maxOutputTokens;
    private Integer topK;
    private Float topP;

    @Builder
    public VertexAiGeminiChatModel(String project,
                                   String location,
                                   String modelName,
                                   Float temperature,
                                   Integer maxOutputTokens,
                                   Integer topK,
                                   Float topP,
                                   Integer maxRetries) {

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

        this.maxRetries = getOrDefault(maxRetries, 3);
    }

    public VertexAiGeminiChatModel(GenerativeModel generativeModel,
                                   GenerationConfig generationConfig) {
        this.generativeModel = ensureNotNull(generativeModel, "generativeModel");
        this.generationConfig = ensureNotNull(generationConfig, "generationConfig");
        this.maxRetries = 3;
    }

    public VertexAiGeminiChatModel(GenerativeModel generativeModel,
                                   GenerationConfig generationConfig,
                                   Integer maxRetries) {
        this.generativeModel = ensureNotNull(generativeModel, "generativeModel");
        this.generationConfig = ensureNotNull(generationConfig, "generationConfig");
        this.maxRetries = getOrDefault(maxRetries, 3);
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {
        List<Content> contents = ContentsMapper.map(messages);
        GenerateContentResponse response = withRetry(() -> generativeModel.generateContent(contents, generationConfig), maxRetries);

        Content content = ResponseHandler.getContent(response);

        List<FunctionCall> functionCalls = content.getPartsList().stream()
            .filter(Part::hasFunctionCall)
            .map(Part::getFunctionCall)
            .collect(Collectors.toList());

        if (!functionCalls.isEmpty()) {
            List<ToolExecutionRequest> toolExecutionRequests = FunctionCallHelper.fromFunctionCalls(functionCalls);

            return Response.from(
                AiMessage.from(toolExecutionRequests),
                TokenUsageMapper.map(response.getUsageMetadata()),
                FinishReasonMapper.map(ResponseHandler.getFinishReason(response))
            );
        } else {
            return Response.from(
                AiMessage.from(ResponseHandler.getText(response)),
                TokenUsageMapper.map(response.getUsageMetadata()),
                FinishReasonMapper.map(ResponseHandler.getFinishReason(response))
            );
        }
    }

    private VertexAiGeminiChatModel copyModel() {
        return new VertexAiGeminiChatModel(
            this.project, this.location, this.modelName, this.temperature,
            this.maxOutputTokens, this.topK, this.topP, this.maxRetries
        );
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
        Tool tool = FunctionCallHelper.convertToolSpecifications(toolSpecifications);
        VertexAiGeminiChatModel copiedModel = copyModel();
        copiedModel.generativeModel.setTools(Collections.singletonList(tool));
        return copiedModel.generate(messages);
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, ToolSpecification toolSpecification) {
        if (toolSpecification == null) {
            return generate(messages);
        } else {
            return generate(messages, Collections.singletonList(toolSpecification));
        }
    }

    public static VertexAiGeminiChatModelBuilder builder() {
        for (VertexAiGeminiChatModelBuilderFactory factory : loadFactories(VertexAiGeminiChatModelBuilderFactory.class)) {
            return factory.get();
        }
        return new VertexAiGeminiChatModelBuilder();
    }

    public static class VertexAiGeminiChatModelBuilder {
        public VertexAiGeminiChatModelBuilder() {
            // This is public so it can be extended
            // By default with Lombok it becomes package private
        }
    }
}
