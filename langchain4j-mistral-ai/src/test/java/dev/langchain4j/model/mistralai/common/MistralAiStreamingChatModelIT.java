package dev.langchain4j.model.mistralai.common;

import static dev.langchain4j.model.chat.common.AbstractChatModelAndCapabilities.SupportStatus.NOT_SUPPORTED;
import static dev.langchain4j.model.mistralai.MistralAiChatModelName.OPEN_MIXTRAL_8X22B;

import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.common.AbstractChatModelAndCapabilities;
import dev.langchain4j.model.chat.common.AbstractStreamingChatModelIT;
import dev.langchain4j.model.chat.common.StreamingChatModelAndCapabilities;
import dev.langchain4j.model.mistralai.MistralAiStreamingChatModel;
import java.util.List;

class MistralAiStreamingChatModelIT extends AbstractStreamingChatModelIT {

    static final StreamingChatLanguageModel MISTRAL_STREAMING_CHAT_MODEL = MistralAiStreamingChatModel.builder()
            .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
            .modelName(OPEN_MIXTRAL_8X22B)
            .temperature(0.0)
            .logRequests(true)
            .logResponses(true)
            .build();

    @Override
    protected List<AbstractChatModelAndCapabilities<StreamingChatLanguageModel>> models() {
        return List.of(StreamingChatModelAndCapabilities.builder()
                .model(MISTRAL_STREAMING_CHAT_MODEL)
                .mnemonicName("mixtral_8x22b")
                .supportsSingleImageInputAsPublicURL(NOT_SUPPORTED) // TODO implement
                .supportsToolChoiceRequired(NOT_SUPPORTED) // TODO implement
                .supportsStopSequencesParameter(NOT_SUPPORTED) // TODO implement
                .supportsModelNameParameter(NOT_SUPPORTED) // TODO implement
                .supportsMaxOutputTokensParameter(NOT_SUPPORTED) // TODO implement
                .supportsSingleImageInputAsBase64EncodedString(NOT_SUPPORTED) // TODO implement
                .supportsJsonResponseFormat(NOT_SUPPORTED) // TODO implement
                .supportsJsonResponseFormatWithSchema(NOT_SUPPORTED) // TODO implement
                .supportsCommonParametersWrappedInIntegrationSpecificClass(NOT_SUPPORTED)
                .assertExceptionType(false)
                .assertResponseId(false) // TODO implement
                .assertFinishReason(false) // TODO implement
                .assertResponseModel(false) // TODO implement
                .assertThreads(false)
                .build());
        // TODO add more model configs, see OpenAiChatModelIT
    }

    @Override
    protected boolean disableParametersInDefaultModelTests() {
        return true; // TODO implement
    }
}
