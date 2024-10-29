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
import java.util.stream.Stream;

import static dev.langchain4j.internal.RetryUtils.withRetry;
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
public class GoogleAiGeminiStreamingChatModel extends BaseGeminiChatModel implements StreamingChatLanguageModel {
    @Builder
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

    @Override
    public void generate(List<ChatMessage> messages, StreamingResponseHandler<AiMessage> handler) {
        generate(messages, Collections.emptyList(), handler);
    }

    @Override
    public void generate(List<ChatMessage> messages, ToolSpecification toolSpecification, StreamingResponseHandler<AiMessage> handler) {
        throw new RuntimeException("This method is not supported: Gemini AI cannot be forced to execute a tool.");
    }

    @Override
    public void generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications, StreamingResponseHandler<AiMessage> handler) {
        GeminiGenerateContentRequest request = createGenerateContentRequest(messages, toolSpecifications, this.responseFormat);
        ChatModelRequest chatModelRequest = createChatModelRequest(messages, toolSpecifications);

        ConcurrentHashMap<Object, Object> listenerAttributes = new ConcurrentHashMap<>();
        ChatModelRequestContext chatModelRequestContext = new ChatModelRequestContext(chatModelRequest, listenerAttributes);
        notifyListenersOnRequest(chatModelRequestContext);

        processGenerateContentRequest(request, handler, chatModelRequest, listenerAttributes);
    }

    private void processGenerateContentRequest(GeminiGenerateContentRequest request, StreamingResponseHandler<AiMessage> handler,
                                               ChatModelRequest chatModelRequest, ConcurrentHashMap<Object, Object> listenerAttributes) {
        GeminiStreamingResponseBuilder responseBuilder = new GeminiStreamingResponseBuilder(this.includeCodeExecutionOutput);

        try {
            Stream<GeminiGenerateContentResponse> contentStream = withRetry(
                () -> this.geminiService.generateContentStream(this.modelName, this.apiKey, request),
                maxRetries);

            contentStream.forEach(partialResponse -> {
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
}
