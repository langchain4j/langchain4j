package dev.langchain4j.model.googleai;

import static dev.langchain4j.model.ModelProvider.GOOGLE_AI_GEMINI;

import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class GoogleAiGeminiStreamingChatModel extends BaseGeminiChatModel implements StreamingChatModel {

    public GoogleAiGeminiStreamingChatModel(GoogleAiGeminiStreamingChatModelBuilder builder) {
        super(
                builder.httpClientBuilder,
                builder.apiKey,
                builder.modelName,
                builder.temperature,
                builder.topK,
                builder.seed,
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
                null,
                builder.thinkingConfig,
                builder.defaultRequestParameters);
    }

    /**
     * @deprecated please use {@link #GoogleAiGeminiStreamingChatModel(GoogleAiGeminiStreamingChatModelBuilder)} instead
     */
    @Deprecated(forRemoval = true, since = "1.1.0-beta7")
    public GoogleAiGeminiStreamingChatModel(
            String apiKey,
            String modelName,
            Double temperature,
            Integer topK,
            Double topP,
            Integer seed,
            Integer maxOutputTokens,
            Duration timeout,
            ResponseFormat responseFormat,
            List<String> stopSequences,
            GeminiFunctionCallingConfig toolConfig,
            Boolean allowCodeExecution,
            Boolean includeCodeExecutionOutput,
            Boolean logRequestsAndResponses,
            List<GeminiSafetySetting> safetySettings,
            List<ChatModelListener> listeners,
            Integer maxRetries) {
        super(
                null,
                apiKey,
                modelName,
                temperature,
                topK,
                seed,
                topP,
                null,
                null,
                maxOutputTokens,
                timeout,
                responseFormat,
                stopSequences,
                toolConfig,
                allowCodeExecution,
                includeCodeExecutionOutput,
                logRequestsAndResponses,
                safetySettings,
                listeners,
                maxRetries,
                null,
                null);
    }

    public static GoogleAiGeminiStreamingChatModelBuilder builder() {
        return new GoogleAiGeminiStreamingChatModelBuilder();
    }

    @Override
    public ChatRequestParameters defaultRequestParameters() {
        return defaultRequestParameters;
    }

    @Override
    public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
        GeminiGenerateContentRequest geminiRequest = createGenerateContentRequest(request);
        geminiService.generateContentStream(
                request.modelName(), apiKey, geminiRequest, includeCodeExecutionOutput, handler);
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

        private HttpClientBuilder httpClientBuilder;
        private ChatRequestParameters defaultRequestParameters;
        private String apiKey;
        private String modelName;
        private Double temperature;
        private Integer topK;
        private Integer seed;
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
        private List<ChatModelListener> listeners;
        private GeminiThinkingConfig thinkingConfig;

        GoogleAiGeminiStreamingChatModelBuilder() {}

        public GoogleAiGeminiStreamingChatModelBuilder httpClientBuilder(HttpClientBuilder httpClientBuilder) {
            this.httpClientBuilder = httpClientBuilder;
            return this;
        }

        public GoogleAiGeminiStreamingChatModelBuilder defaultRequestParameters(
                ChatRequestParameters defaultRequestParameters) {
            this.defaultRequestParameters = defaultRequestParameters;
            return this;
        }

        public GoogleAiGeminiStreamingChatModelBuilder toolConfig(GeminiMode mode, String... allowedFunctionNames) {
            this.functionCallingConfig = new GeminiFunctionCallingConfig(mode, Arrays.asList(allowedFunctionNames));
            return this;
        }

        public GoogleAiGeminiStreamingChatModelBuilder safetySettings(
                Map<GeminiHarmCategory, GeminiHarmBlockThreshold> safetySettingMap) {
            this.safetySettings = safetySettingMap.entrySet().stream()
                    .map(entry -> new GeminiSafetySetting(entry.getKey(), entry.getValue()))
                    .collect(Collectors.toList());
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

        public GoogleAiGeminiStreamingChatModelBuilder seed(Integer seed) {
            this.seed = seed;
            return this;
        }

        public GoogleAiGeminiStreamingChatModelBuilder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        public GoogleAiGeminiStreamingChatModelBuilder frequencyPenalty(Double frequencyPenalty) {
            this.frequencyPenalty = frequencyPenalty;
            return this;
        }

        public GoogleAiGeminiStreamingChatModelBuilder presencePenalty(Double presencePenalty) {
            this.presencePenalty = presencePenalty;
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
            this.functionCallingConfig = toolConfig;
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

        /**
         * @deprecated retries are not supported for streaming model
         */
        @Deprecated(forRemoval = true, since = "1.1.0-beta7")
        public GoogleAiGeminiStreamingChatModelBuilder maxRetries(Integer maxRetries) {
            return this;
        }

        public GoogleAiGeminiStreamingChatModelBuilder thinkingConfig(GeminiThinkingConfig thinkingConfig) {
            this.thinkingConfig = thinkingConfig;
            return this;
        }

        public GoogleAiGeminiStreamingChatModel build() {
            return new GoogleAiGeminiStreamingChatModel(this);
        }
    }
}
