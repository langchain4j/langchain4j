package dev.langchain4j.model.googleai;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.copyIfNotNull;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.model.googleai.FinishReasonMapper.fromGFinishReasonToFinishReason;
import static dev.langchain4j.model.googleai.FunctionMapper.fromToolSepcsToGTool;
import static dev.langchain4j.model.googleai.PartsAndContentsMapper.fromGPartsToAiMessage;
import static dev.langchain4j.model.googleai.PartsAndContentsMapper.fromMessageToGContent;
import static dev.langchain4j.model.googleai.SchemaMapper.fromJsonSchemaToGSchema;
import static dev.langchain4j.model.output.FinishReason.TOOL_EXECUTION;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonRawSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.googleai.GeminiGenerateContentRequest.GeminiToolConfig;
import dev.langchain4j.model.googleai.GeminiGenerateContentResponse.GeminiCandidate;
import dev.langchain4j.model.googleai.GeminiGenerateContentResponse.GeminiUsageMetadata;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;

class BaseGeminiChatModel {
    protected final GeminiService geminiService;
    protected final GeminiFunctionCallingConfig functionCallingConfig;
    protected final boolean allowCodeExecution;
    protected final boolean allowGoogleSearch;
    protected final boolean allowGoogleMaps;
    protected final boolean retrieveGoogleMapsWidgetToken;
    protected final boolean allowUrlContext;
    protected final boolean includeCodeExecutionOutput;
    protected final List<GeminiSafetySetting> safetySettings;
    protected final List<ChatModelListener> listeners;
    protected final GeminiThinkingConfig thinkingConfig;
    protected final Boolean returnThinking;
    protected final boolean sendThinking;
    protected final Integer seed;
    protected final Integer logprobs;
    protected final Boolean responseLogprobs;
    protected final Boolean enableEnhancedCivicAnswers;
    protected final GeminiMediaResolutionLevel mediaResolution;

    protected final ChatRequestParameters defaultRequestParameters;

    protected BaseGeminiChatModel(GoogleAiGeminiChatModelBaseBuilder<?> builder, GeminiService geminiService) {
        this.geminiService = geminiService;

        this.functionCallingConfig = builder.functionCallingConfig;
        this.allowCodeExecution = getOrDefault(builder.allowCodeExecution, false);
        this.allowGoogleSearch = getOrDefault(builder.allowGoogleSearch, false);
        this.allowGoogleMaps = getOrDefault(builder.allowGoogleMaps, false);
        this.retrieveGoogleMapsWidgetToken = getOrDefault(builder.retrieveGoogleMapsWidgetToken, false);
        this.allowUrlContext = getOrDefault(builder.allowUrlContext, false);
        this.includeCodeExecutionOutput = getOrDefault(builder.includeCodeExecutionOutput, false);
        this.safetySettings = copyIfNotNull(builder.safetySettings);
        this.listeners = copy(builder.listeners);
        this.thinkingConfig = builder.thinkingConfig;
        this.returnThinking = builder.returnThinking;
        this.sendThinking = getOrDefault(builder.sendThinking, false);
        this.seed = builder.seed;
        this.responseLogprobs = getOrDefault(builder.responseLogprobs, false);
        this.enableEnhancedCivicAnswers = getOrDefault(builder.enableEnhancedCivicAnswers, false);
        this.logprobs = builder.logprobs;
        this.mediaResolution = builder.mediaResolution;

        ChatRequestParameters parameters;
        if (builder.defaultRequestParameters != null) {
            parameters = builder.defaultRequestParameters;
        } else {
            parameters = DefaultChatRequestParameters.EMPTY;
        }

        this.defaultRequestParameters = ChatRequestParameters.builder()
                .modelName(getOrDefault(builder.modelName, parameters.modelName()))
                .temperature(getOrDefault(builder.temperature, parameters.temperature()))
                .topP(getOrDefault(builder.topP, parameters.topP()))
                .topK(getOrDefault(builder.topK, parameters.topK()))
                .frequencyPenalty(getOrDefault(builder.frequencyPenalty, parameters.frequencyPenalty()))
                .presencePenalty(getOrDefault(builder.presencePenalty, parameters.presencePenalty()))
                .maxOutputTokens(getOrDefault(builder.maxOutputTokens, parameters.maxOutputTokens()))
                .stopSequences(getOrDefault(builder.stopSequences, parameters.stopSequences()))
                .toolSpecifications(parameters.toolSpecifications())
                .toolChoice(getOrDefault(toToolChoice(functionCallingConfig), parameters.toolChoice()))
                .responseFormat(getOrDefault(builder.responseFormat, parameters.responseFormat()))
                .build();
    }

