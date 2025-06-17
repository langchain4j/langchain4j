package dev.langchain4j.model.googleai.common;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.common.AbstractStreamingChatModelIT;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.googleai.GoogleAiGeminiStreamingChatModel;

import java.util.List;

import static dev.langchain4j.internal.Utils.getOrDefault;

class GoogleAiGeminiStreamingChatModelIT extends AbstractStreamingChatModelIT {

    // TODO https://github.com/langchain4j/langchain4j/issues/2219
    // TODO https://github.com/langchain4j/langchain4j/issues/2220

    static final StreamingChatModel GOOGLE_AI_GEMINI_STREAMING_CHAT_MODEL = GoogleAiGeminiStreamingChatModel.builder()
            .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
            .modelName("gemini-2.0-flash-lite")
            .logRequestsAndResponses(false) // images are huge in logs
            .build();

    @Override
    protected List<StreamingChatModel> models() {
        return List.of(
                GOOGLE_AI_GEMINI_STREAMING_CHAT_MODEL
                // TODO add more model configs, see OpenAiChatModelIT
        );
    }

    @Override
    protected String customModelName() {
        return "gemini-2.0-flash";
    }

    @Override
    protected StreamingChatModel createModelWith(ChatRequestParameters parameters) {
        return GoogleAiGeminiStreamingChatModel.builder()
                .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
                .defaultRequestParameters(parameters)
                .modelName(getOrDefault(parameters.modelName(), "gemini-2.0-flash-lite"))
                .logRequestsAndResponses(true)
                .build();
    }

    @Override
    protected ChatRequestParameters createIntegrationSpecificParameters(int maxOutputTokens) {
        return ChatRequestParameters.builder()
                .maxOutputTokens(maxOutputTokens)
                .build();
    }

    @Override
    protected boolean supportsToolsAndJsonResponseFormatWithSchema() {
        return false; // Gemini does not support tools and response format simultaneously
    }

    @Override
    public StreamingChatModel createModelWith(ChatModelListener listener) {
        return GoogleAiGeminiStreamingChatModel.builder()
                .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
                .modelName("gemini-2.0-flash-lite")
                .logRequestsAndResponses(true)
                .listeners(List.of(listener))
                .build();
    }
}
