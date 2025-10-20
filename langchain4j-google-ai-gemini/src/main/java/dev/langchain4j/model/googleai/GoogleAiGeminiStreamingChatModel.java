package dev.langchain4j.model.googleai;

import static dev.langchain4j.model.ModelProvider.GOOGLE_AI_GEMINI;

import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.util.List;

public class GoogleAiGeminiStreamingChatModel extends BaseGeminiChatModel implements StreamingChatModel {

    public GoogleAiGeminiStreamingChatModel(GoogleAiGeminiStreamingChatModelBuilder builder) {
        super(
                builder.httpClientBuilder,
                builder.apiKey,
                builder.baseUrl,
                builder.modelName,
                builder.temperature,
                builder.topK,
                builder.seed,
                builder.topP,
                builder.frequencyPenalty,
                builder.presencePenalty,
                builder.maxOutputTokens,
                builder.logprobs,
                builder.timeout,
                builder.responseFormat,
                builder.stopSequences,
                builder.functionCallingConfig,
                builder.allowCodeExecution,
                builder.includeCodeExecutionOutput,
                builder.logRequestsAndResponses,
                builder.logRequests,
                builder.logResponses,
                builder.logger,
                builder.responseLogprobs,
                builder.enableEnhancedCivicAnswers,
                builder.safetySettings,
                builder.listeners,
                null,
                builder.thinkingConfig,
                builder.returnThinking,
                builder.sendThinking,
                builder.defaultRequestParameters);
    }

    public static GoogleAiGeminiStreamingChatModelBuilder builder() {
        return new GoogleAiGeminiStreamingChatModelBuilder();
    }

    @Override
    public ChatRequestParameters defaultRequestParameters() {
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
    public ModelProvider provider() {
        return GOOGLE_AI_GEMINI;
    }

    public static final class GoogleAiGeminiStreamingChatModelBuilder
            extends GoogleAiGeminiChatModelBaseBuilder<GoogleAiGeminiStreamingChatModelBuilder> {

        private GoogleAiGeminiStreamingChatModelBuilder() {}

        public GoogleAiGeminiStreamingChatModel build() {
            return new GoogleAiGeminiStreamingChatModel(this);
        }
    }
}
