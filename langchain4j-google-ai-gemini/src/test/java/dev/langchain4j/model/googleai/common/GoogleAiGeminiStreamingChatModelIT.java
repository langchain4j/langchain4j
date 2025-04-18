package dev.langchain4j.model.googleai.common;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.common.AbstractStreamingChatModelIT;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.googleai.GoogleAiGeminiStreamingChatModel;

import java.util.List;

import static dev.langchain4j.internal.Utils.getOrDefault;

class GoogleAiGeminiStreamingChatModelIT extends AbstractStreamingChatModelIT {

    // TODO https://github.com/langchain4j/langchain4j/issues/2219
    // TODO https://github.com/langchain4j/langchain4j/issues/2220

    static final StreamingChatModel GOOGLE_AI_GEMINI_STREAMING_CHAT_MODEL = GoogleAiGeminiStreamingChatModel.builder()
            .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
            .modelName("gemini-1.5-flash-8b")
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
        return "gemini-1.5-flash";
    }

    @Override
    protected StreamingChatModel createModelWith(ChatRequestParameters parameters) {
        return GoogleAiGeminiStreamingChatModel.builder()
                .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))

                // TODO re-implement, support .defaultRequestParameters(ChatRequestParameters)
                .modelName(getOrDefault(parameters.modelName(), "gemini-1.5-flash-8b"))
                .temperature(parameters.temperature())
                .topP(parameters.topP())
                .topK(parameters.topK())
                .maxOutputTokens(parameters.maxOutputTokens())
                .stopSequences(parameters.stopSequences())
                .responseFormat(parameters.responseFormat())

                .build();
    }

    @Override
    protected ChatRequestParameters createIntegrationSpecificParameters(int maxOutputTokens) {
        return ChatRequestParameters.builder() // TODO return Gemini-specific params
                .maxOutputTokens(maxOutputTokens)
                .build();
    }

    @Override
    protected boolean supportsDefaultRequestParameters() {
        return false; // TODO implement
    }

    @Override
    protected boolean supportsModelNameParameter() {
        return false; // TODO implement
    }

    @Override
    protected boolean supportsMaxOutputTokensParameter() {
        return false; // TODO implement
    }

    @Override
    protected boolean supportsStopSequencesParameter() {
        return false; // TODO implement
    }

    @Override
    protected boolean supportsToolChoiceRequired() {
        return false; // TODO implement
    }

    @Override
    protected boolean supportsToolsAndJsonResponseFormatWithSchema() {
        return false; // TODO fix
    }

    @Override
    protected boolean supportsSingleImageInputAsPublicURL() {
        return false; // TODO check if supported
    }

    @Override
    protected boolean assertResponseId() {
        return false; // TODO implement
    }

    @Override
    protected boolean assertResponseModel() {
        return false; // TODO implement
    }

    protected boolean assertFinishReason() {
        return false; // TODO implement
    }

    @Override
    protected boolean assertThreads() {
        return false; // TODO fix
    }

    @Override
    protected boolean assertExceptionType() {
        return false; // TODO fix
    }
}
