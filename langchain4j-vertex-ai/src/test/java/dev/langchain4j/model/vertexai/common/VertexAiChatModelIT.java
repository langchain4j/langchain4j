package dev.langchain4j.model.vertexai.common;

import static dev.langchain4j.model.chat.common.ChatModelCapabilities.SupportStatus.NOT_SUPPORTED;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.common.AbstractChatModelIT;
import dev.langchain4j.model.chat.common.ChatLanguageModelCapabilities;
import dev.langchain4j.model.chat.common.ChatModelCapabilities;
import dev.langchain4j.model.vertexai.VertexAiChatModel;
import java.util.List;

class VertexAiChatModelIT extends AbstractChatModelIT {

    // TODO https://github.com/langchain4j/langchain4j/issues/2219

    @Override
    protected List<ChatModelCapabilities<ChatLanguageModel>> models() {
        return List.of(ChatLanguageModelCapabilities.builder()
                .model(VertexAiChatModel.builder()
                        .endpoint(System.getenv("GCP_VERTEXAI_ENDPOINT"))
                        .project(System.getenv("GCP_PROJECT_ID"))
                        .location(System.getenv("GCP_LOCATION"))
                        .publisher("google")
                        .modelName("chat-bison@001")
                        .build())
                .mnemonicName("vertex ai chat model")
                .supportsSingleImageInputAsPublicURL(NOT_SUPPORTED)
                .supportsSingleImageInputAsBase64EncodedString(NOT_SUPPORTED)
                .supportsMaxOutputTokensParameter(NOT_SUPPORTED)
                .supportsModelNameParameter(NOT_SUPPORTED)
                .supportsStopSequencesParameter(NOT_SUPPORTED)
                .supportsToolChoiceRequired(NOT_SUPPORTED)
                .supportsCommonParametersWrappedInIntegrationSpecificClass(NOT_SUPPORTED)
                .supportsToolsAndJsonResponseFormatWithSchema(NOT_SUPPORTED)
                .assertExceptionType(false)
                .assertResponseId(false)
                .assertFinishReason(false)
                .assertResponseModel(false)
                .build());
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
    protected boolean assertResponseId() {
        return false; // TODO implement
    }

    @Override
    protected boolean assertResponseModel() {
        return false; // TODO implement
    }

    @Override
    protected boolean assertFinishReason() {
        return false; // TODO implement
    }

    @Override
    protected boolean assertExceptionType() {
        return false; // TODO fix
    }
}
