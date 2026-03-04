package dev.langchain4j.model.vertexai.gemini;

import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onCompleteResponse;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onCompleteToolCall;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onPartialResponse;
import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNotNullOrEmpty;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.model.ModelProvider.GOOGLE_VERTEX_AI_GEMINI;
import static dev.langchain4j.model.vertexai.gemini.FunctionCallHelper.fromFunctionCall;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;
import static java.util.Collections.emptyList;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.vertexai.VertexAI;
import com.google.cloud.vertexai.api.FunctionCall;
import com.google.cloud.vertexai.api.FunctionCallingConfig;
import com.google.cloud.vertexai.api.GenerationConfig;
import com.google.cloud.vertexai.api.Schema;
import com.google.cloud.vertexai.api.Tool;
import com.google.cloud.vertexai.api.ToolConfig;
import com.google.cloud.vertexai.generativeai.GenerativeModel;
import com.google.common.annotations.VisibleForTesting;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.internal.ChatRequestValidationUtils;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.chat.response.CompleteToolCall;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.chat.response.StreamingHandle;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.vertexai.gemini.spi.VertexAiGeminiStreamingChatModelBuilderFactory;
import java.io.Closeable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents a Google Vertex AI Gemini language model with a stream chat completion interface, such as gemini-pro.
 * See details <a href="https://cloud.google.com/vertex-ai/docs/generative-ai/model-reference/gemini">here</a>.
 */
public class VertexAiGeminiStreamingChatModel implements StreamingChatModel, Closeable {

    private static final Logger logger = LoggerFactory.getLogger(VertexAiGeminiStreamingChatModel.class);

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

    private final List<ChatModelListener> listeners;

    private final Executor executor;

    public VertexAiGeminiStreamingChatModel(VertexAiGeminiStreamingChatModelBuilder builder) {
        ensureNotBlank(builder.modelName, "modelName");

        GenerationConfig.Builder generationConfigBuilder = GenerationConfig.newBuilder();
        if (builder.temperature != null) {
            generationConfigBuilder.setTemperature(builder.temperature);
        }
        if (builder.maxOutputTokens != null) {
            generationConfigBuilder.setMaxOutputTokens(builder.maxOutputTokens);
        }
        if (builder.topK != null) {
            generationConfigBuilder.setTopK(builder.topK);
        }
        if (builder.topP != null) {
            generationConfigBuilder.setTopP(builder.topP);
        }
        if (builder.responseMimeType != null) {
            generationConfigBuilder.setResponseMimeType(builder.responseMimeType);
        }
        if (builder.responseSchema != null) {
            if (builder.responseSchema.getEnumCount() > 0) {
                generationConfigBuilder.setResponseMimeType("text/x.enum");
            } else {
                generationConfigBuilder.setResponseMimeType("application/json");
            }
            generationConfigBuilder.setResponseSchema(builder.responseSchema);
        }
        this.generationConfig = generationConfigBuilder.build();

        this.safetySettings = copy(builder.safetySettings);

        if (builder.useGoogleSearch != null && builder.useGoogleSearch) {
            googleSearch = ResponseGrounding.googleSearchTool(builder.modelName);
        } else {
            googleSearch = null;
        }
        if (builder.vertexSearchDatastore != null) {
            vertexSearch = ResponseGrounding.vertexAiSearch(builder.vertexSearchDatastore);
        } else {
            vertexSearch = null;
        }

        if (builder.allowedFunctionNames != null) {
            this.allowedFunctionNames = Collections.unmodifiableList(builder.allowedFunctionNames);
        } else {
            this.allowedFunctionNames = Collections.emptyList();
        }
        if (builder.toolCallingMode != null) {
            // only a subset of functions allowed to be used by the model
            if (builder.toolCallingMode == ToolCallingMode.ANY && !allowedFunctionNames.isEmpty()) {
                this.toolConfig = ToolConfig.newBuilder()
                        .setFunctionCallingConfig(FunctionCallingConfig.newBuilder()
                                .setMode(FunctionCallingConfig.Mode.ANY)
                                .addAllAllowedFunctionNames(this.allowedFunctionNames)
                                .build())
                        .build();
            } else if (builder.toolCallingMode == ToolCallingMode.NONE) { // no functions allowed
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
        if (builder.customHeaders != null) {
            headers = new HashMap<>(builder.customHeaders);
            headers.putIfAbsent("user-agent", "LangChain4j");
        } else {
            headers = Map.of("user-agent", "LangChain4j");
        }

        VertexAI.Builder vertexAiBuilder = new VertexAI.Builder()
                .setProjectId(ensureNotBlank(builder.project, "project"))
                .setLocation(ensureNotBlank(builder.location, "location"))
                .setCustomHeaders(headers);

        if (builder.credentials != null) {
            GoogleCredentials scopedCredentials =
                    builder.credentials.createScoped("https://www.googleapis.com/auth/cloud-platform");
            vertexAiBuilder.setCredentials(scopedCredentials);
        }

        if (builder.apiEndpoint != null) {
            vertexAiBuilder.setApiEndpoint(builder.apiEndpoint);
        }

        this.vertexAI = vertexAiBuilder.build();

        this.generativeModel = new GenerativeModel(builder.modelName, vertexAI).withGenerationConfig(generationConfig);
        this.logRequests = getOrDefault(builder.logRequests, false);
        this.logResponses = getOrDefault(builder.logResponses, false);
        this.listeners = copy(builder.listeners);
        this.executor = getOrDefault(builder.executor, VertexAiGeminiStreamingChatModel::createDefaultExecutor);
    }

    /**
     * @deprecated please use {@link #VertexAiGeminiStreamingChatModel(VertexAiGeminiStreamingChatModelBuilder)} instead
     */
    @Deprecated(forRemoval = true, since = "1.1.0-beta7")
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
            final Map<String, String> customHeaders,
            Executor executor) {
        ensureNotBlank(modelName, "modelName");

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
            googleSearch = ResponseGrounding.googleSearchTool(modelName);
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

        this.generativeModel = new GenerativeModel(modelName, vertexAI).withGenerationConfig(generationConfig);

        this.logRequests = Objects.requireNonNullElse(logRequests, false);
        this.logResponses = Objects.requireNonNullElse(logResponses, false);

        this.listeners = listeners == null ? emptyList() : new ArrayList<>(listeners);
        this.executor = getOrDefault(executor, VertexAiGeminiStreamingChatModel::createDefaultExecutor);
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
        this.executor = VertexAiGeminiStreamingChatModel.createDefaultExecutor();
    }

    public VertexAiGeminiStreamingChatModel(
            GenerativeModel generativeModel, GenerationConfig generationConfig, Executor executor) {
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
        this.executor = getOrDefault(executor, VertexAiGeminiStreamingChatModel::createDefaultExecutor);
    }

    private static ExecutorService createDefaultExecutor() {
        return new ThreadPoolExecutor(0, Integer.MAX_VALUE, 1, SECONDS, new SynchronousQueue<>());
    }

    @Override
    public void chat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
        ChatRequestParameters parameters = chatRequest.parameters();
        ChatRequestValidationUtils.validateParameters(parameters);
        ChatRequestValidationUtils.validate(parameters.toolChoice());
        ChatRequestValidationUtils.validate(parameters.responseFormat());

        List<ToolSpecification> toolSpecifications = parameters.toolSpecifications();
        if (isNullOrEmpty(toolSpecifications)) {
            generate(chatRequest.messages(), handler);
        } else {
            generate(chatRequest.messages(), toolSpecifications, handler);
        }
    }

