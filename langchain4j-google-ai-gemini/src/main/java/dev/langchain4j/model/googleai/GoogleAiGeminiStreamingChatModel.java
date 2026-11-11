package dev.langchain4j.model.googleai;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.model.ModelProvider.GOOGLE_AI_GEMINI;

import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.util.List;

public class GoogleAiGeminiStreamingChatModel extends BaseGeminiChatModel implements StreamingChatModel {

    private final int maxRetries;

    public GoogleAiGeminiStreamingChatModel(GoogleAiGeminiStreamingChatModelBuilder builder) {
        this(builder, buildGeminiService(builder));
    }

    GoogleAiGeminiStreamingChatModel(GoogleAiGeminiStreamingChatModelBuilder builder, GeminiService geminiService) {
        super(builder, geminiService);
        this.maxRetries = getOrDefault(builder.maxRetries, 0);
    }

    public static GoogleAiGeminiStreamingChatModelBuilder builder() {
        return new GoogleAiGeminiStreamingChatModelBuilder();
    }

    @Override
    public GoogleAiGeminiChatRequestParameters defaultRequestParameters() {
        return defaultRequestParameters;
    }

    @Override
    public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
        GeminiGenerateContentRequest geminiRequest = createGenerateContentRequest(request);
        geminiService.generateContentStream(
                request.modelName(), geminiRequest, includeCodeExecutionOutput, returnThinking, handler);
    }

    @Override
    public List<ChatModelListener> listeners() {
        return listeners;
    }

    @Override
    public int maxRetries() {
        return maxRetries;
    }

    @Override
    public ModelProvider provider() {
        return GOOGLE_AI_GEMINI;
    }

    public static final class GoogleAiGeminiStreamingChatModelBuilder
            extends GoogleAiGeminiChatModelBaseBuilder<GoogleAiGeminiStreamingChatModelBuilder> {

        private Integer maxRetries;

        private GoogleAiGeminiStreamingChatModelBuilder() {}

        /**
         * Sets the maximum number of times a failed request is retried, as long as nothing has been streamed
         * to the handler yet. Default: {@code 0} (no retries), unlike the non-streaming model, which retries twice.
         *
         * @param maxRetries the maximum number of retries
         * @return {@code this}
         * @see StreamingChatModel#maxRetries()
         */
        public GoogleAiGeminiStreamingChatModelBuilder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public GoogleAiGeminiStreamingChatModel build() {
            return new GoogleAiGeminiStreamingChatModel(this);
        }
    }
}
