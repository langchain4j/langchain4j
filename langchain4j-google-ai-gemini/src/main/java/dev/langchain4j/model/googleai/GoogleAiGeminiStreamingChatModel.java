package dev.langchain4j.model.googleai;

import dev.langchain4j.Experimental;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.listener.*;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.output.Response;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static dev.langchain4j.internal.Utils.copyIfNotNull;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.model.googleai.PartsAndContentsMapper.fromMessageToGContent;
import static dev.langchain4j.model.googleai.SchemaMapper.fromJsonSchemaToGSchema;
import static java.time.Duration.ofSeconds;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

@Experimental
@Slf4j
public class GoogleAiGeminiStreamingChatModel implements StreamingChatLanguageModel {
    private final GeminiService geminiService;
    private final String apiKey;
    private final String modelName;
    private final Double temperature;
    private final Integer topK;
    private final Double topP;
    private final Integer maxOutputTokens;
    private final List<String> stopSequences;
    private final Integer candidateCount;
    private final ResponseFormat responseFormat;
    private final GeminiFunctionCallingConfig toolConfig;
    private final boolean allowCodeExecution;
    private final boolean includeCodeExecutionOutput;
    private final List<GeminiSafetySetting> safetySettings;
    private final List<ChatModelListener> listeners;

    @Builder
    public GoogleAiGeminiStreamingChatModel(String apiKey, String modelName,
                                            Double temperature, Integer topK, Double topP,
                                            Integer maxOutputTokens, Duration timeout,
                                            ResponseFormat responseFormat,
                                            List<String> stopSequences, GeminiFunctionCallingConfig toolConfig,
                                            Boolean allowCodeExecution, Boolean includeCodeExecutionOutput,
                                            Boolean logRequestsAndResponses,
                                            List<GeminiSafetySetting> safetySettings,
                                            List<ChatModelListener> listeners) {
        this.apiKey = ensureNotBlank(apiKey, "apiKey");
        this.modelName = ensureNotBlank(modelName, "modelName");
        this.temperature = temperature;
        this.topK = topK;
        this.topP = topP;
        this.maxOutputTokens = maxOutputTokens;
        this.candidateCount = 1; // Multiple candidates aren't supported by langchain4j
        this.stopSequences = getOrDefault(stopSequences, emptyList());
        this.toolConfig = toolConfig;
        this.allowCodeExecution = getOrDefault(allowCodeExecution, false);
        this.includeCodeExecutionOutput = getOrDefault(includeCodeExecutionOutput, false);
        this.safetySettings = copyIfNotNull(safetySettings);
        this.responseFormat = responseFormat;
        this.listeners = listeners == null ? emptyList() : new ArrayList<>(listeners);
        this.geminiService = new GeminiService(
                getOrDefault(logRequestsAndResponses, false) ? log : null,
                getOrDefault(timeout, ofSeconds(60))
        );
    }

    @Override
    public void generate(List<ChatMessage> messages, StreamingResponseHandler<AiMessage> handler) {
        generate(messages, Collections.emptyList(), handler);
    }

    @Override
    public void generate(List<ChatMessage> messages, ToolSpecification toolSpecification, StreamingResponseHandler<AiMessage> handler) {
        generate(messages, singletonList(toolSpecification), handler);
    }

