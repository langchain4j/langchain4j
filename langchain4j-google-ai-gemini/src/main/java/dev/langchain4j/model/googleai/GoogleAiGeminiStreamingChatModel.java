package dev.langchain4j.model.googleai;

import dev.langchain4j.Experimental;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ChatRequestValidator;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.Response;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptions;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.model.ModelProvider.GOOGLE_AI_GEMINI;

@Experimental
public class GoogleAiGeminiStreamingChatModel extends BaseGeminiChatModel implements StreamingChatModel {

    private static final Logger log = org.slf4j.LoggerFactory.getLogger(GoogleAiGeminiStreamingChatModel.class);

    public GoogleAiGeminiStreamingChatModel(
            String apiKey, String modelName,
            Double temperature, Integer topK, Double topP,
            Integer maxOutputTokens, Duration timeout,
            ResponseFormat responseFormat,
            List<String> stopSequences, GeminiFunctionCallingConfig toolConfig,
            Boolean allowCodeExecution, Boolean includeCodeExecutionOutput,
            Boolean logRequestsAndResponses,
            List<GeminiSafetySetting> safetySettings,
            List<ChatModelListener> listeners,
            Integer maxRetries
    ) {
        super(apiKey, modelName, temperature, topK, topP, maxOutputTokens, timeout,
                responseFormat, stopSequences, toolConfig, allowCodeExecution,
                includeCodeExecutionOutput, logRequestsAndResponses, safetySettings,
                listeners, maxRetries);
    }

    public static GoogleAiGeminiStreamingChatModelBuilder builder() {
        return new GoogleAiGeminiStreamingChatModelBuilder();
    }