    protected static GeminiService buildGeminiService(GoogleAiGeminiChatModelBaseBuilder<?> builder) {
        return new GeminiService(
                builder.httpClientBuilder,
                builder.apiKey,
                builder.baseUrl,
                getOrDefault(builder.logRequestsAndResponses, false),
                getOrDefault(builder.logRequests, false),
                getOrDefault(builder.logResponses, false),
                builder.logger,
                builder.timeout);
    }

    protected GeminiGenerateContentRequest createGenerateContentRequest(ChatRequest chatRequest) {
        ChatRequestParameters parameters = chatRequest.parameters();

        GeminiContent systemInstruction = new GeminiContent(List.of(), GeminiRole.MODEL.toString());
        List<GeminiContent> geminiContentList =
                fromMessageToGContent(chatRequest.messages(), systemInstruction, sendThinking);

        ResponseFormat responseFormat = chatRequest.responseFormat();
        GeminiSchema schema = null;
        Map<String, Object> rawSchema = null;

        if (responseFormat != null) {
            if (responseFormat.jsonSchema() != null) {
                if (responseFormat.jsonSchema().rootElement() instanceof JsonRawSchema jsonRawSchema) {
                    rawSchema = Json.fromJson(jsonRawSchema.schema(), Map.class);
                } else {
                    schema = fromJsonSchemaToGSchema(responseFormat.jsonSchema());
                }
            }
        }

        return GeminiGenerateContentRequest.builder()
                .contents(geminiContentList)
                .systemInstruction(!systemInstruction.parts().isEmpty() ? systemInstruction : null)
                .generationConfig(GeminiGenerationConfig.builder()
                        .candidateCount(1) // Multiple candidates aren't supported by langchain4j
                        .maxOutputTokens(parameters.maxOutputTokens())
                        .responseMimeType(computeMimeType(responseFormat))
                        .responseSchema(schema)
                        .responseJsonSchema(rawSchema)
                        .stopSequences(parameters.stopSequences())
                        .temperature(parameters.temperature())
                        .topK(parameters.topK())
                        .seed(seed)
                        .topP(parameters.topP())
                        .presencePenalty(parameters.presencePenalty())
                        .frequencyPenalty(parameters.frequencyPenalty())
                        .responseLogprobs(responseLogprobs)
                        .logprobs(logprobs)
                        .thinkingConfig(this.thinkingConfig)
                        .mediaResolution(this.mediaResolution)
                        .build())
                .safetySettings(this.safetySettings)
                .tools(fromToolSepcsToGTool(
                        chatRequest.toolSpecifications(),
                        this.allowCodeExecution,
                        this.allowGoogleSearch,
                        this.allowUrlContext,
                        this.allowGoogleMaps,
                        this.retrieveGoogleMapsWidgetToken))
                .toolConfig(toToolConfig(parameters.toolChoice(), this.functionCallingConfig))
                .build();
    }

    private GeminiToolConfig toToolConfig(ToolChoice toolChoice, GeminiFunctionCallingConfig functionCallingConfig) {
        if (toolChoice == null && functionCallingConfig == null) {
            return null;
        }

        GeminiMode geminiMode = Optional.ofNullable(functionCallingConfig)
                .map(GeminiFunctionCallingConfig::getMode)
                .orElse(null);
        List<String> allowedFunctionNames = Optional.ofNullable(functionCallingConfig)
                .map(GeminiFunctionCallingConfig::getAllowedFunctionNames)
                .orElse(null);

        if (toolChoice != null) {
            geminiMode = toGeminiMode(toolChoice);
        }

        return new GeminiToolConfig(new GeminiFunctionCallingConfig(geminiMode, allowedFunctionNames));
    }