    @Override
    public void generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications, StreamingResponseHandler<AiMessage> handler) {
        GeminiGenerateContentRequest request = createGenerateContentRequest(messages, toolSpecifications);
        ChatModelRequest chatModelRequest = createChatModelRequest(messages, toolSpecifications);

        ConcurrentHashMap<Object, Object> listenerAttributes = new ConcurrentHashMap<>();
        ChatModelRequestContext chatModelRequestContext = new ChatModelRequestContext(chatModelRequest, listenerAttributes);
        notifyListenersOnRequest(chatModelRequestContext);

        processGenerateContentRequest(request, handler, chatModelRequest, listenerAttributes);
    }

    private GeminiGenerateContentRequest createGenerateContentRequest(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
        GeminiContent systemInstruction = new GeminiContent(GeminiRole.MODEL.toString());
        List<GeminiContent> geminiContentList = fromMessageToGContent(messages, systemInstruction);

        GeminiSchema schema = null;
        if (this.responseFormat != null && this.responseFormat.jsonSchema() != null) {
            schema = fromJsonSchemaToGSchema(this.responseFormat.jsonSchema());
        }

        return GeminiGenerateContentRequest.builder()
                .contents(geminiContentList)
                .systemInstruction(!systemInstruction.getParts().isEmpty() ? systemInstruction : null)
                .generationConfig(GeminiGenerationConfig.builder()
                        .candidateCount(this.candidateCount)
                        .maxOutputTokens(this.maxOutputTokens)
                        .responseMimeType(computeMimeType(this.responseFormat))
                        .responseSchema(schema)
                        .stopSequences(this.stopSequences)
                        .temperature(this.temperature)
                        .topK(this.topK)
                        .topP(this.topP)
                        .build())
                .safetySettings(this.safetySettings)
                .tools(FunctionMapper.fromToolSepcsToGTool(toolSpecifications, this.allowCodeExecution))
                .toolConfig(new GeminiToolConfig(this.toolConfig))
                .build();
    }

    private ChatModelRequest createChatModelRequest(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
        return ChatModelRequest.builder()
                .model(modelName)
                .temperature(temperature)
                .topP(topP)
                .maxTokens(maxOutputTokens)
                .messages(messages)
                .toolSpecifications(toolSpecifications)
                .build();
    }

    private void processGenerateContentRequest(GeminiGenerateContentRequest request, StreamingResponseHandler<AiMessage> handler,
                                               ChatModelRequest chatModelRequest, ConcurrentHashMap<Object, Object> listenerAttributes) {
        GeminiStreamingResponseBuilder responseBuilder = new GeminiStreamingResponseBuilder(this.includeCodeExecutionOutput);

        try {
            this.geminiService.generateContentStream(this.modelName, this.apiKey, request)
                    .forEach(partialResponse -> {
                        Optional<String> text = responseBuilder.append(partialResponse);
                        text.ifPresent(handler::onNext);
                    });

            Response<AiMessage> fullResponse = responseBuilder.build();
            handler.onComplete(fullResponse);

            notifyListenersOnResponse(fullResponse, chatModelRequest, listenerAttributes);
        } catch (RuntimeException exception) {
            notifyListenersOnError(exception, chatModelRequest, listenerAttributes);
            handler.onError(exception);
        }
    }

    private static String computeMimeType(ResponseFormat responseFormat) {
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

    private void notifyListenersOnRequest(ChatModelRequestContext context) {
        listeners.forEach((listener) -> {
            try {
                listener.onRequest(context);
            } catch (Exception e) {
                log.warn("Exception while calling model listener (onRequest)", e);
            }
        });
    }

    private void notifyListenersOnResponse(Response<AiMessage> fullResponse, ChatModelRequest chatModelRequest,
                                           ConcurrentHashMap<Object, Object> listenerAttributes) {
        ChatModelResponse chatModelResponse = ChatModelResponse.builder()
                .model(modelName)
                .tokenUsage(fullResponse.tokenUsage())
                .finishReason(fullResponse.finishReason())
                .aiMessage(fullResponse.content())
                .build();
        ChatModelResponseContext chatModelResponseContext = new ChatModelResponseContext(
                chatModelResponse, chatModelRequest, listenerAttributes);
        listeners.forEach((listener) -> {
            try {
                listener.onResponse(chatModelResponseContext);
            } catch (Exception e) {
                log.warn("Exception while calling model listener (onResponse)", e);
            }
        });
    }

    private void notifyListenersOnError(Exception exception, ChatModelRequest chatModelRequest,
                                        ConcurrentHashMap<Object, Object> listenerAttributes) {
        listeners.forEach((listener) -> {
            try {
                ChatModelErrorContext chatModelErrorContext =
                        new ChatModelErrorContext(exception, chatModelRequest, null, listenerAttributes);
                listener.onError(chatModelErrorContext);
            } catch (Exception t) {
                log.warn("Exception while calling model listener (onError)", t);
            }
        });
    }
}