package dev.langchain4j.model.googleai;

import dev.langchain4j.Experimental;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.Response;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static dev.langchain4j.internal.Utils.copyIfNotNull;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.model.googleai.PartsAndContentsMapper.fromMessageToGContent;
import static dev.langchain4j.model.googleai.SchemaMapper.fromJsonSchemaToGSchema;
import static java.time.Duration.ofSeconds;
import static java.util.Collections.emptyList;

@Experimental
abstract class BaseGeminiChatModel {
    private static final Logger log = org.slf4j.LoggerFactory.getLogger(BaseGeminiChatModel.class);
    protected final GeminiService geminiService;
    protected final String apiKey;
    protected final String modelName;
    protected final Double temperature;
    protected final Integer topK;
    protected final Double topP;
    protected final Integer maxOutputTokens;
    protected final List<String> stopSequences;
    protected final ResponseFormat responseFormat;
    protected final GeminiFunctionCallingConfig toolConfig;
    protected final boolean allowCodeExecution;
    protected final boolean includeCodeExecutionOutput;
    protected final List<GeminiSafetySetting> safetySettings;
    protected final List<ChatModelListener> listeners;
    protected final Integer maxRetries;

    protected BaseGeminiChatModel(
            String apiKey,
            String modelName,
            Double temperature,
            Integer topK,
            Double topP,
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
            Integer maxRetries
    ) {
        this.apiKey = ensureNotBlank(apiKey, "apiKey");
        this.modelName = ensureNotBlank(modelName, "modelName");
        this.temperature = temperature;
        this.topK = topK;
        this.topP = topP;
        this.maxOutputTokens = maxOutputTokens;
        this.stopSequences = getOrDefault(stopSequences, emptyList());
        this.toolConfig = toolConfig;
        this.allowCodeExecution = getOrDefault(allowCodeExecution, false);
        this.includeCodeExecutionOutput = getOrDefault(includeCodeExecutionOutput, false);
        this.safetySettings = copyIfNotNull(safetySettings);
        this.responseFormat = responseFormat;
        this.listeners = listeners == null ? emptyList() : new ArrayList<>(listeners);
        this.maxRetries = getOrDefault(maxRetries, 2);
        this.geminiService = new GeminiService(
                getOrDefault(logRequestsAndResponses, false) ? log : null,
                getOrDefault(timeout, ofSeconds(60))
        );
    }

    protected GeminiGenerateContentRequest createGenerateContentRequest(
            List<ChatMessage> messages,
            List<ToolSpecification> toolSpecifications,
            ResponseFormat responseFormat,
            ChatRequestParameters requestParameters
    ) {
        GeminiContent systemInstruction = new GeminiContent(GeminiRole.MODEL.toString());
        List<GeminiContent> geminiContentList = fromMessageToGContent(messages, systemInstruction);

        GeminiSchema schema = null;
        if (responseFormat != null && responseFormat.jsonSchema() != null) {
            schema = fromJsonSchemaToGSchema(responseFormat.jsonSchema());
        }

        return GeminiGenerateContentRequest.builder()
                .contents(geminiContentList)
                .systemInstruction(!systemInstruction.getParts().isEmpty() ? systemInstruction : null)
                .generationConfig(GeminiGenerationConfig.builder()
                        .candidateCount(1) // Multiple candidates aren't supported by langchain4j
                        .maxOutputTokens(getOrDefault(requestParameters.maxOutputTokens(), this.maxOutputTokens))
                        .responseMimeType(computeMimeType(responseFormat))
                        .responseSchema(schema)
                        .stopSequences(getOrDefault(requestParameters.stopSequences(), this.stopSequences))
                        .temperature(getOrDefault(requestParameters.temperature(), this.temperature))
                        .topK(getOrDefault(requestParameters.topK(), this.topK))
                        .topP(getOrDefault(requestParameters.topP(), this.topP))
                        .build())
                .safetySettings(this.safetySettings)
                .tools(FunctionMapper.fromToolSepcsToGTool(toolSpecifications, this.allowCodeExecution))
                .toolConfig(new GeminiToolConfig(this.toolConfig))
                .build();
    }

    protected ChatRequest createListenerRequest(
            String modelName,
            List<ChatMessage> messages,
            List<ToolSpecification> toolSpecifications,
            ChatRequestParameters requestParameters
    ) {
        return ChatRequest.builder()
                .messages(messages)
                .parameters(ChatRequestParameters.builder()
                        .modelName(getOrDefault(modelName, this.modelName))
                        .temperature(getOrDefault(requestParameters.temperature(), this.temperature))
                        .topP(getOrDefault(requestParameters.topP(), this.topP))
                        .maxOutputTokens(getOrDefault(requestParameters.maxOutputTokens(), this.maxOutputTokens))
                        .toolSpecifications(toolSpecifications)
                        .build())
                .build();
    }

    protected static String computeMimeType(ResponseFormat responseFormat) {
        if (responseFormat == null || ResponseFormatType.TEXT.equals(responseFormat.type())) {
            return "text/plain";
        }

        if (ResponseFormatType.JSON.equals(responseFormat.type()) &&
                responseFormat.jsonSchema() != null &&
                responseFormat.jsonSchema().rootElement() != null &&
                responseFormat.jsonSchema().rootElement() instanceof JsonEnumSchema) {
            return "text/x.enum";
        }

        return "application/json";
    }

    protected void notifyListenersOnRequest(ChatModelRequestContext context) {
        listeners.forEach((listener) -> {
            try {
                listener.onRequest(context);
            } catch (Exception e) {
                log.warn("Exception while calling model listener (onRequest)", e);
            }
        });
    }

    protected void notifyListenersOnResponse(Response<AiMessage> response,
                                             ChatRequest listenerRequest,
                                             ModelProvider modelProvider,
                                             Map<Object, Object> attributes) {
        ChatResponse LISTENERResponse = ChatResponse.builder()
                .aiMessage(response.content())
                .metadata(ChatResponseMetadata.builder()
                        // TODO take actual modelName from response or return null?
                        .modelName(listenerRequest.parameters().modelName())
                        .tokenUsage(response.tokenUsage())
                        .finishReason(response.finishReason())
                        .build())
                .build();
        ChatModelResponseContext context = new ChatModelResponseContext(
                LISTENERResponse, listenerRequest, modelProvider, attributes);
        listeners.forEach((listener) -> {
            try {
                listener.onResponse(context);
            } catch (Exception e) {
                log.warn("Exception while calling model listener (onResponse)", e);
            }
        });
    }

    protected void notifyListenersOnError(Exception exception,
                                          ChatRequest listenerRequest,
                                          ModelProvider modelProvider,
                                          Map<Object, Object> attributes) {
        listeners.forEach((listener) -> {
            try {
                ChatModelErrorContext context = new ChatModelErrorContext(
                        exception, listenerRequest, modelProvider, attributes);
                listener.onError(context);
            } catch (Exception e) {
                log.warn("Exception while calling model listener (onError)", e);
            }
        });
    }
}

