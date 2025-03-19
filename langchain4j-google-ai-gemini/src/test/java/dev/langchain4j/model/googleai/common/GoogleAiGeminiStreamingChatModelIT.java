package dev.langchain4j.model.googleai.common;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.model.chat.common.AbstractChatModelAndCapabilities.SupportStatus.NOT_SUPPORTED;

import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.common.AbstractChatModelAndCapabilities;
import dev.langchain4j.model.chat.common.AbstractStreamingChatModelIT;
import dev.langchain4j.model.chat.common.StreamingChatModelAndCapabilities;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.googleai.GoogleAiGeminiStreamingChatModel;
import java.util.List;

class GoogleAiGeminiStreamingChatModelIT extends AbstractStreamingChatModelIT {

    // TODO https://github.com/langchain4j/langchain4j/issues/2219
    // TODO https://github.com/langchain4j/langchain4j/issues/2220

    static final StreamingChatLanguageModel GOOGLE_AI_GEMINI_STREAMING_CHAT_MODEL =
            GoogleAiGeminiStreamingChatModel.builder()
                    .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
                    .modelName("gemini-1.5-flash-8b")
                    .build();

    @Override
    protected List<AbstractChatModelAndCapabilities<StreamingChatLanguageModel>> models() {
        return List.of(StreamingChatModelAndCapabilities.builder()
                .model(GOOGLE_AI_GEMINI_STREAMING_CHAT_MODEL)
                .mnemonicName("google ai gemini chat model")
                .supportsSingleImageInputAsPublicURL(NOT_SUPPORTED) // TODO check if supported
                .supportsToolChoiceRequired(NOT_SUPPORTED) // TODO implement
                .supportsToolsAndJsonResponseFormatWithSchema(NOT_SUPPORTED) // TODO fix
                .supportsStopSequencesParameter(NOT_SUPPORTED) // TODO implement
                .supportsModelNameParameter(NOT_SUPPORTED) // TODO implement
                .supportsMaxOutputTokensParameter(NOT_SUPPORTED) // TODO implement
                .supportsCommonParametersWrappedInIntegrationSpecificClass(NOT_SUPPORTED)
                .assertExceptionType(false) // TODO fix
                .assertResponseId(false) // TODO implement
                .assertFinishReason(false) // TODO implement
                .assertResponseModel(false) // TODO implement
                .assertThreads(false)
                .build());
        // TODO add more model configs, see OpenAiChatModelIT
    }

    @Override
    protected String customModelName() {
        return "gemini-1.5-flash";
    }

    @Override
    protected AbstractChatModelAndCapabilities<StreamingChatLanguageModel> createModelAndCapabilitiesWith(
            ChatRequestParameters parameters) {
        return StreamingChatModelAndCapabilities.builder()
                .model(GoogleAiGeminiStreamingChatModel.builder()
                        .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))

                        // TODO re-implement, support .defaultRequestParameters(ChatRequestParameters)
                        .modelName(getOrDefault(parameters.modelName(), "gemini-1.5-flash-8b"))
                        .temperature(parameters.temperature())
                        .topP(parameters.topP())
                        .topK(parameters.topK())
                        .maxOutputTokens(parameters.maxOutputTokens())
                        .stopSequences(parameters.stopSequences())
                        .responseFormat(parameters.responseFormat())
                        .build())
                .supportsToolChoiceRequired(NOT_SUPPORTED)
                .supportsStopSequencesParameter(NOT_SUPPORTED) // TODO implement
                .supportsModelNameParameter(NOT_SUPPORTED) // TODO implement
                .supportsMaxOutputTokensParameter(NOT_SUPPORTED) // TODO implement
                .assertResponseId(false)
                .assertResponseModel(false)
                .assertFinishReason(false)
                .assertExceptionType(false)
                .assertThreads(false)
                .build();
    }

    @Override
    protected ChatRequestParameters createIntegrationSpecificParameters(int maxOutputTokens) {
        return ChatRequestParameters.builder() // TODO return Gemini-specific params
                .maxOutputTokens(maxOutputTokens)
                .build();
    }
}
