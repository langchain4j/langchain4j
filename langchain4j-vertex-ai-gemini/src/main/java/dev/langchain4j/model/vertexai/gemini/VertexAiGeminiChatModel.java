package dev.langchain4j.model.vertexai.gemini;

import com.google.cloud.vertexai.VertexAI;
import com.google.cloud.vertexai.api.Content;
import com.google.cloud.vertexai.api.FunctionCall;
import com.google.cloud.vertexai.api.FunctionCallingConfig;
import com.google.cloud.vertexai.api.GenerateContentResponse;
import com.google.cloud.vertexai.api.GenerationConfig;
import com.google.cloud.vertexai.api.Part;
import com.google.cloud.vertexai.api.Schema;
import com.google.cloud.vertexai.api.Tool;
import com.google.cloud.vertexai.api.ToolConfig;
import com.google.cloud.vertexai.generativeai.GenerativeModel;
import com.google.cloud.vertexai.generativeai.ResponseHandler;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.internal.ChatRequestValidationUtils;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.vertexai.gemini.spi.VertexAiGeminiChatModelBuilderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptions;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.model.ModelProvider.GOOGLE_VERTEX_AI_GEMINI;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;
import static java.util.Collections.emptyList;

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
public class VertexAiGeminiChatModel implements ChatModel, Closeable {

    private final GenerativeModel generativeModel;
    private final GenerationConfig generationConfig;
    private final Integer maxRetries;
    private final VertexAI vertexAI;

    private final Map<HarmCategory, SafetyThreshold> safetySettings;

    private final Tool googleSearch;
    private final Tool vertexSearch;

    private final ToolConfig toolConfig;
    private final List<String> allowedFunctionNames;

    private final Boolean logRequests;
    private final Boolean logResponses;

    private static final Logger logger = LoggerFactory.getLogger(VertexAiGeminiChatModel.class);

    private final List<ChatModelListener> listeners;

