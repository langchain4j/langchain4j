package dev.langchain4j.model.googleai;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.TokenCountEstimator;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestValidator;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptions;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.model.ModelProvider.GOOGLE_AI_GEMINI;
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
    public ChatResponse chat(ChatRequest chatRequest) {

        ChatRequestParameters parameters = chatRequest.parameters();
        validate(parameters);
        ChatRequestValidator.validate(parameters.toolChoice());

        GeminiGenerateContentRequest request = createGenerateContentRequest(
                chatRequest.messages(),
                parameters.toolSpecifications(),
                getOrDefault(parameters.responseFormat(), this.responseFormat),
                chatRequest.parameters()
        );

        ChatRequest listenerRequest = createListenerRequest(
                parameters.modelName(),
                chatRequest.messages(),
                parameters.toolSpecifications(),
                chatRequest.parameters()
        );

        ConcurrentHashMap<Object, Object> listenerAttributes = new ConcurrentHashMap<>();
        notifyListenersOnRequest(new ChatModelRequestContext(listenerRequest, provider(), listenerAttributes));

        try {
            GeminiGenerateContentResponse geminiResponse = withRetryMappingExceptions(
                () -> this.geminiService.generateContent(this.modelName, this.apiKey, request),
                this.maxRetries
            );

            return processResponse(geminiResponse, listenerRequest, listenerAttributes);
        } catch (RuntimeException e) {
            notifyListenersOnError(e, listenerRequest, provider(), listenerAttributes);
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
        ChatRequest listenerRequest,
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
        notifyListenersOnResponse(response, listenerRequest, provider(), listenerAttributes);

        return ChatResponse.builder()
                .aiMessage(aiMessage)
                .metadata(ChatResponseMetadata.builder()
                        // TODO take actual modelName from response or return null?
                        .modelName(listenerRequest.parameters().modelName())
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

    @Override
    public List<ChatModelListener> listeners() {
        return listeners;
    }

    @Override
    public ModelProvider provider() {
        return GOOGLE_AI_GEMINI;
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
