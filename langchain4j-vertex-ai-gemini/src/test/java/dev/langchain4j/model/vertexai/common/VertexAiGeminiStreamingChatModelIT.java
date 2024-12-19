package dev.langchain4j.model.vertexai.common;

import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.common.AbstractStreamingChatModelIT;
import dev.langchain4j.model.vertexai.VertexAiGeminiStreamingChatModel;

import java.util.List;

class VertexAiGeminiStreamingChatModelIT extends AbstractStreamingChatModelIT {

    static final VertexAiGeminiStreamingChatModel VERTEX_AI_GEMINI_STREAMING_CHAT_MODEL =
            VertexAiGeminiStreamingChatModel.builder()
                    .project(System.getenv("GCP_PROJECT_ID"))
                    .location(System.getenv("GCP_LOCATION"))
                    .modelName("gemini-1.5-flash")
                    .build();

    @Override
    protected List<StreamingChatLanguageModel> models() {
        return List.of(
                VERTEX_AI_GEMINI_STREAMING_CHAT_MODEL
                // TODO add more model configs, see OpenAiChatModelIT
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
    protected boolean supportsToolChoiceRequired() {
        return false; // TODO implement
    }

    @Override
    protected boolean supportsJsonResponseFormat() {
        return false; // TODO implement
    }

    @Override
    protected boolean supportsJsonResponseFormatWithSchema() {
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
    protected boolean assertThreads() {
        return false; // TODO what to do with it?
    }
}
