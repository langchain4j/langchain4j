package dev.langchain4j.model.googleai;

import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptions;
import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.model.ModelProvider.GOOGLE_AI_GEMINI;
import static dev.langchain4j.model.chat.Capability.RESPONSE_FORMAT_JSON_SCHEMA;
import static dev.langchain4j.model.googleai.FinishReasonMapper.fromGFinishReasonToFinishReason;
import static dev.langchain4j.model.googleai.PartsAndContentsMapper.fromGPartsToAiMessage;
import static dev.langchain4j.model.output.FinishReason.TOOL_EXECUTION;
import static java.util.Arrays.asList;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.googleai.GeminiGenerateContentResponse.GeminiCandidate;
import dev.langchain4j.model.googleai.GeminiGenerateContentResponse.GeminiUsageMetadata;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GoogleAiGeminiChatModel extends BaseGeminiChatModel implements ChatModel {
    private final Set<Capability> supportedCapabilities;
    private final Integer maximumRetries;

    public GoogleAiGeminiChatModel(GoogleAiGeminiChatModelBuilder builder) {
        this(builder, buildGeminiService(builder));
    }

    GoogleAiGeminiChatModel(GoogleAiGeminiChatModelBuilder builder, GeminiService geminiService) {
        super(builder, geminiService);
        this.maximumRetries = getOrDefault(builder.maxRetries, 2);
        this.supportedCapabilities = copy(builder.supportedCapabilities);
    }

    public static GoogleAiGeminiChatModelBuilder builder() {
        return new GoogleAiGeminiChatModelBuilder();
    }

    @Override
    public ChatRequestParameters defaultRequestParameters() {
        return defaultRequestParameters;
    }

    @Override
    public ChatResponse doChat(ChatRequest chatRequest) {
        GeminiGenerateContentRequest request = createGenerateContentRequest(chatRequest);

        GeminiGenerateContentResponse geminiResponse = withRetryMappingExceptions(
                () -> geminiService.generateContent(chatRequest.modelName(), request), maximumRetries);

        return processResponse(geminiResponse);
    }

    private ChatResponse processResponse(GeminiGenerateContentResponse geminiResponse) {
        GeminiCandidate firstCandidate = geminiResponse.candidates().get(0);
        AiMessage aiMessage = createAiMessage(firstCandidate);

        FinishReason finishReason = fromGFinishReasonToFinishReason(firstCandidate.finishReason());
        if (aiMessage != null && aiMessage.hasToolExecutionRequests()) {
            finishReason = TOOL_EXECUTION;
        }

        return ChatResponse.builder()
                .aiMessage(aiMessage)
                .metadata(ChatResponseMetadata.builder()
                        .id(geminiResponse.responseId())
                        .modelName(geminiResponse.modelVersion())
                        .tokenUsage(createTokenUsage(geminiResponse.usageMetadata()))
                        .finishReason(finishReason)
                        .build())
                .build();
    }

    private AiMessage createAiMessage(GeminiCandidate candidate) {
        if (candidate == null || candidate.content() == null) {
            return fromGPartsToAiMessage(List.of(), includeCodeExecutionOutput, returnThinking);
        }

        return fromGPartsToAiMessage(candidate.content().parts(), includeCodeExecutionOutput, returnThinking);
    }

    private TokenUsage createTokenUsage(GeminiUsageMetadata tokenCounts) {
        return new TokenUsage(
                tokenCounts.promptTokenCount(), tokenCounts.candidatesTokenCount(), tokenCounts.totalTokenCount());
    }

    @Override
    public Set<Capability> supportedCapabilities() {
        Set<Capability> capabilities = new HashSet<>(supportedCapabilities);
        // when response format is not null, it's JSON, either application/json or text/x.enum
        ResponseFormat responseFormat = this.defaultRequestParameters.responseFormat();
        if (responseFormat != null && ResponseFormatType.JSON.equals(responseFormat.type())) {
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

    public static final class GoogleAiGeminiChatModelBuilder
            extends GoogleAiGeminiChatModelBaseBuilder<GoogleAiGeminiChatModelBuilder> {
        private Integer maxRetries;
        private Set<Capability> supportedCapabilities;

        private GoogleAiGeminiChatModelBuilder() {}

        public GoogleAiGeminiChatModelBuilder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public GoogleAiGeminiChatModelBuilder supportedCapabilities(Set<Capability> supportedCapabilities) {
            this.supportedCapabilities = supportedCapabilities;
            return this;
        }

        public GoogleAiGeminiChatModelBuilder supportedCapabilities(Capability... supportedCapabilities) {
            return supportedCapabilities(new HashSet<>(asList(supportedCapabilities)));
        }

        public GoogleAiGeminiChatModel build() {
            return new GoogleAiGeminiChatModel(this);
        }

        GoogleAiGeminiChatModel build(GeminiService geminiService) {
            return new GoogleAiGeminiChatModel(this, geminiService);
        }
    }
}
