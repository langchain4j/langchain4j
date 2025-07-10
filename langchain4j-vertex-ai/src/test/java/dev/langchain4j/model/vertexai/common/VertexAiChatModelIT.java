package dev.langchain4j.model.vertexai.common;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.common.AbstractChatModelIT;
import dev.langchain4j.model.vertexai.VertexAiChatModel;
import org.junit.jupiter.api.Disabled;

import java.util.List;

@Disabled("TODO: configure custom model")
class VertexAiChatModelIT extends AbstractChatModelIT {

    // TODO https://github.com/langchain4j/langchain4j/issues/2219

    @Override
    protected List<ChatModel> models() {
        return List.of(
                VertexAiChatModel.builder()
                        .endpoint(System.getenv("GCP_VERTEXAI_ENDPOINT"))
                        .project(System.getenv("GCP_PROJECT_ID"))
                        .location(System.getenv("GCP_LOCATION"))
                        .publisher("google")
                        .modelName("llama-3.3-70b-instruct-maas")
                        .build()
        );
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
    protected boolean supportsTools() {
        return false; // TODO check if supported
    }

    @Override
    protected boolean supportsToolChoiceRequired() {
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
}
