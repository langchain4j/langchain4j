package dev.langchain4j.model.vertexai.common;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.common.AbstractChatModelIT;
import dev.langchain4j.model.vertexai.VertexAiGeminiChatModel;

import java.util.List;

class VertexAiGeminiChatModelIT extends AbstractChatModelIT {

    static final VertexAiGeminiChatModel VERTEX_AI_GEMINI_CHAT_MODEL = VertexAiGeminiChatModel.builder()
            .project(System.getenv("GCP_PROJECT_ID"))
            .location(System.getenv("GCP_LOCATION"))
            .modelName("gemini-1.5-flash")
            .build();

    @Override
    protected List<ChatLanguageModel> models() {
        return List.of(VERTEX_AI_GEMINI_CHAT_MODEL);
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
    protected boolean supportsToolChoiceRequiredWithMultipleTools() {
        return false; // TODO implement
    }

    @Override
    protected boolean supportsToolChoiceRequiredWithSingleTool() {
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

    protected boolean assertFinishReason() {
        return false; // TODO fix
    }
}
