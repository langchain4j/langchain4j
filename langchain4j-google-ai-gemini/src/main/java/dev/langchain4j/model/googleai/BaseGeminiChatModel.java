package dev.langchain4j.model.googleai;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.copyIfNotNull;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.model.googleai.FunctionMapper.fromToolSepcsToGTool;
import static dev.langchain4j.model.googleai.PartsAndContentsMapper.fromMessageToGContent;
import static dev.langchain4j.model.googleai.SchemaMapper.fromJsonSchemaToGSchema;

import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

abstract class BaseGeminiChatModel {

    protected final GeminiService geminiService;
    protected final String apiKey;
    protected final GeminiFunctionCallingConfig functionCallingConfig;
    protected final boolean allowCodeExecution;
    protected final boolean includeCodeExecutionOutput;
    protected final List<GeminiSafetySetting> safetySettings;
    protected final List<ChatModelListener> listeners;
    protected final Integer maxRetries;
    protected final GeminiThinkingConfig thinkingConfig;
    protected final Integer seed;

    protected final ChatRequestParameters defaultRequestParameters;

    protected BaseGeminiChatModel(
            HttpClientBuilder httpClientBuilder,
            String apiKey,
            String modelName,
            Double temperature,
            Integer topK,
            Integer seed,
            Double topP,
            Double frequencyPenalty,
            Double presencePenalty,
            Integer maxOutputTokens,
            Duration timeout,
            ResponseFormat responseFormat,
            List<String> stopSequences,
            GeminiFunctionCallingConfig functionCallingConfig,
            Boolean allowCodeExecution,
            Boolean includeCodeExecutionOutput,
            Boolean logRequestsAndResponses,
            List<GeminiSafetySetting> safetySettings,
            List<ChatModelListener> listeners,
            Integer maxRetries,
            GeminiThinkingConfig thinkingConfig,
            ChatRequestParameters defaultRequestParameters) {
        this.apiKey = ensureNotBlank(apiKey, "apiKey");
        this.functionCallingConfig = functionCallingConfig;
        this.allowCodeExecution = getOrDefault(allowCodeExecution, false);
        this.includeCodeExecutionOutput = getOrDefault(includeCodeExecutionOutput, false);
        this.safetySettings = copyIfNotNull(safetySettings);
        this.listeners = copy(listeners);
        this.maxRetries = getOrDefault(maxRetries, 2);
        this.thinkingConfig = thinkingConfig;
        this.seed = seed;
        this.geminiService =
                new GeminiService(httpClientBuilder, getOrDefault(logRequestsAndResponses, false), timeout);

        ChatRequestParameters parameters;
        if (defaultRequestParameters != null) {
            parameters = defaultRequestParameters;
        } else {
            parameters = DefaultChatRequestParameters.EMPTY;
        }

        this.defaultRequestParameters = ChatRequestParameters.builder()
                .modelName(getOrDefault(modelName, parameters.modelName()))
                .temperature(getOrDefault(temperature, parameters.temperature()))
                .topP(getOrDefault(topP, parameters.topP()))
                .topK(getOrDefault(topK, parameters.topK()))
                .frequencyPenalty(getOrDefault(frequencyPenalty, parameters.frequencyPenalty()))
                .presencePenalty(getOrDefault(presencePenalty, parameters.presencePenalty()))
                .maxOutputTokens(getOrDefault(maxOutputTokens, parameters.maxOutputTokens()))
                .stopSequences(getOrDefault(stopSequences, parameters.stopSequences()))
                .toolSpecifications(parameters.toolSpecifications())
                .toolChoice(getOrDefault(toToolChoice(functionCallingConfig), parameters.toolChoice()))
                .responseFormat(getOrDefault(responseFormat, parameters.responseFormat()))
                .build();
    }

    protected GeminiGenerateContentRequest createGenerateContentRequest(ChatRequest chatRequest) {
        ChatRequestParameters parameters = chatRequest.parameters();

        GeminiContent systemInstruction = new GeminiContent(GeminiRole.MODEL.toString());
        List<GeminiContent> geminiContentList = fromMessageToGContent(chatRequest.messages(), systemInstruction);

        ResponseFormat responseFormat = chatRequest.responseFormat();
        GeminiSchema schema = null;
        if (responseFormat != null && responseFormat.jsonSchema() != null) {
            schema = fromJsonSchemaToGSchema(responseFormat.jsonSchema());
        }

        return GeminiGenerateContentRequest.builder()
                .model(chatRequest.modelName())
                .contents(geminiContentList)
                .systemInstruction(!systemInstruction.getParts().isEmpty() ? systemInstruction : null)
                .generationConfig(GeminiGenerationConfig.builder()
                        .candidateCount(1) // Multiple candidates aren't supported by langchain4j
                        .maxOutputTokens(parameters.maxOutputTokens())
                        .responseMimeType(computeMimeType(responseFormat))
                        .responseSchema(schema)
                        .stopSequences(parameters.stopSequences())
                        .temperature(parameters.temperature())
                        .topK(parameters.topK())
                        .seed(seed)
                        .topP(parameters.topP())
                        .presencePenalty(parameters.presencePenalty())
                        .frequencyPenalty(parameters.frequencyPenalty())
                        .thinkingConfig(this.thinkingConfig)
                        .build())
                .safetySettings(this.safetySettings)
                .tools(fromToolSepcsToGTool(chatRequest.toolSpecifications(), this.allowCodeExecution))
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
}
