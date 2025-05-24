package dev.langchain4j.model.vertexai.gemini;

import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.model.ModelProvider.GOOGLE_VERTEX_AI_GEMINI;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;
import static java.util.Collections.emptyList;

import com.google.cloud.vertexai.VertexAI;
import com.google.cloud.vertexai.api.FunctionCallingConfig;
import com.google.cloud.vertexai.api.GenerationConfig;
import com.google.cloud.vertexai.api.Schema;
import com.google.cloud.vertexai.api.Tool;
import com.google.cloud.vertexai.api.ToolConfig;
import com.google.cloud.vertexai.generativeai.GenerativeModel;
import com.google.cloud.vertexai.generativeai.ResponseHandler;
import com.google.common.annotations.VisibleForTesting;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.internal.ChatRequestValidationUtils;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.vertexai.gemini.spi.VertexAiGeminiStreamingChatModelBuilderFactory;
import java.io.Closeable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents a Google Vertex AI Gemini language model with a stream chat completion interface, such as gemini-pro.
 * See details <a href="https://cloud.google.com/vertex-ai/docs/generative-ai/model-reference/gemini">here</a>.
 */
public class VertexAiGeminiStreamingChatModel implements StreamingChatModel, Closeable {

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

    private final List<ChatModelListener> listeners;

    public VertexAiGeminiStreamingChatModel(
            String project,
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
            Boolean logResponses,
            List<ChatModelListener> listeners,
            final Map<String, String> customHeaders) {
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
            if (toolCallingMode == ToolCallingMode.ANY
                    && allowedFunctionNames != null
                    && !allowedFunctionNames.isEmpty()) {
                this.toolConfig = ToolConfig.newBuilder()
                        .setFunctionCallingConfig(FunctionCallingConfig.newBuilder()
                                .setMode(FunctionCallingConfig.Mode.ANY)
                                .addAllAllowedFunctionNames(this.allowedFunctionNames)
                                .build())
                        .build();
            } else if (toolCallingMode == ToolCallingMode.NONE) { // no functions allowed
                this.toolConfig = ToolConfig.newBuilder()
                        .setFunctionCallingConfig(FunctionCallingConfig.newBuilder()
                                .setMode(FunctionCallingConfig.Mode.NONE)
                                .build())
                        .build();
            } else { // Mode AUTO by default
                this.toolConfig = ToolConfig.newBuilder()
                        .setFunctionCallingConfig(FunctionCallingConfig.newBuilder()
                                .setMode(FunctionCallingConfig.Mode.AUTO)
                                .build())
                        .build();
            }
        } else {
            this.toolConfig = ToolConfig.newBuilder()
                    .setFunctionCallingConfig(FunctionCallingConfig.newBuilder()
                            .setMode(FunctionCallingConfig.Mode.AUTO)
                            .build())
                    .build();
        }
        final Map<String, String> headers;
        if (customHeaders != null) {
            headers = new HashMap<>(customHeaders);
            headers.putIfAbsent("user-agent", "LangChain4j");
        } else {
            headers = Map.of("user-agent", "LangChain4j");
        }

        this.vertexAI = new VertexAI.Builder()
                .setProjectId(ensureNotBlank(project, "project"))
                .setLocation(ensureNotBlank(location, "location"))
                .setCustomHeaders(headers)
                .build();

        this.generativeModel = new GenerativeModel(ensureNotBlank(modelName, "modelName"), vertexAI)
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

