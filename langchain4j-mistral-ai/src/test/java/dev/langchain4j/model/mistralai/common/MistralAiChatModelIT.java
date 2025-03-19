package dev.langchain4j.model.mistralai.common;

import static dev.langchain4j.model.chat.common.AbstractChatModelAndCapabilities.SupportStatus.NOT_SUPPORTED;
import static dev.langchain4j.model.mistralai.MistralAiChatModelName.OPEN_MIXTRAL_8X22B;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.common.AbstractChatModelAndCapabilities;
import dev.langchain4j.model.chat.common.AbstractChatModelIT;
import dev.langchain4j.model.chat.common.ChatModelAndCapabilities;
import dev.langchain4j.model.mistralai.MistralAiChatModel;
import java.util.List;

class MistralAiChatModelIT extends AbstractChatModelIT {

    static final ChatLanguageModel MISTRAL_CHAT_MODEL = MistralAiChatModel.builder()
            .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
            .modelName(OPEN_MIXTRAL_8X22B)
            .temperature(0.0)
            .logRequests(true)
            .logResponses(true)
            .build();

    @Override
    protected List<AbstractChatModelAndCapabilities<ChatLanguageModel>> models() {
        return List.of(ChatModelAndCapabilities.builder()
                .model(MISTRAL_CHAT_MODEL)
                .mnemonicName("mixtral_8x22b")
                .supportsSingleImageInputAsPublicURL(NOT_SUPPORTED) // TODO check if supported
                .supportsToolChoiceRequired(NOT_SUPPORTED) // TODO implement
                .supportsStopSequencesParameter(NOT_SUPPORTED) // TODO implement
                .supportsModelNameParameter(NOT_SUPPORTED) // TODO implement
                .supportsMaxOutputTokensParameter(NOT_SUPPORTED) // TODO implement
                .supportsSingleImageInputAsBase64EncodedString(NOT_SUPPORTED) // TODO implement
                .supportsJsonResponseFormat(NOT_SUPPORTED) // TODO implement
                .supportsJsonResponseFormatWithSchema(NOT_SUPPORTED) // TODO implement
                .supportsCommonParametersWrappedInIntegrationSpecificClass(NOT_SUPPORTED)
                .assertExceptionType(false) // TODO fix
                .assertResponseId(false) // TODO implement
                .assertFinishReason(false) // TODO implement
                .assertResponseModel(false) // TODO implement
                .build());
    }

    @Override
    protected boolean disableParametersInDefaultModelTests() {
        return true; // TODO implement
    }
}
