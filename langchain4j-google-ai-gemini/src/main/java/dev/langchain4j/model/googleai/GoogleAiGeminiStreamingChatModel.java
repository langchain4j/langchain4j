package dev.langchain4j.model.googleai;

import dev.langchain4j.Experimental;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequest;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.output.Response;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static dev.langchain4j.model.chat.policy.RetryUtils.withRetry;

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
        ChatRequestParameters parameters = ChatRequestParameters.builder().build();
        GeminiGenerateContentRequest request = createGenerateContentRequest(messages, toolSpecifications, this.responseFormat, parameters);
        ChatModelRequest chatModelRequest = createChatModelRequest(null, messages, toolSpecifications, parameters);

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

    public static class GoogleAiGeminiStreamingChatModelBuilder {
        public GoogleAiGeminiStreamingChatModelBuilder toolConfig(GeminiMode mode, String... allowedFunctionNames){
            this.toolConfig = new GeminiFunctionCallingConfig(mode, Arrays.asList(allowedFunctionNames));
            return this;
        }

        public GoogleAiGeminiStreamingChatModelBuilder safetySettings(Map<GeminiHarmCategory, GeminiHarmBlockThreshold> safetySettingMap) {
            this.safetySettings = safetySettingMap.entrySet().stream()
                    .map(entry -> new GeminiSafetySetting(entry.getKey(), entry.getValue())
                    ).collect(Collectors.toList());
            return this;
        }
    }
}