        this.listeners = listeners == null ? emptyList() : new ArrayList<>(listeners);
    }

    public VertexAiGeminiStreamingChatModel(GenerativeModel generativeModel, GenerationConfig generationConfig) {
        this.generativeModel = ensureNotNull(generativeModel, "generativeModel");
        this.generationConfig = ensureNotNull(generationConfig, "generationConfig");
        this.vertexAI = null;
        this.safetySettings = Collections.emptyMap();
        this.googleSearch = null;
        this.vertexSearch = null;
        this.toolConfig = ToolConfig.newBuilder()
                .setFunctionCallingConfig(FunctionCallingConfig.newBuilder()
                        .setMode(FunctionCallingConfig.Mode.AUTO)
                        .build())
                .build();
        this.allowedFunctionNames = Collections.emptyList();
        this.logRequests = false;
        this.logResponses = false;
        this.listeners = Collections.emptyList();
    }

    @Override
    public void chat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
        ChatRequestParameters parameters = chatRequest.parameters();
        ChatRequestValidationUtils.validateParameters(parameters);
        ChatRequestValidationUtils.validate(parameters.toolChoice());
        ChatRequestValidationUtils.validate(parameters.responseFormat());

        StreamingResponseHandler<AiMessage> legacyHandler = new StreamingResponseHandler<>() {

            @Override
            public void onNext(String token) {
                handler.onPartialResponse(token);
            }

            @Override
            public void onComplete(Response<AiMessage> response) {
                ChatResponse chatResponse = ChatResponse.builder()
                        .aiMessage(response.content())
                        .metadata(ChatResponseMetadata.builder()
                                .tokenUsage(response.tokenUsage())
                                .finishReason(response.finishReason())
                                .build())
                        .build();
                handler.onCompleteResponse(chatResponse);
            }

            @Override
            public void onError(Throwable error) {
                handler.onError(error);
            }
        };

        List<ToolSpecification> toolSpecifications = parameters.toolSpecifications();
        if (isNullOrEmpty(toolSpecifications)) {
            generate(chatRequest.messages(), legacyHandler);
        } else {
            generate(chatRequest.messages(), toolSpecifications, legacyHandler);
        }
    }

    private void generate(List<ChatMessage> messages, StreamingResponseHandler<AiMessage> handler) {
        generate(messages, Collections.emptyList(), handler);
    }

    private void generate(
            List<ChatMessage> messages,
            List<ToolSpecification> toolSpecifications,
            StreamingResponseHandler<AiMessage> handler) {
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

        GenerativeModel model = this.generativeModel.withTools(tools).withToolConfig(this.toolConfig);

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
        ChatModelRequestContext chatModelRequestContext =
                new ChatModelRequestContext(listenerRequest, provider(), listenerAttributes);
        listeners.forEach((listener) -> {
            try {
                listener.onRequest(chatModelRequestContext);
            } catch (Exception e) {
                logger.warn("Exception while calling model listener (onRequest)", e);
            }
        });

        StreamingChatResponseBuilder responseBuilder = new StreamingChatResponseBuilder();

        try {
            model.generateContentStream(instructionAndContent.contents).stream().forEach(partialResponse -> {
                if (partialResponse.getCandidatesCount() > 0) {
                    responseBuilder.append(partialResponse);
                    handler.onNext(ResponseHandler.getText(partialResponse));
                }
            });
            Response<AiMessage> fullResponse = responseBuilder.build();
            handler.onComplete(fullResponse);

            ChatResponse listenerResponse = ChatResponse.builder()
                    .aiMessage(fullResponse.content())
                    .metadata(ChatResponseMetadata.builder()
                            .modelName(modelName)
                            .tokenUsage(fullResponse.tokenUsage())
                            .finishReason(fullResponse.finishReason())
                            .build())
                    .build();
            ChatModelResponseContext chatModelResponseContext =
                    new ChatModelResponseContext(listenerResponse, listenerRequest, provider(), listenerAttributes);
            listeners.forEach((listener) -> {
                try {
                    listener.onResponse(chatModelResponseContext);
                } catch (Exception e) {
                    logger.warn("Exception while calling model listener (onResponse)", e);
                }
            });

            if (this.logResponses && logger.isDebugEnabled()) {
                logger.debug("GEMINI ({}) response: {}", modelName, fullResponse);
            }
        } catch (Exception exception) {
            listeners.forEach((listener) -> {
                try {
                    ChatModelErrorContext chatModelErrorContext =
                            new ChatModelErrorContext(exception, listenerRequest, provider(), listenerAttributes);
                    listener.onError(chatModelErrorContext);
                } catch (Exception t) {
                    logger.warn("Exception while calling model listener (onError)", t);
                }
            });

            handler.onError(exception);
        }
    }

    @VisibleForTesting
    VertexAI vertexAI() {
        return this.vertexAI;
    }

    @Override
    public void close() {
        if (this.vertexAI != null) {
            this.vertexAI.close();
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

    public static VertexAiGeminiStreamingChatModelBuilder builder() {
        for (VertexAiGeminiStreamingChatModelBuilderFactory factory :
                loadFactories(VertexAiGeminiStreamingChatModelBuilderFactory.class)) {
            return factory.get();
        }
        return new VertexAiGeminiStreamingChatModelBuilder();
    }

    public static class VertexAiGeminiStreamingChatModelBuilder {
        private String project;
        private String location;
        private String modelName;
        private Float temperature;
        private Integer maxOutputTokens;
        private Integer topK;
        private Float topP;
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
        private Map<String, String> customHeaders;

        public VertexAiGeminiStreamingChatModelBuilder() {
            // This is public so it can be extended
            // By default with Lombok it becomes package private
        }

        public VertexAiGeminiStreamingChatModelBuilder project(String project) {
            this.project = project;
            return this;
        }

        public VertexAiGeminiStreamingChatModelBuilder location(String location) {
            this.location = location;
            return this;
        }

        public VertexAiGeminiStreamingChatModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public VertexAiGeminiStreamingChatModelBuilder temperature(Float temperature) {
            this.temperature = temperature;
            return this;
        }

        public VertexAiGeminiStreamingChatModelBuilder maxOutputTokens(Integer maxOutputTokens) {
            this.maxOutputTokens = maxOutputTokens;
            return this;
        }

        public VertexAiGeminiStreamingChatModelBuilder topK(Integer topK) {
            this.topK = topK;
            return this;
        }

        public VertexAiGeminiStreamingChatModelBuilder topP(Float topP) {
            this.topP = topP;
            return this;
        }

        public VertexAiGeminiStreamingChatModelBuilder responseMimeType(String responseMimeType) {
            this.responseMimeType = responseMimeType;
            return this;
        }

        public VertexAiGeminiStreamingChatModelBuilder responseSchema(Schema responseSchema) {
            this.responseSchema = responseSchema;
            return this;
        }

        public VertexAiGeminiStreamingChatModelBuilder safetySettings(
                Map<HarmCategory, SafetyThreshold> safetySettings) {
            this.safetySettings = safetySettings;
            return this;
        }

        public VertexAiGeminiStreamingChatModelBuilder useGoogleSearch(Boolean useGoogleSearch) {
            this.useGoogleSearch = useGoogleSearch;
            return this;
        }

        public VertexAiGeminiStreamingChatModelBuilder vertexSearchDatastore(String vertexSearchDatastore) {
            this.vertexSearchDatastore = vertexSearchDatastore;
            return this;
        }

        public VertexAiGeminiStreamingChatModelBuilder toolCallingMode(ToolCallingMode toolCallingMode) {
            this.toolCallingMode = toolCallingMode;
            return this;
        }

        public VertexAiGeminiStreamingChatModelBuilder allowedFunctionNames(List<String> allowedFunctionNames) {
            this.allowedFunctionNames = allowedFunctionNames;
            return this;
        }

        public VertexAiGeminiStreamingChatModelBuilder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public VertexAiGeminiStreamingChatModelBuilder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public VertexAiGeminiStreamingChatModelBuilder listeners(List<ChatModelListener> listeners) {
            this.listeners = listeners;
            return this;
        }

        /**
         * Sets custom headers to be included in the LLM requests.
         * Main use-case is to support provision throughput quota.
         * E.g: "X-Vertex-AI-LLM-Request-Type: dedicated" will exhaust the provisioned throughput quota first, and will
         * return HTTP_429 if the quota is exhausted.
         * "X-Vertex-AI-LLM-Request-Type: shared" will bypass the provisioned throughput quota completely.
         *  For more information please refer to the <a href="https://cloud.google.com/vertex-ai/generative-ai/docs/use-provisioned-throughput">official documentation</a>
         *
         * @param customHeaders a map of custom header keys and their corresponding values
         * @return the updated instance of {@code VertexAiGeminiStreamingChatModelBuilder}
         */
        public VertexAiGeminiStreamingChatModelBuilder customHeaders(Map<String, String> customHeaders) {
            this.customHeaders = customHeaders;
            return this;
        }

        public VertexAiGeminiStreamingChatModel build() {
            return new VertexAiGeminiStreamingChatModel(
                    this.project,
                    this.location,
                    this.modelName,
                    this.temperature,
                    this.maxOutputTokens,
                    this.topK,
                    this.topP,
                    this.responseMimeType,
                    this.responseSchema,
                    this.safetySettings,
                    this.useGoogleSearch,
                    this.vertexSearchDatastore,
                    this.toolCallingMode,
                    this.allowedFunctionNames,
                    this.logRequests,
                    this.logResponses,
                    this.listeners,
                    this.customHeaders);
        }

        public String toString() {
            return "VertexAiGeminiStreamingChatModel.VertexAiGeminiStreamingChatModelBuilder(project=" + this.project
                    + ", location=" + this.location + ", modelName=" + this.modelName + ", temperature="
                    + this.temperature + ", maxOutputTokens=" + this.maxOutputTokens + ", topK=" + this.topK + ", topP="
                    + this.topP + ", responseMimeType=" + this.responseMimeType + ", responseSchema="
                    + this.responseSchema + ", safetySettings=" + this.safetySettings + ", useGoogleSearch="
                    + this.useGoogleSearch + ", vertexSearchDatastore=" + this.vertexSearchDatastore
                    + ", toolCallingMode=" + this.toolCallingMode + ", allowedFunctionNames="
                    + this.allowedFunctionNames + ", logRequests=" + this.logRequests + ", logResponses="
                    + this.logResponses + ", listeners=" + this.listeners + ")";
        }
    }
}