    public VertexAiGeminiChatModel(String project,
                                   String location,
                                   String modelName,
                                   Float temperature,
                                   Integer maxOutputTokens,
                                   Integer topK,
                                   Float topP,
                                   Integer seed,
                                   Integer maxRetries,
                                   String responseMimeType,
                                   Schema responseSchema,
                                   Map<HarmCategory, SafetyThreshold> safetySettings,
                                   Boolean useGoogleSearch,
                                   String vertexSearchDatastore,
                                   ToolCallingMode toolCallingMode,
                                   List<String> allowedFunctionNames,
                                   Boolean logRequests,
                                   Boolean logResponses,
                                   List<ChatModelListener> listeners) {
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
        if (seed != null) {
            generationConfigBuilder.setSeed(seed);
        }
        if (responseMimeType != null) {
            generationConfigBuilder.setResponseMimeType(responseMimeType);
        }
        if (responseSchema != null) {
            if (responseSchema.getEnumCount() > 0) {
                generationConfigBuilder.setResponseMimeType("text/x.enum");
            } else {
                generationConfigBuilder.setResponseMimeType("application/json");
            }
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

        this.maxRetries = getOrDefault(maxRetries, 2);

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

        this.listeners = listeners == null ? emptyList() : new ArrayList<>(listeners);
    }

    public VertexAiGeminiChatModel(GenerativeModel generativeModel,
                                   GenerationConfig generationConfig) {
        this(generativeModel, generationConfig, 2);
    }

    public VertexAiGeminiChatModel(GenerativeModel generativeModel,
                                   GenerationConfig generationConfig,
                                   Integer maxRetries) {
        this.generationConfig = ensureNotNull(generationConfig, "generationConfig");
        this.generativeModel = ensureNotNull(generativeModel, "generativeModel")
                .withGenerationConfig(generationConfig);
        this.maxRetries = getOrDefault(maxRetries, 2);
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
        this.listeners = Collections.emptyList();
    }

    @Override
    public ChatResponse chat(ChatRequest chatRequest) {
        ChatRequestParameters parameters = chatRequest.parameters();
        ChatRequestValidationUtils.validateParameters(parameters);
        ChatRequestValidationUtils.validate(parameters.toolChoice());
        ChatRequestValidationUtils.validate(parameters.responseFormat());

        Response<AiMessage> response;
        List<ToolSpecification> toolSpecifications = parameters.toolSpecifications();
        if (isNullOrEmpty(toolSpecifications)) {
            response = generate(chatRequest.messages());
        } else {
            response = generate(chatRequest.messages(), toolSpecifications);
        }

        return ChatResponse.builder()
                .aiMessage(response.content())
                .metadata(ChatResponseMetadata.builder()
                        .tokenUsage(response.tokenUsage())
                        .finishReason(response.finishReason())
                        .build())
                .build();
    }

    private Response<AiMessage> generate(List<ChatMessage> messages) {
        return generate(messages, new ArrayList<>());
    }

    private Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
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

        GenerativeModel finalModel = model;

        ChatRequest listenerRequest = ChatRequest.builder()
                .messages(messages)
                .parameters(ChatRequestParameters.builder()
                        .modelName(modelName)
                        .temperature((double) generationConfig.getTemperature())
                        .topP((double) generationConfig.getTopP())
                        .maxOutputTokens(generationConfig.getMaxOutputTokens())
                        .toolSpecifications(toolSpecifications)
                        .build())
                .build();
        ConcurrentHashMap<Object, Object> listenerAttributes = new ConcurrentHashMap<>();
        ChatModelRequestContext chatModelRequestContext = new ChatModelRequestContext(
                listenerRequest, provider(), listenerAttributes);
        listeners.forEach((listener) -> {
            try {
                listener.onRequest(chatModelRequestContext);
            } catch (Exception e) {
                logger.warn("Exception while calling model listener (onRequest)", e);
            }
        });

        GenerateContentResponse response = null;
        try {
            response = withRetryMappingExceptions(() ->
                    finalModel.generateContent(instructionAndContent.contents), maxRetries);
        } catch (Exception e) {
            listeners.forEach((listener) -> {
                try {
                    ChatModelErrorContext chatModelErrorContext =
                            new ChatModelErrorContext(e, listenerRequest, provider(), listenerAttributes);
                    listener.onError(chatModelErrorContext);
                } catch (Exception t) {
                    logger.warn("Exception while calling model listener (onError)", t);
                }
            });

            throw new RuntimeException(e);
        }

        if (this.logResponses && logger.isDebugEnabled()) {
            logger.debug("GEMINI ({}) response: {}", modelName, response);
        }

        Content content = ResponseHandler.getContent(response);

        List<FunctionCall> functionCalls = content.getPartsList().stream()
                .filter(Part::hasFunctionCall)
                .map(Part::getFunctionCall)
                .collect(Collectors.toList());

        Response finalResponse = null;
        AiMessage aiMessage;

        if (!functionCalls.isEmpty()) {
            List<ToolExecutionRequest> toolExecutionRequests = FunctionCallHelper.fromFunctionCalls(functionCalls);

            aiMessage = AiMessage.from(toolExecutionRequests);
            finalResponse = Response.from(
                    aiMessage,
                    TokenUsageMapper.map(response.getUsageMetadata()),
                    FinishReasonMapper.map(ResponseHandler.getFinishReason(response))
            );
        } else {
            aiMessage = AiMessage.from(ResponseHandler.getText(response));
            finalResponse = Response.from(
                    aiMessage,
                    TokenUsageMapper.map(response.getUsageMetadata()),
                    FinishReasonMapper.map(ResponseHandler.getFinishReason(response))
            );
        }

        ChatResponse listenerResponse = ChatResponse.builder()
                .aiMessage(aiMessage)
                .metadata(ChatResponseMetadata.builder()
                        .modelName(modelName)
                        .tokenUsage(finalResponse.tokenUsage())
                        .finishReason(finalResponse.finishReason())
                        .build())
                .build();
        ChatModelResponseContext chatModelResponseContext = new ChatModelResponseContext(
                listenerResponse, listenerRequest, provider(), listenerAttributes);
        listeners.forEach((listener) -> {
            try {
                listener.onResponse(chatModelResponseContext);
            } catch (Exception e) {
                logger.warn("Exception while calling model listener (onResponse)", e);
            }
        });

        return finalResponse;
    }

    @Override
    public void close() {
        if (this.vertexAI != null) {
            vertexAI.close();
        }
    }

    @Override
    public List<ChatModelListener> listeners() {
        return listeners;
    }

    @Override
    public ModelProvider provider() {
        return GOOGLE_VERTEX_AI_GEMINI;
    }

    public static VertexAiGeminiChatModelBuilder builder() {
        for (VertexAiGeminiChatModelBuilderFactory factory : loadFactories(VertexAiGeminiChatModelBuilderFactory.class)) {
            return factory.get();
        }
        return new VertexAiGeminiChatModelBuilder();
    }

