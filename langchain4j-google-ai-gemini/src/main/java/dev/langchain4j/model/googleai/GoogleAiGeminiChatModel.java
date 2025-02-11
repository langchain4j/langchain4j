package dev.langchain4j.model.googleai;

import dev.langchain4j.Experimental;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.TokenCountEstimator;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequest;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static dev.langchain4j.model.chat.policy.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.model.chat.Capability.RESPONSE_FORMAT_JSON_SCHEMA;
import static dev.langchain4j.model.googleai.FinishReasonMapper.fromGFinishReasonToFinishReason;
import static dev.langchain4j.model.googleai.PartsAndContentsMapper.fromGPartsToAiMessage;
import static java.time.Duration.ofSeconds;

@Experimental
@Slf4j
public class GoogleAiGeminiChatModel extends BaseGeminiChatModel implements ChatLanguageModel, TokenCountEstimator {
    private final GoogleAiGeminiTokenizer geminiTokenizer;

    @Builder
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
        super(apiKey, modelName, temperature, topK, topP, maxOutputTokens, timeout,
            responseFormat, stopSequences, toolConfig, allowCodeExecution,
            includeCodeExecutionOutput, logRequestsAndResponses, safetySettings,
            listeners, maxRetries);

        this.geminiTokenizer = GoogleAiGeminiTokenizer.builder()
            .modelName(this.modelName)
            .apiKey(this.apiKey)
            .timeout(getOrDefault(timeout, ofSeconds(60)))
            .maxRetries(this.maxRetries)
            .logRequestsAndResponses(getOrDefault(logRequestsAndResponses, false))
            .build();
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {
        ChatRequest request = ChatRequest.builder()
            .messages(messages)
            .build();

        ChatResponse response = chat(request);

        return Response.from(
                response.aiMessage(),
                response.metadata().tokenUsage(),
                response.metadata().finishReason()
        );
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, ToolSpecification toolSpecification) {
        return generate(messages, Collections.singletonList(toolSpecification));
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
        ChatRequest request = ChatRequest.builder()
            .messages(messages)
            .toolSpecifications(toolSpecifications)
            .build();

        ChatResponse response = chat(request);

        return Response.from(
                response.aiMessage(),
                response.metadata().tokenUsage(),
                response.metadata().finishReason()
        );
    }

    @Override
    public ChatResponse chat(ChatRequest chatRequest) {

        ChatRequestParameters parameters = chatRequest.parameters();
        validate(parameters);
        ChatLanguageModel.validate(parameters.toolChoice());

        GeminiGenerateContentRequest request = createGenerateContentRequest(
                chatRequest.messages(),
                parameters.toolSpecifications(),
                getOrDefault(parameters.responseFormat(), this.responseFormat),
                chatRequest.parameters()
        );

        ChatModelRequest chatModelRequest = createChatModelRequest(
                parameters.modelName(),
                chatRequest.messages(),
                parameters.toolSpecifications(),
                chatRequest.parameters()
        );

        ConcurrentHashMap<Object, Object> listenerAttributes = new ConcurrentHashMap<>();
        notifyListenersOnRequest(new ChatModelRequestContext(chatModelRequest, listenerAttributes));

        try {
            GeminiGenerateContentResponse geminiResponse = withRetry(
                () -> this.geminiService.generateContent(this.modelName, this.apiKey, request),
                this.maxRetries
            );

            return processResponse(geminiResponse, chatModelRequest, listenerAttributes);
        } catch (RuntimeException e) {
            notifyListenersOnError(e, chatModelRequest, listenerAttributes);
            throw e;
        }
    }

    private static void validate(ChatRequestParameters parameters) {
        if (parameters.frequencyPenalty() != null) {
            throw new UnsupportedFeatureException("'frequencyPenalty' parameter is not supported by Google AI Gemini");
        }
        if (parameters.presencePenalty() != null) {
            throw new UnsupportedFeatureException("'presencePenalty' parameter is not supported by Google AI Gemini");
        }
    }

    private ChatResponse processResponse(
        GeminiGenerateContentResponse geminiResponse,
        ChatModelRequest chatModelRequest,
        ConcurrentHashMap<Object, Object> listenerAttributes
    ) {
        if (geminiResponse == null) {
            throw new RuntimeException("Gemini response was null");
        }

        GeminiCandidate firstCandidate = geminiResponse.getCandidates().get(0);
        GeminiUsageMetadata tokenCounts = geminiResponse.getUsageMetadata();

        FinishReason finishReason = fromGFinishReasonToFinishReason(firstCandidate.getFinishReason());
        AiMessage aiMessage = createAiMessage(firstCandidate, finishReason);
        TokenUsage tokenUsage = createTokenUsage(tokenCounts);

        Response<AiMessage> response = Response.from(aiMessage, tokenUsage, finishReason);
        notifyListenersOnResponse(response, chatModelRequest, listenerAttributes);

        return ChatResponse.builder()
                .aiMessage(aiMessage)
                .metadata(ChatResponseMetadata.builder()
                        .modelName(chatModelRequest.model()) // TODO take actual model from response or return null?
                        .finishReason(finishReason)
                        .tokenUsage(tokenUsage)
                        .build())
                .build();
    }

    private AiMessage createAiMessage(GeminiCandidate candidate, FinishReason finishReason) {
        if (candidate.getContent() == null) {
            return AiMessage.from("No text was returned by the model. " +
                "The model finished generating because of the following reason: " + finishReason);
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
    public int estimateTokenCount(List<ChatMessage> messages) {
        return geminiTokenizer.estimateTokenCountInMessages(messages);
    }

    @Override
    public Set<Capability> supportedCapabilities() {
        Set<Capability> capabilities = new HashSet<>();
        // when response format is not null, it's JSON, either application/json or text/x.enum
        if (this.responseFormat != null && ResponseFormatType.JSON.equals(this.responseFormat.type())) {
            capabilities.add(RESPONSE_FORMAT_JSON_SCHEMA);
        }
        return capabilities;
    }

    public static class GoogleAiGeminiChatModelBuilder {
        public GoogleAiGeminiChatModelBuilder toolConfig(GeminiMode mode, String... allowedFunctionNames) {
            this.toolConfig = new GeminiFunctionCallingConfig(mode, Arrays.asList(allowedFunctionNames));
            return this;
        }

        public GoogleAiGeminiChatModelBuilder safetySettings(Map<GeminiHarmCategory, GeminiHarmBlockThreshold> safetySettingMap) {
            this.safetySettings = safetySettingMap.entrySet().stream()
                .map(entry -> new GeminiSafetySetting(entry.getKey(), entry.getValue())
                ).collect(Collectors.toList());
            return this;
        }
    }
}
