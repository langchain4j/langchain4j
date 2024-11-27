package dev.langchain4j.model.vertexai.common;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.common.AbstractChatModelIT;
import dev.langchain4j.model.vertexai.VertexAiChatModel;

import java.util.List;

class VertexAiChatModelIT extends AbstractChatModelIT {

    @Override
    protected List<ChatLanguageModel> models() {
        return List.of(
                VertexAiChatModel.builder()
                        .endpoint(System.getenv("GCP_VERTEXAI_ENDPOINT"))
                        .project(System.getenv("GCP_PROJECT_ID"))
                        .location(System.getenv("GCP_LOCATION"))
                        .publisher("google")
                        .modelName("chat-bison@001")
                        .build()
        );
    }

    @Override
    protected boolean supportsModelNameParameter() {
        return false; // TODO implement
    }

    @Override
    protected boolean supportsTemperatureParameter() {
        return false; // TODO implement
    }

    @Override
    protected boolean supportsTopPParameter() {
        return false; // TODO implement
    }

    @Override
    protected boolean supportsTopKParameter() {
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
    protected boolean supportsTools() {
        return false; // TODO check if supported
    }

    @Override
    protected boolean supportsJsonResponseFormat() {
        return false; // TODO check if supported
    }

    @Override
    protected boolean supportsJsonResponseFormatWithSchema() {
        return false; // TODO check if supported
    }

    @Override
    protected boolean supportsSingleImageInputAsBase64EncodedString() {
        return false; // TODO check if supported
    }

    @Override
    protected boolean supportsSingleImageInputAsPublicURL() {
        return false; // TODO check if supported
    }

    @Override
    protected boolean assertFinishReason() {
        return false; // TODO fix
    }

    @Override
    protected boolean assertExceptionType() {
        return false; // TODO fix
    }
}