    public static class VertexAiGeminiChatModelBuilder {
        private String project;
        private String location;
        private String modelName;
        private Float temperature;
        private Integer maxOutputTokens;
        private Integer topK;
        private Float topP;
        private Integer seed;
        private Integer maxRetries;
        private String responseMimeType;
        private Schema responseSchema;
        private Map<HarmCategory, SafetyThreshold> safetySettings;
        private Boolean useGoogleSearch;
        private String vertexSearchDatastore;
        private ToolCallingMode toolCallingMode;
        private List<String> allowedFunctionNames;
        private Boolean logRequests;
        private Boolean logResponses;
        private List<ChatModelListener> listeners;

        public VertexAiGeminiChatModelBuilder() {
            // This is public so it can be extended
            // By default with Lombok it becomes package private
        }

        public VertexAiGeminiChatModelBuilder project(String project) {
            this.project = project;
            return this;
        }

        public VertexAiGeminiChatModelBuilder location(String location) {
            this.location = location;
            return this;
        }

        public VertexAiGeminiChatModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public VertexAiGeminiChatModelBuilder temperature(Float temperature) {
            this.temperature = temperature;
            return this;
        }

        public VertexAiGeminiChatModelBuilder maxOutputTokens(Integer maxOutputTokens) {
            this.maxOutputTokens = maxOutputTokens;
            return this;
        }

        public VertexAiGeminiChatModelBuilder topK(Integer topK) {
            this.topK = topK;
            return this;
        }

        public VertexAiGeminiChatModelBuilder topP(Float topP) {
            this.topP = topP;
            return this;
        }

        public VertexAiGeminiChatModelBuilder seed(Integer seed) {
            this.seed = seed;
            return this;
        }

        public VertexAiGeminiChatModelBuilder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public VertexAiGeminiChatModelBuilder responseMimeType(String responseMimeType) {
            this.responseMimeType = responseMimeType;
            return this;
        }

        public VertexAiGeminiChatModelBuilder responseSchema(Schema responseSchema) {
            this.responseSchema = responseSchema;
            return this;
        }

        public VertexAiGeminiChatModelBuilder safetySettings(Map<HarmCategory, SafetyThreshold> safetySettings) {
            this.safetySettings = safetySettings;
            return this;
        }

        public VertexAiGeminiChatModelBuilder useGoogleSearch(Boolean useGoogleSearch) {
            this.useGoogleSearch = useGoogleSearch;
            return this;
        }

        public VertexAiGeminiChatModelBuilder vertexSearchDatastore(String vertexSearchDatastore) {
            this.vertexSearchDatastore = vertexSearchDatastore;
            return this;
        }

        public VertexAiGeminiChatModelBuilder toolCallingMode(ToolCallingMode toolCallingMode) {
            this.toolCallingMode = toolCallingMode;
            return this;
        }

        public VertexAiGeminiChatModelBuilder allowedFunctionNames(List<String> allowedFunctionNames) {
            this.allowedFunctionNames = allowedFunctionNames;
            return this;
        }

        public VertexAiGeminiChatModelBuilder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public VertexAiGeminiChatModelBuilder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public VertexAiGeminiChatModelBuilder listeners(List<ChatModelListener> listeners) {
            this.listeners = listeners;
            return this;
        }

        public VertexAiGeminiChatModel build() {
            return new VertexAiGeminiChatModel(this.project, this.location, this.modelName, this.temperature, this.maxOutputTokens, this.topK, this.topP, this.seed, this.maxRetries, this.responseMimeType, this.responseSchema, this.safetySettings, this.useGoogleSearch, this.vertexSearchDatastore, this.toolCallingMode, this.allowedFunctionNames, this.logRequests, this.logResponses, this.listeners);
        }

        public String toString() {
            return "VertexAiGeminiChatModel.VertexAiGeminiChatModelBuilder(project=" + this.project + ", location=" + this.location + ", modelName=" + this.modelName + ", temperature=" + this.temperature + ", maxOutputTokens=" + this.maxOutputTokens + ", topK=" + this.topK + ", topP=" + this.topP + ", seed=" + this.seed + ", maxRetries=" + this.maxRetries + ", responseMimeType=" + this.responseMimeType + ", responseSchema=" + this.responseSchema + ", safetySettings=" + this.safetySettings + ", useGoogleSearch=" + this.useGoogleSearch + ", vertexSearchDatastore=" + this.vertexSearchDatastore + ", toolCallingMode=" + this.toolCallingMode + ", allowedFunctionNames=" + this.allowedFunctionNames + ", logRequests=" + this.logRequests + ", logResponses=" + this.logResponses + ", listeners=" + this.listeners + ")";
        }
    }
}
