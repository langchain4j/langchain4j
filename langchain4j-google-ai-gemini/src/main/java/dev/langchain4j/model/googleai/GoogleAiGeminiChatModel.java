package dev.langchain4j.model.googleai;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptions;
import static dev.langchain4j.model.ModelProvider.GOOGLE_AI_GEMINI;
import static dev.langchain4j.model.chat.Capability.RESPONSE_FORMAT_JSON_SCHEMA;
import static dev.langchain4j.model.googleai.FinishReasonMapper.fromGFinishReasonToFinishReason;
import static dev.langchain4j.model.googleai.PartsAndContentsMapper.fromGPartsToAiMessage;
import static dev.langchain4j.model.output.FinishReason.TOOL_EXECUTION;

@Experimental
public class GoogleAiGeminiChatModel extends BaseGeminiChatModel implements ChatModel {

    public GoogleAiGeminiChatModel(GoogleAiGeminiChatModelBuilder builder) {
        super(
                builder.apiKey,
                builder.modelName,
                builder.temperature,
                builder.topK,
                builder.topP,
                builder.frequencyPenalty,
                builder.presencePenalty,
                builder.maxOutputTokens,
                builder.timeout,
                builder.responseFormat,
                builder.stopSequences,
                builder.functionCallingConfig,
                builder.allowCodeExecution,
                builder.includeCodeExecutionOutput,
                builder.logRequestsAndResponses,
                builder.safetySettings,
                builder.listeners,
                builder.maxRetries,
                builder.thinkingConfig,
                builder.defaultRequestParameters
        );
    }

    /**
     * @deprecated please use {@link #GoogleAiGeminiChatModel(GoogleAiGeminiChatModelBuilder)} instead
     */
    @Deprecated(forRemoval = true, since = "1.1.0-beta7")
    public GoogleAiGeminiChatModel(
            String apiKey, String modelName,
            Integer maxRetries,
            Double temperature, Integer topK, Double topP,
            Integer maxOutputTokens, Duration timeout,
            ResponseFormat responseFormat,
            List<String> stopSequences, GeminiFunctionCallingConfig toolConfig,
            Boolean allowCodeExecution, Boolean includeCodeExecutionOutput,
            Boolean logRequestsAndResponses,
            List<GeminiSafetySetting> safetySettings,
            List<ChatModelListener> listeners
    ) {
        super(apiKey, modelName, temperature, topK, topP, null, null, maxOutputTokens, timeout,
                responseFormat, stopSequences, toolConfig, allowCodeExecution,
                includeCodeExecutionOutput, logRequestsAndResponses, safetySettings,
                listeners, maxRetries, null, null);
    }

    public static GoogleAiGeminiChatModelBuilder builder() {
        return new GoogleAiGeminiChatModelBuilder();
    }

    @Override
    public ChatRequestParameters defaultRequestParameters() {
        return defaultRequestParameters;
    }

    @Override
    public ChatResponse doChat(ChatRequest chatRequest) {

        ChatRequestParameters parameters = chatRequest.parameters();

        GeminiGenerateContentRequest request = createGenerateContentRequest(chatRequest);

        GeminiGenerateContentResponse geminiResponse = withRetryMappingExceptions(() ->
                        geminiService.generateContent(parameters.modelName(), apiKey, request), maxRetries);

        return processResponse(geminiResponse);
    }

    private ChatResponse processResponse(GeminiGenerateContentResponse geminiResponse) {
        if (geminiResponse == null) {
            throw new RuntimeException("Gemini response was null");
        }

        GeminiCandidate firstCandidate = geminiResponse.getCandidates().get(0);
        AiMessage aiMessage = createAiMessage(firstCandidate);

        FinishReason finishReason = fromGFinishReasonToFinishReason(firstCandidate.getFinishReason());
        if (aiMessage != null && aiMessage.hasToolExecutionRequests()) {
            finishReason = TOOL_EXECUTION;
        }

        return ChatResponse.builder()
                .aiMessage(aiMessage)
                .metadata(ChatResponseMetadata.builder()
                        .id(geminiResponse.getResponseId())
                        .modelName(geminiResponse.getModelVersion())
                        .tokenUsage(createTokenUsage(geminiResponse.getUsageMetadata()))
                        .finishReason(finishReason)
                        .build())
                .build();
    }

    private AiMessage createAiMessage(GeminiCandidate candidate) {
        if (candidate == null || candidate.getContent() == null) {
            return null;
        }

        return fromGPartsToAiMessage(candidate.getContent().getParts(), this.includeCodeExecutionOutput);
    }

    private TokenUsage createTokenUsage(GeminiUsageMetadata tokenCounts) {
        return new TokenUsage(
                tokenCounts.getPromptTokenCount(),
                tokenCounts.getCandidatesTokenCount(),
                tokenCounts.getTotalTokenCount()
        );
    }

