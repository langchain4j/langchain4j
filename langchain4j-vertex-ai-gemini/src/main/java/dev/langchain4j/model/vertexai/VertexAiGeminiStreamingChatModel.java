package dev.langchain4j.model.vertexai;

import com.google.cloud.vertexai.VertexAI;
import com.google.cloud.vertexai.api.FunctionCallingConfig;
import com.google.cloud.vertexai.api.GenerationConfig;
import com.google.cloud.vertexai.api.Schema;
import com.google.cloud.vertexai.api.Tool;
import com.google.cloud.vertexai.api.ToolConfig;
import com.google.cloud.vertexai.generativeai.GenerativeModel;
import com.google.cloud.vertexai.generativeai.ResponseHandler;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.vertexai.spi.VertexAiGeminiStreamingChatModelBuilderFactory;
import lombok.Builder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;

/**
 * Represents a Google Vertex AI Gemini language model with a stream chat completion interface, such as gemini-pro.
 * See details <a href="https://cloud.google.com/vertex-ai/docs/generative-ai/model-reference/gemini">here</a>.
 */
public class VertexAiGeminiStreamingChatModel implements StreamingChatLanguageModel, Closeable {

    private final GenerativeModel generativeModel;
    private final GenerationConfig generationConfig;
    private final VertexAI vertexAI;

    private final Map<HarmCategory, SafetyThreshold> safetySettings;

    private final Tool googleSearch;
    private final Tool vertexSearch;

    private final ToolConfig toolConfig;
    private final List<String> allowedFunctionNames;

    private final Boolean logRequests;
    private final Boolean logResponses;

    private static final Logger logger = LoggerFactory.getLogger(VertexAiGeminiChatModel.class);

    @Builder
    public VertexAiGeminiStreamingChatModel(String project,
                                            String location,
                                            String modelName,
                                            Float temperature,
                                            Integer maxOutputTokens,
                                            Integer topK,
                                            Float topP,
                                            String responseMimeType,
                                            Schema responseSchema,
                                            Map<HarmCategory, SafetyThreshold> safetySettings,
                                            Boolean useGoogleSearch,
                                            String vertexSearchDatastore,
                                            ToolCallingMode toolCallingMode,
                                            List<String> allowedFunctionNames,
                                            Boolean logRequests,
                                            Boolean logResponses) {
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
        if (responseMimeType != null) {
            generationConfigBuilder.setResponseMimeType(responseMimeType);
        }
        if (responseSchema != null) {
            generationConfigBuilder.setResponseMimeType("application/json");
            generationConfigBuilder.setResponseSchema(responseSchema);
        }
        this.generationConfig = generationConfigBuilder.build();

        if (safetySettings != null) {
            this.safetySettings = new HashMap<>(safetySettings);
        } else {
            this.safetySettings = Collections.emptyMap();
        }

        if (useGoogleSearch != null && useGoogleSearch) {
            googleSearch = ResponseGrounding.googleSearchTool();
        } else {
            googleSearch = null;
        }
        if (vertexSearchDatastore != null) {
            vertexSearch = ResponseGrounding.vertexAiSearch(vertexSearchDatastore);
        } else {
            vertexSearch = null;
        }

        if (allowedFunctionNames != null) {
            this.allowedFunctionNames = Collections.unmodifiableList(allowedFunctionNames);
        } else {
            this.allowedFunctionNames = Collections.emptyList();
        }
        if (toolCallingMode != null) {
            // only a subset of functions allowed to be used by the model
            if (toolCallingMode == ToolCallingMode.ANY &&
                allowedFunctionNames != null && !allowedFunctionNames.isEmpty()) {
                this.toolConfig = ToolConfig.newBuilder().setFunctionCallingConfig(
                    FunctionCallingConfig.newBuilder()
                        .setMode(FunctionCallingConfig.Mode.ANY)
                        .addAllAllowedFunctionNames(this.allowedFunctionNames)
                        .build()
                ).build();
            } else if (toolCallingMode == ToolCallingMode.NONE) { // no functions allowed
                this.toolConfig = ToolConfig.newBuilder().setFunctionCallingConfig(
                    FunctionCallingConfig.newBuilder()
                        .setMode(FunctionCallingConfig.Mode.NONE)
                        .build()
                ).build();
            } else { // Mode AUTO by default
                this.toolConfig = ToolConfig.newBuilder().setFunctionCallingConfig(
                    FunctionCallingConfig.newBuilder()
                        .setMode(FunctionCallingConfig.Mode.AUTO)
                        .build()
                ).build();
            }
        } else {
            this.toolConfig = ToolConfig.newBuilder().setFunctionCallingConfig(
                FunctionCallingConfig.newBuilder()
                    .setMode(FunctionCallingConfig.Mode.AUTO)
                    .build()
            ).build();
        }

        this.vertexAI = new VertexAI.Builder()
            .setProjectId(ensureNotBlank(project, "project"))
            .setLocation(ensureNotBlank(location, "location"))
            .setCustomHeaders(Collections.singletonMap("user-agent", "LangChain4j"))
            .build();

        this.generativeModel = new GenerativeModel(
            ensureNotBlank(modelName, "modelName"), vertexAI)
            .withGenerationConfig(generationConfig);

        if (logRequests != null) {
            this.logRequests = logRequests;
        } else {
            this.logRequests = false;
        }
        if (logResponses != null) {
            this.logResponses = logResponses;
        } else {
            this.logResponses = false;
        }
    }

