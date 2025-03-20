package dev.langchain4j.model.vertexai.common;

import static dev.langchain4j.model.chat.common.AbstractChatModelAndCapabilities.SupportStatus.NOT_SUPPORTED;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.common.AbstractChatModelAndCapabilities;
import dev.langchain4j.model.chat.common.AbstractChatModelIT;
import dev.langchain4j.model.chat.common.ChatModelAndCapabilities;
import dev.langchain4j.model.vertexai.VertexAiChatModel;
import java.util.List;

class VertexAiChatModelIT extends AbstractChatModelIT {

    // TODO https://github.com/langchain4j/langchain4j/issues/2219

    @Override
    protected List<AbstractChatModelAndCapabilities<ChatLanguageModel>> models() {
        return List.of(ChatModelAndCapabilities.builder()
                .model(VertexAiChatModel.builder()
                        .endpoint(System.getenv("GCP_VERTEXAI_ENDPOINT"))
                        .project(System.getenv("GCP_PROJECT_ID"))
                        .location(System.getenv("GCP_LOCATION"))
                        .publisher("google")
                        .modelName("chat-bison@001")
                        .build())
                .mnemonicName("vertex ai chat model")
                .supportsSingleImageInputAsPublicURL(NOT_SUPPORTED) // TODO check if supported
                .supportsSingleImageInputAsBase64EncodedString(NOT_SUPPORTED) // TODO check if supported
                .supportsMaxOutputTokensParameter(NOT_SUPPORTED) // TODO implement
                .supportsModelNameParameter(NOT_SUPPORTED) // TODO implement
                .supportsStopSequencesParameter(NOT_SUPPORTED) // TODO implement
                .supportsToolChoiceRequired(NOT_SUPPORTED) // TODO check if supported
                .supportsCommonParametersWrappedInIntegrationSpecificClass(NOT_SUPPORTED)
                .supportsToolsAndJsonResponseFormatWithSchema(NOT_SUPPORTED) // TODO check if supported
                .assertExceptionType(false)
                .assertResponseId(false) // TODO implement
                .assertFinishReason(false)
                .assertResponseModel(false) // TODO implement
                .build());
    }

    @Override
    protected boolean disableParametersInDefaultModelTests() {
        return true; // TODO implement
    }
}