    @Override
    public void chat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
        ChatRequestParameters parameters = chatRequest.parameters();
        ChatRequestValidator.validateParameters(parameters);
        ChatRequestValidator.validate(parameters.toolChoice());

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
            generate(chatRequest.messages(), parameters.responseFormat(), legacyHandler);
        } else {
            generate(chatRequest.messages(), toolSpecifications, parameters.responseFormat(), legacyHandler);
        }
    }

    private void generate(List<ChatMessage> messages,
                          ResponseFormat responseFormat,
                          StreamingResponseHandler<AiMessage> handler) {
        generate(messages, List.of(), responseFormat, handler);
    }

    private void generate(List<ChatMessage> messages,
                          List<ToolSpecification> toolSpecifications,
                          ResponseFormat responseFormat,
                          StreamingResponseHandler<AiMessage> handler
    ) {
        ChatRequestParameters parameters = ChatRequestParameters.builder().build();
        GeminiGenerateContentRequest request = createGenerateContentRequest(messages, toolSpecifications, getOrDefault(responseFormat, this.responseFormat), parameters);
        ChatRequest listenerRequest = createListenerRequest(null, messages, toolSpecifications, parameters);

        ConcurrentHashMap<Object, Object> listenerAttributes = new ConcurrentHashMap<>();
        ChatModelRequestContext chatModelRequestContext = new ChatModelRequestContext(listenerRequest, provider(), listenerAttributes);
        notifyListenersOnRequest(chatModelRequestContext);

        processGenerateContentRequest(request, handler, listenerRequest, listenerAttributes);
    }

    private void processGenerateContentRequest(GeminiGenerateContentRequest request,
                                               StreamingResponseHandler<AiMessage> handler,
                                               ChatRequest listenerRequest,
                                               ConcurrentHashMap<Object, Object> listenerAttributes) {
        GeminiStreamingResponseBuilder responseBuilder = new GeminiStreamingResponseBuilder(this.includeCodeExecutionOutput);

        try {
            Stream<GeminiGenerateContentResponse> contentStream = withRetryMappingExceptions(
                    () -> this.geminiService.generateContentStream(this.modelName, this.apiKey, request),
                    maxRetries);

            contentStream.forEach(partialResponse -> {
                Optional<String> text = responseBuilder.append(partialResponse);
                text.ifPresent(handler::onNext);
            });

            Response<AiMessage> fullResponse = responseBuilder.build();
            handler.onComplete(fullResponse);

            notifyListenersOnResponse(fullResponse, listenerRequest, provider(), listenerAttributes);
        } catch (RuntimeException exception) {
            notifyListenersOnError(exception, listenerRequest, provider(), listenerAttributes);
            handler.onError(exception);
        }
    }

    @Override
    public List<ChatModelListener> listeners() {
        return listeners;
    }

    @Override
    public ModelProvider provider() {
        return GOOGLE_AI_GEMINI;
    }

    public static class GoogleAiGeminiStreamingChatModelBuilder {
        private String apiKey;
        private String modelName;
        private Double temperature;
        private Integer topK;
        private Double topP;
        private Integer maxOutputTokens;
        private Duration timeout;
        private ResponseFormat responseFormat;
        private List<String> stopSequences;
        private GeminiFunctionCallingConfig toolConfig;
        private Boolean allowCodeExecution;
        private Boolean includeCodeExecutionOutput;
        private Boolean logRequestsAndResponses;
        private List<GeminiSafetySetting> safetySettings;
        private List<ChatModelListener> listeners;
        private Integer maxRetries;

        GoogleAiGeminiStreamingChatModelBuilder() {
        }

        public GoogleAiGeminiStreamingChatModelBuilder toolConfig(GeminiMode mode, String... allowedFunctionNames) {
            this.toolConfig = new GeminiFunctionCallingConfig(mode, Arrays.asList(allowedFunctionNames));
            return this;
        }

        public GoogleAiGeminiStreamingChatModelBuilder safetySettings(Map<GeminiHarmCategory, GeminiHarmBlockThreshold> safetySettingMap) {
            this.safetySettings = safetySettingMap.entrySet().stream()
                    .map(entry -> new GeminiSafetySetting(entry.getKey(), entry.getValue())
                    ).collect(Collectors.toList());
            return this;
        }

        public GoogleAiGeminiStreamingChatModelBuilder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public GoogleAiGeminiStreamingChatModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public GoogleAiGeminiStreamingChatModelBuilder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public GoogleAiGeminiStreamingChatModelBuilder topK(Integer topK) {
            this.topK = topK;
            return this;
        }

        public GoogleAiGeminiStreamingChatModelBuilder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        public GoogleAiGeminiStreamingChatModelBuilder maxOutputTokens(Integer maxOutputTokens) {
            this.maxOutputTokens = maxOutputTokens;
            return this;
        }

        public GoogleAiGeminiStreamingChatModelBuilder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public GoogleAiGeminiStreamingChatModelBuilder responseFormat(ResponseFormat responseFormat) {
            this.responseFormat = responseFormat;
            return this;
        }

        public GoogleAiGeminiStreamingChatModelBuilder stopSequences(List<String> stopSequences) {
            this.stopSequences = stopSequences;
            return this;
        }

        public GoogleAiGeminiStreamingChatModelBuilder toolConfig(GeminiFunctionCallingConfig toolConfig) {
            this.toolConfig = toolConfig;
            return this;
        }

        public GoogleAiGeminiStreamingChatModelBuilder allowCodeExecution(Boolean allowCodeExecution) {
            this.allowCodeExecution = allowCodeExecution;
            return this;
        }

        public GoogleAiGeminiStreamingChatModelBuilder includeCodeExecutionOutput(Boolean includeCodeExecutionOutput) {
            this.includeCodeExecutionOutput = includeCodeExecutionOutput;
            return this;
        }

        public GoogleAiGeminiStreamingChatModelBuilder logRequestsAndResponses(Boolean logRequestsAndResponses) {
            this.logRequestsAndResponses = logRequestsAndResponses;
            return this;
        }

        public GoogleAiGeminiStreamingChatModelBuilder safetySettings(List<GeminiSafetySetting> safetySettings) {
            this.safetySettings = safetySettings;
            return this;
        }

        public GoogleAiGeminiStreamingChatModelBuilder listeners(List<ChatModelListener> listeners) {
            this.listeners = listeners;
            return this;
        }

        public GoogleAiGeminiStreamingChatModelBuilder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public GoogleAiGeminiStreamingChatModel build() {
            return new GoogleAiGeminiStreamingChatModel(this.apiKey, this.modelName, this.temperature, this.topK, this.topP, this.maxOutputTokens, this.timeout, this.responseFormat, this.stopSequences, this.toolConfig, this.allowCodeExecution, this.includeCodeExecutionOutput, this.logRequestsAndResponses, this.safetySettings, this.listeners, this.maxRetries);
        }

        public String toString() {
            return "GoogleAiGeminiStreamingChatModel.GoogleAiGeminiStreamingChatModelBuilder(apiKey=" + this.apiKey + ", modelName=" + this.modelName + ", temperature=" + this.temperature + ", topK=" + this.topK + ", topP=" + this.topP + ", maxOutputTokens=" + this.maxOutputTokens + ", timeout=" + this.timeout + ", responseFormat=" + this.responseFormat + ", stopSequences=" + this.stopSequences + ", toolConfig=" + this.toolConfig + ", allowCodeExecution=" + this.allowCodeExecution + ", includeCodeExecutionOutput=" + this.includeCodeExecutionOutput + ", logRequestsAndResponses=" + this.logRequestsAndResponses + ", safetySettings=" + this.safetySettings + ", listeners=" + this.listeners + ", maxRetries=" + this.maxRetries + ")";
        }
    }
}
