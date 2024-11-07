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
    protected boolean supportsTools() {
        return false;
    }

    @Override
    protected boolean supportsToolChoice() {
        return false;
    }

    @Override
    protected boolean supportsJsonResponseFormat() {
        return false;
    }

    @Override
    protected boolean supportsJsonResponseFormatWithSchema() {
        return false;
    }

    @Override
    protected boolean assertFinishReason() {
        return false;
    }
}