    private void generate(List<ChatMessage> messages, StreamingChatResponseHandler handler) {
        generate(messages, Collections.emptyList(), handler);
    }

    private void generate(
            List<ChatMessage> messages,
            List<ToolSpecification> toolSpecifications,
            StreamingChatResponseHandler handler) {
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
        final GenerativeModel finalModel = model;
        AtomicInteger toolIndex = new AtomicInteger(0);
        StreamingHandle streamingHandle = new VertexAiGeminiStreamingHandle();

        executor.execute(() -> {
            try {
                finalModel.generateContentStream(instructionAndContent.contents).stream()
                        .forEach(partialResponse -> {
                            if (streamingHandle.isCancelled()) {
                                return;
                            }

                            if (partialResponse.getCandidatesCount() > 0) {
                                StreamingChatResponseBuilder.TextAndFunctions textAndFunctions =
                                        responseBuilder.append(partialResponse);

                                String text = textAndFunctions.text();
                                if (isNotNullOrEmpty(text)) {
                                    onPartialResponse(handler, text, streamingHandle);
                                }

                                for (FunctionCall functionCall : textAndFunctions.functionCalls()) {
                                    final int index = toolIndex.get();
                                    ToolExecutionRequest toolExecutionRequest = fromFunctionCall(index, functionCall);
                                    CompleteToolCall completeToolCall =
                                            new CompleteToolCall(index, toolExecutionRequest);
                                    onCompleteToolCall(handler, completeToolCall);
                                    toolIndex.incrementAndGet();
                                }
                            }
                        });

                if (streamingHandle.isCancelled()) {
                    return;
                }

                Response<AiMessage> fullResponse = responseBuilder.build();

                ChatResponse chatResponse = ChatResponse.builder()
                        .aiMessage(fullResponse.content())
                        .metadata(ChatResponseMetadata.builder()
                                .tokenUsage(fullResponse.tokenUsage())
                                .finishReason(fullResponse.finishReason())
                                .build())
                        .build();

                onCompleteResponse(handler, chatResponse);

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
        });
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

        if (executor instanceof ExecutorService) {
            ((ExecutorService) executor).shutdown();
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
        private Executor executor;
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
        private GoogleCredentials credentials;
        private String apiEndpoint;

        public VertexAiGeminiStreamingChatModelBuilder() {
            // This is public so it can be extended
        }

        public VertexAiGeminiStreamingChatModelBuilder executor(Executor executor) {
            this.executor = executor;
            return this;
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

        public VertexAiGeminiStreamingChatModelBuilder apiEndpoint(String apiEndpoint) {
            this.apiEndpoint = apiEndpoint;
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

        /**
         * Sets the Google credentials to use for authentication.
         * If not provided, the client will use Application Default Credentials.
         *
         * @param credentials the Google credentials to use
         * @return this builder
         */
        public VertexAiGeminiStreamingChatModelBuilder credentials(GoogleCredentials credentials) {
            this.credentials = credentials;
            return this;
        }

        public VertexAiGeminiStreamingChatModel build() {
            return new VertexAiGeminiStreamingChatModel(this);
        }
    }
}
