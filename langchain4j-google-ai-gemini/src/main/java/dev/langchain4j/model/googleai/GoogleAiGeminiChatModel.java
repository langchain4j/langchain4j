package dev.langchain4j.model.googleai;

import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptions;
import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.model.ModelProvider.GOOGLE_AI_GEMINI;
import static dev.langchain4j.model.chat.Capability.RESPONSE_FORMAT_JSON_SCHEMA;
import static java.util.Arrays.asList;

import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.response.ChatResponse;
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

    @Override
    public Set<Capability> supportedCapabilities() {
        Set<Capability> capabilities = new HashSet<>(supportedCapabilities);
        // when responses format is not null, it's JSON, either application/json or text/x.enum
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
