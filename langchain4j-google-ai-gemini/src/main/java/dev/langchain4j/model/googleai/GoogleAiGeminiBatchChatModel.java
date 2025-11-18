package dev.langchain4j.model.googleai;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchGenerateContentResponse;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Batch chat model for Google AI Gemini.
 */
@Experimental
public final class GoogleAiGeminiBatchChatModel extends AbstractGeminiBatchClient<ChatRequest, ChatResponse> {

    private final BaseGeminiChatModel chatModel;

    GoogleAiGeminiBatchChatModel(final Builder builder) {
        this(builder, BaseGeminiChatModel.buildGeminiService(builder));
    }

    GoogleAiGeminiBatchChatModel(final Builder builder, final GeminiService geminiService) {
        super(geminiService);
        this.chatModel = new BaseGeminiChatModel(builder, geminiService);
    }

    /**
     * Creates and enqueues a batch of chat requests for asynchronous processing.
     */
    public BatchResponse createBatchInline(String displayName, @Nullable Long priority, List<ChatRequest> requests) {
        var modelName = extractModelFromChatRequests(requests);
        return super.createBatchInline(displayName, priority, requests, modelName);
    }

    @Override
    protected ChatRequest prepareRequest(ChatRequest request) {
        return ChatRequest.builder()
                .messages(request.messages())
                .parameters(chatModel.defaultRequestParameters.overrideWith(request.parameters()))
                .build();
    }

    @Override
    protected Object createInlinedRequest(ChatRequest request) {
        return chatModel.createGenerateContentRequest(request);
    }

    @Override
    protected List<ChatResponse> extractResponses(BatchGenerateContentResponse.Response response) {
        if (response == null || response.inlinedResponses() == null) {
            return List.of();
        }

        return response.inlinedResponses().inlinedResponses().stream()
                .map(BatchGenerateContentResponse.InlinedResponseWrapper::response)
                .map(chatModel::processResponse)
                .toList();
    }

    private static String extractModelFromChatRequests(List<ChatRequest> requests) {
        var modelNames = requests.stream()
                .map(ChatRequest::modelName)
                .collect(Collectors.toUnmodifiableSet());

        if (modelNames.size() != 1) {
            throw new IllegalArgumentException(
                    "Batch requests cannot contain ChatRequest objects with different models; "
                            + "all requests must use the same model.");
        }

        return modelNames.iterator().next();
    }

    static Builder builder() {
        return new Builder();
    }

    public static final class Builder extends BaseGeminiChatModel.GoogleAiGeminiChatModelBaseBuilder<Builder> {
        private Builder() {
        }

        public GoogleAiGeminiBatchChatModel build() {
            return new GoogleAiGeminiBatchChatModel(this);
        }
    }
}