    @Override
    public Set<Capability> supportedCapabilities() {
        Set<Capability> capabilities = new HashSet<>();
        // when response format is not null, it's JSON, either application/json or text/x.enum
        ResponseFormat responseFormat = this.defaultRequestParameters.responseFormat();
        if (responseFormat != null && ResponseFormatType.JSON.equals(responseFormat.type())) {
            capabilities.add(RESPONSE_FORMAT_JSON_SCHEMA); // TODO or allow always? all models support?
            // TODO check docu
        }
        return capabilities;
    }

    @Override
    public List<ChatModelListener> listeners() {
        return listeners;
    }

    @Override
    public ModelProvider provider() {
        return GOOGLE_AI_GEMINI;
    }

    public static class GoogleAiGeminiChatModelBuilder {

        private ChatRequestParameters defaultRequestParameters;
        private String apiKey;
        private String modelName;
        private Integer maxRetries;
        private Double temperature;
        private Integer topK;
        private Double topP;
        private Double frequencyPenalty;
        private Double presencePenalty;
        private Integer maxOutputTokens;
        private Duration timeout;
        private ResponseFormat responseFormat;
        private List<String> stopSequences;
        private GeminiFunctionCallingConfig functionCallingConfig;
        private Boolean allowCodeExecution;
        private Boolean includeCodeExecutionOutput;
        private Boolean logRequestsAndResponses;
        private List<GeminiSafetySetting> safetySettings;
        private GeminiThinkingConfig thinkingConfig;
        private List<ChatModelListener> listeners;

        GoogleAiGeminiChatModelBuilder() {
        }

        public GoogleAiGeminiChatModelBuilder defaultRequestParameters(ChatRequestParameters defaultRequestParameters) {
            this.defaultRequestParameters = defaultRequestParameters;
            return this;
        }

        public GoogleAiGeminiChatModelBuilder toolConfig(GeminiMode mode, String... allowedFunctionNames) {
            this.functionCallingConfig = new GeminiFunctionCallingConfig(mode, Arrays.asList(allowedFunctionNames));
            return this;
        }

        public GoogleAiGeminiChatModelBuilder safetySettings(Map<GeminiHarmCategory, GeminiHarmBlockThreshold> safetySettingMap) {
            this.safetySettings = safetySettingMap.entrySet().stream()
                    .map(entry -> new GeminiSafetySetting(entry.getKey(), entry.getValue())
                    ).collect(Collectors.toList());
            return this;
        }

        public GoogleAiGeminiChatModelBuilder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public GoogleAiGeminiChatModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public GoogleAiGeminiChatModelBuilder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public GoogleAiGeminiChatModelBuilder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public GoogleAiGeminiChatModelBuilder topK(Integer topK) {
            this.topK = topK;
            return this;
        }

        public GoogleAiGeminiChatModelBuilder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        public GoogleAiGeminiChatModelBuilder frequencyPenalty(Double frequencyPenalty) {
            this.frequencyPenalty = frequencyPenalty;
            return this;
        }

        public GoogleAiGeminiChatModelBuilder presencePenalty(Double presencePenalty) {
            this.presencePenalty = presencePenalty;
            return this;
        }

        public GoogleAiGeminiChatModelBuilder maxOutputTokens(Integer maxOutputTokens) {
            this.maxOutputTokens = maxOutputTokens;
            return this;
        }

        public GoogleAiGeminiChatModelBuilder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public GoogleAiGeminiChatModelBuilder responseFormat(ResponseFormat responseFormat) {
            this.responseFormat = responseFormat;
            return this;
        }

        public GoogleAiGeminiChatModelBuilder stopSequences(List<String> stopSequences) {
            this.stopSequences = stopSequences;
            return this;
        }

        public GoogleAiGeminiChatModelBuilder toolConfig(GeminiFunctionCallingConfig toolConfig) {
            this.functionCallingConfig = toolConfig;
            return this;
        }

        public GoogleAiGeminiChatModelBuilder allowCodeExecution(Boolean allowCodeExecution) {
            this.allowCodeExecution = allowCodeExecution;
            return this;
        }

        public GoogleAiGeminiChatModelBuilder includeCodeExecutionOutput(Boolean includeCodeExecutionOutput) {
            this.includeCodeExecutionOutput = includeCodeExecutionOutput;
            return this;
        }

        public GoogleAiGeminiChatModelBuilder logRequestsAndResponses(Boolean logRequestsAndResponses) {
            this.logRequestsAndResponses = logRequestsAndResponses;
            return this;
        }

        public GoogleAiGeminiChatModelBuilder safetySettings(List<GeminiSafetySetting> safetySettings) {
            this.safetySettings = safetySettings;
            return this;
        }

        public GoogleAiGeminiChatModelBuilder thinkingConfig(GeminiThinkingConfig thinkingConfig) {
            this.thinkingConfig = thinkingConfig;
            return this;
        }

        public GoogleAiGeminiChatModelBuilder listeners(List<ChatModelListener> listeners) {
            this.listeners = listeners;
            return this;
        }

        public GoogleAiGeminiChatModel build() {
            return new GoogleAiGeminiChatModel(this);
        }
    }
}
