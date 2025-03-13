package dev.langchain4j.model.googleai;

import dev.langchain4j.Experimental;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequest;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ChatRequestValidator;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.Response;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

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
        ChatModelRequest chatModelRequest = createChatModelRequest(null, messages, toolSpecifications, parameters);

        ConcurrentHashMap<Object, Object> listenerAttributes = new ConcurrentHashMap<>();
        ChatModelRequestContext chatModelRequestContext = new ChatModelRequestContext(chatModelRequest, provider(), listenerAttributes);
        notifyListenersOnRequest(chatModelRequestContext);

        processGenerateContentRequest(request, handler, chatModelRequest, listenerAttributes);
    }

    private void processGenerateContentRequest(GeminiGenerateContentRequest request, StreamingResponseHandler<AiMessage> handler,
                                               ChatModelRequest chatModelRequest, ConcurrentHashMap<Object, Object> listenerAttributes) {
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

            notifyListenersOnResponse(fullResponse, chatModelRequest, provider(), listenerAttributes);
        } catch (RuntimeException exception) {
            notifyListenersOnError(exception, chatModelRequest, provider(), listenerAttributes);
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