    protected static String computeMimeType(ResponseFormat responseFormat) {
        if (responseFormat == null || ResponseFormatType.TEXT.equals(responseFormat.type())) {
            return "text/plain";
        }

        if (ResponseFormatType.JSON.equals(responseFormat.type())
                && responseFormat.jsonSchema() != null
                && responseFormat.jsonSchema().rootElement() != null
                && responseFormat.jsonSchema().rootElement() instanceof JsonEnumSchema) {
            return "text/x.enum";
        }

        return "application/json";
    }

    private static GeminiMode toGeminiMode(ToolChoice toolChoice) {
        return switch (toolChoice) {
            case AUTO -> GeminiMode.AUTO;
            case REQUIRED -> GeminiMode.ANY;
            case NONE -> GeminiMode.NONE;
        };
    }

    private static ToolChoice toToolChoice(GeminiFunctionCallingConfig config) {
        if (config == null || config.getMode() == null) {
            return null;
        }

        return switch (config.getMode()) {
            case AUTO -> ToolChoice.AUTO;
            case ANY -> ToolChoice.REQUIRED;
            case NONE -> null;
        };
    }

    protected ChatResponse processResponse(GeminiGenerateContentResponse geminiResponse) {
        GeminiCandidate firstCandidate = geminiResponse.candidates().get(0);
        AiMessage aiMessage = createAiMessage(firstCandidate);

        FinishReason finishReason = fromGFinishReasonToFinishReason(firstCandidate.finishReason());
        if (aiMessage != null && aiMessage.hasToolExecutionRequests()) {
            finishReason = TOOL_EXECUTION;
        }

        return ChatResponse.builder()
                .aiMessage(aiMessage)
                .metadata(GoogleAiGeminiChatResponseMetadata.builder()
                        .id(geminiResponse.responseId())
                        .modelName(geminiResponse.modelVersion())
                        .tokenUsage(createTokenUsage(geminiResponse.usageMetadata()))
                        .finishReason(finishReason)
                        .groundingMetadata(geminiResponse.groundingMetadata())
                        .build())
                .build();
    }

    protected AiMessage createAiMessage(GeminiCandidate candidate) {
        if (candidate == null || candidate.content() == null) {
            return fromGPartsToAiMessage(List.of(), includeCodeExecutionOutput, returnThinking);
        }

        return fromGPartsToAiMessage(candidate.content().parts(), includeCodeExecutionOutput, returnThinking);
    }

    protected TokenUsage createTokenUsage(GeminiUsageMetadata tokenCounts) {
        return new TokenUsage(
                tokenCounts.promptTokenCount(), tokenCounts.candidatesTokenCount(), tokenCounts.totalTokenCount());
    }

    /**
     * Base builder class containing shared properties and methods for Google AI Gemini chat models.
     */
    public abstract static class GoogleAiGeminiChatModelBaseBuilder<B extends GoogleAiGeminiChatModelBaseBuilder<B>> {

        protected HttpClientBuilder httpClientBuilder;
        protected ChatRequestParameters defaultRequestParameters;
        protected String apiKey;
        protected String baseUrl;
        protected String modelName;
        protected Double temperature;
        protected Integer topK;
        protected Integer seed;
        protected Double topP;
        protected Double frequencyPenalty;
        protected Double presencePenalty;
        protected Integer maxOutputTokens;
        protected Duration timeout;
        protected ResponseFormat responseFormat;
        protected List<String> stopSequences;
        protected GeminiFunctionCallingConfig functionCallingConfig;
        protected Boolean allowCodeExecution;
        protected Boolean allowGoogleSearch;
        protected Boolean allowGoogleMaps;
        protected Boolean retrieveGoogleMapsWidgetToken;
        protected Boolean allowUrlContext;
        protected Boolean includeCodeExecutionOutput;
        protected Boolean logRequestsAndResponses;
        protected Boolean logRequests;
        protected Boolean logResponses;
        protected Logger logger;
        protected Boolean responseLogprobs;
        protected Boolean enableEnhancedCivicAnswers;
        protected List<GeminiSafetySetting> safetySettings;
        protected GeminiThinkingConfig thinkingConfig;
        protected Boolean returnThinking;
        protected Boolean sendThinking;
        protected Integer logprobs;
        protected List<ChatModelListener> listeners;
        protected GeminiMediaResolutionLevel mediaResolution;