    public VertexAiGeminiStreamingChatModel(GenerativeModel generativeModel,
                                            GenerationConfig generationConfig) {
        this.generativeModel = ensureNotNull(generativeModel, "generativeModel");
        this.generationConfig = ensureNotNull(generationConfig, "generationConfig");
        this.vertexAI = null;
        this.safetySettings = Collections.emptyMap();
        this.googleSearch = null;
        this.vertexSearch = null;
        this.toolConfig = ToolConfig.newBuilder().setFunctionCallingConfig(
            FunctionCallingConfig.newBuilder()
                .setMode(FunctionCallingConfig.Mode.AUTO)
                .build()
        ).build();
        this.allowedFunctionNames = Collections.emptyList();
        this.logRequests = false;
        this.logResponses = false;
    }

    @Override
    public void generate(List<ChatMessage> messages, StreamingResponseHandler<AiMessage> handler) {
        generate(messages, Collections.emptyList(), handler);
    }

    @Override
    public void generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications, StreamingResponseHandler<AiMessage> handler) {
        String modelName = generativeModel.getModelName();

        List<Tool> tools = new ArrayList<>();
        if (toolSpecifications != null && !toolSpecifications.isEmpty()) {
            Tool tool = FunctionCallHelper.convertToolSpecifications(toolSpecifications);
            tools.add(tool);
        }

        if (this.googleSearch != null) {
            tools.add(this.googleSearch);
        }
        if (this.vertexSearch != null) {
            tools.add(this.vertexSearch);
        }

        GenerativeModel model = this.generativeModel
            .withTools(tools)
            .withToolConfig(this.toolConfig);

        ContentsMapper.InstructionAndContent instructionAndContent =
            ContentsMapper.splitInstructionAndContent(messages);

        if (instructionAndContent.systemInstruction != null) {
            model = model.withSystemInstruction(instructionAndContent.systemInstruction);
        }

        if (!this.safetySettings.isEmpty()) {
            model = model.withSafetySettings(SafetySettingsMapper.mapSafetySettings(this.safetySettings));
        }

        if (this.logRequests && logger.isDebugEnabled()) {
            logger.debug("GEMINI ({}) request: {} tools: {}", modelName, instructionAndContent, tools);
        }

        StreamingChatResponseBuilder responseBuilder = new StreamingChatResponseBuilder();

        try {
            model.generateContentStream(instructionAndContent.contents)
                .stream()
                .forEach(partialResponse -> {
                    if (partialResponse.getCandidatesCount() > 0) {
                        responseBuilder.append(partialResponse);
                        handler.onNext(ResponseHandler.getText(partialResponse));
                    }
                });
            Response<AiMessage> fullResponse = responseBuilder.build();
            handler.onComplete(fullResponse);

            if (this.logResponses && logger.isDebugEnabled()) {
                logger.debug("GEMINI ({}) response: {}", modelName, fullResponse);
            }
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

    @Override
    public void close() throws IOException {
        if (this.vertexAI != null) {
            this.vertexAI.close();
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