        @SuppressWarnings("unchecked")
        protected B builder() {
            return (B) this;
        }

        public B httpClientBuilder(HttpClientBuilder httpClientBuilder) {
            this.httpClientBuilder = httpClientBuilder;
            return builder();
        }

        public B defaultRequestParameters(ChatRequestParameters defaultRequestParameters) {
            this.defaultRequestParameters = defaultRequestParameters;
            return builder();
        }

        public B apiKey(String apiKey) {
            this.apiKey = apiKey;
            return builder();
        }

        public B baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return builder();
        }

        public B modelName(String modelName) {
            this.modelName = modelName;
            return builder();
        }

        public B timeout(Duration timeout) {
            this.timeout = timeout;
            return builder();
        }

        public B listeners(List<ChatModelListener> listeners) {
            this.listeners = listeners;
            return builder();
        }

        public B includeCodeExecutionOutput(Boolean includeCodeExecutionOutput) {
            this.includeCodeExecutionOutput = includeCodeExecutionOutput;
            return builder();
        }

        public B logRequestsAndResponses(Boolean logRequestsAndResponses) {
            this.logRequestsAndResponses = logRequestsAndResponses;
            return builder();
        }

        public B logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return builder();
        }

        public B logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return builder();
        }

        /**
         * @param logger an alternate {@link Logger} to be used instead of the default one provided by Langchain4J for logging requests and responses.
         * @return {@code this}.
         */
        public B logger(Logger logger) {
            this.logger = logger;
            return builder();
        }

        /**
         * Tool configuration for any Tool specified in the request.
         */
        public B toolConfig(GeminiFunctionCallingConfig toolConfig) {
            this.functionCallingConfig = toolConfig;
            return builder();
        }

        /**
         * Configuration for any Tool specified in the request, including an allow list of functions that can be called.
         */
        public B toolConfig(GeminiMode mode, String... allowedFunctionNames) {
            this.functionCallingConfig = new GeminiFunctionCallingConfig(mode, Arrays.asList(allowedFunctionNames));
            return builder();
        }

        /**
         * A list of unique SafetySetting instances for blocking unsafe content. This will be enforced on the
         * {@code GenerateContentRequest.contents} and {@code GenerateContentResponse.candidates}. There should not
         * be more than one setting for each SafetyCategory type. The API will block any contents and responses that
         * fail to meet the thresholds set by these settings.
         */
        public B safetySettings(Map<GeminiHarmCategory, GeminiHarmBlockThreshold> safetySettingMap) {
            this.safetySettings = safetySettingMap.entrySet().stream()
                    .map(entry -> new GeminiSafetySetting(entry.getKey(), entry.getValue()))
                    .toList();
            return builder();
        }

        /**
         * Controls the randomness of the output.
         *
         * <p><strong>Note:</strong> The default value varies by model, see the {@code Model.temperature} attribute
         * of the Model returned from the getModel function.</p>
         */
        public B temperature(Double temperature) {
            this.temperature = temperature;
            return builder();
        }

        /**
         * The maximum number of tokens to consider when sampling. The models use Top-p (nucleus) sampling or a
         * combination of Top-k and nucleus sampling. Top-k sampling considers the set of topK most probable tokens.
         * Models running with nucleus sampling don't allow topK setting.
         *
         * <p><strong>Note:</strong> The default value varies by Model and is specified by theModel.top_k attribute
         * returned from the getModel function. An empty topK attribute indicates that the model doesn't apply
         * top-k sampling and doesn't allow setting topK on requests.</p>
         *
         */
        public B topK(Integer topK) {
            this.topK = topK;
            return builder();
        }

        /**
         * Seed used in decoding. If not set, the request uses a randomly generated seed.
         */
        public B seed(Integer seed) {
            this.seed = seed;
            return builder();
        }

        /**
         * The maximum cumulative probability of tokens to consider when sampling. The model uses combined Top-k and
         * Top-p (nucleus) sampling.
         *
         * <p>Tokens are sorted based on their assigned probabilities so that only the most likely tokens are
         * considered. Top-k sampling directly limits the maximum number of tokens to consider, while Nucleus sampling
         * limits the number of tokens based on the cumulative probability.</p>
         *
         * <p><strong>Note:</strong> The default value varies by Model and is specified by theModel.top_p attribute
         * returned from the getModel function. An empty topK attribute indicates that the model doesn't apply
         * top-k sampling and doesn't allow setting topK on requests.</p>
         */
        public B topP(Double topP) {
            this.topP = topP;
            return builder();
        }

        /**
         * Frequency penalty applied to the next token's {@code logprobs}, multiplied by the number of times each token
         * has been seen in the respponse so far.
         *
         * <p>A positive penalty will discourage the use of tokens that have already been used, proportional to the
         * number of times the token has been used: The more a token is used, the more difficult it is for the model
         * to use that token again increasing the vocabulary of responses.</p>
         *
         * <p><strong>Caution:</strong>A negative penalty will encourage the model to reuse tokens proportional to the
         * number of times the token has been used. Small negative values will reduce the vocabulary of a response.
         * Larger negative values will cause the model to start repeating a common token until it hits the
         * {@code maxOutputTokens} limit.</p>
         */
        public B frequencyPenalty(Double frequencyPenalty) {
            this.frequencyPenalty = frequencyPenalty;
            return builder();
        }

        /**
         * Presence penalty applied to the next token's {@code logprobs} if the token has already been seen in the response.
         *
         * <p>This penalty is binary on/off and not dependent on the number of times the token is used
         * (after the first). Use frequencyPenalty for a penalty that increases with each use.</p>
         *
         * <ul>
         *  <li>A positive penalty will discourage the use of tokens that have already been used in the response,
         *  increasing the vocabulary.</li>
         *  <li>A negative penalty will encourage the use of tokens that have already been used in the response,
         *  decreasing the vocabulary.</li>
         * </ul>
         */
        public B presencePenalty(Double presencePenalty) {
            this.presencePenalty = presencePenalty;
            return builder();
        }

        /**
         * The maximum number of tokens to include in a response candidate.
         *
         * <p><strong>>Note:</strong> The default value varies by model, see the Model.output_token_limit attribute of
         * the Model returned from the getModel function.</p>
         */
        public B maxOutputTokens(Integer maxOutputTokens) {
            this.maxOutputTokens = maxOutputTokens;
            return builder();
        }

        /**
         * Sets the {@code responseMimeType} and optionally {@code responseSchema} if
         * {@link ResponseFormat#jsonSchema()} is set.
         */
        public B responseFormat(ResponseFormat responseFormat) {
            this.responseFormat = responseFormat;
            return builder();
        }

        /**
         * The set of character sequences (up to 5) that will stop output generation. If specified, the API will
         * stop at the first appearance of a stop_sequence. The stop sequence will not be included as
         * part of the response.
         */
        public B stopSequences(List<String> stopSequences) {
            this.stopSequences = stopSequences;
            return builder();
        }

        /**
         * Enabled <a href="https://ai.google.dev/gemini-api/docs/code-execution">codeExecution tool</a> in Gemini.
         */
        public B allowCodeExecution(Boolean allowCodeExecution) {
            this.allowCodeExecution = allowCodeExecution;
            return builder();
        }

        /**
         * Enabled <a href="https://ai.google.dev/gemini-api/docs/google-search">Google Search tool</a> in Gemini.
         */
        public B allowGoogleSearch(Boolean allowGoogleSearch) {
            this.allowGoogleSearch = allowGoogleSearch;
            return builder();
        }

        /**
         * Enables <a href="https://ai.google.dev/gemini-api/docs/maps-grounding">Google Maps tool</a> in Gemini.
         */
        public B allowGoogleMaps(Boolean allowGoogleMaps) {
            this.allowGoogleMaps = allowGoogleMaps;
            return builder();
        }

        /**
         * Retrieve the Google Maps widget <a href="https://ai.google.dev/gemini-api/docs/maps-grounding#display_the_google_maps_contextual_widget">context token</a> in the response for use with the Google Maps JS API.
         */
        public B retrieveGoogleMapsWidgetToken(Boolean retrieveGoogleMapsWidgetToken) {
            this.retrieveGoogleMapsWidgetToken = retrieveGoogleMapsWidgetToken;
            return builder();
        }

        /**
         * Enabled <a href="https://ai.google.dev/gemini-api/docs/url-context">Url Context tool</a> in Gemini.
         */
        public B allowUrlContext(Boolean allowUrlContext) {
            this.allowUrlContext = allowUrlContext;
            return builder();
        }

        /**
         * Safety setting, affecting the safety-blocking behavior. Passing a safety setting for a category changes the
         * allowed probability that content is blocked
         */
        public B safetySettings(List<GeminiSafetySetting> safetySettings) {
            this.safetySettings = safetySettings;
            return builder();
        }

        /**
         * Specifies the config to enable <a href="https://ai.google.dev/gemini-api/docs/thinking">thinking</a>.
         *
         * @see #returnThinking(Boolean)
         * @see #sendThinking(Boolean)
         */
        public B thinkingConfig(GeminiThinkingConfig thinkingConfig) {
            this.thinkingConfig = thinkingConfig;
            return builder();
        }

        /**
         * Controls whether to return thinking/reasoning text (if available) inside {@link AiMessage#thinking()}.
         * Please note that this does not enable thinking/reasoning for the LLM;
         * it only controls whether to parse the {@code thought} block from the API response
         * and return it inside the {@link AiMessage}.
         * <p>
         * Disabled by default.
         * If enabled, the thinking text will be stored within the {@link AiMessage} and may be persisted.
         * If enabled, thinking signatures will also be stored and returned inside the {@link AiMessage#attributes()}.
         * <p>
         * Please note that when {@code returnThinking} is not set (is {@code null}) and {@code thinkingConfig} is set,
         * thinking/reasoning text will be prepended to the actual response inside the {@link AiMessage#text()} field.
         *
         * @see #thinkingConfig(GeminiThinkingConfig)
         * @see #sendThinking(Boolean)
         */
        public B returnThinking(Boolean returnThinking) {
            this.returnThinking = returnThinking;
            return builder();
        }

        /**
         * Controls whether to send thinking/reasoning text to the LLM in follow-up requests.
         *
         * <p>
         * Disabled by default.
         * If enabled, the contents of {@link AiMessage#thinking()} will be sent in the API request.
         * If enabled, thinking signatures (inside the {@link AiMessage#attributes()}) will also be sent.</p>
         *
         * @see #thinkingConfig(GeminiThinkingConfig)
         * @see #returnThinking(Boolean)
         */
        public B sendThinking(Boolean sendThinking) {
            this.sendThinking = sendThinking;
            return builder();
        }

        /**
         * If true, export the logprobs results in response.
         */
        public B responseLogprobs(Boolean responseLogprobs) {
            this.responseLogprobs = responseLogprobs;
            return builder();
        }

        /**
         * Only valid if responseLogprobs=True. This sets the number of top logprobs to return at each decoding step in
         * the Candidate.logprobs_result. The number must be in the range of [0, 20].
         */
        public B logprobs(Integer logprobs) {
            this.logprobs = logprobs;
            return builder();
        }

        /**
         * Enables enhanced civic answers. It may not be available for all models.
         */
        public B enableEnhancedCivicAnswers(Boolean enableEnhancedCivicAnswers) {
            this.enableEnhancedCivicAnswers = enableEnhancedCivicAnswers;
            return builder();
        }

        /**
         * Sets the media resolution level for controlling how the Gemini API processes media inputs
         * like images, videos, and PDF documents.
         */
        public B mediaResolution(GeminiMediaResolutionLevel mediaResolution) {
            this.mediaResolution = mediaResolution;
            return builder();
        }
    }
}
