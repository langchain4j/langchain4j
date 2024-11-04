package dev.langchain4j.model.vertexai.common;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.common.AbstractChatLanguageModelIT;
import dev.langchain4j.model.vertexai.VertexAiGeminiChatModel;

import java.util.List;

class VertexAiGeminiChatModelIT extends AbstractChatLanguageModelIT {

    @Override
    protected List<ChatLanguageModel> models() {
        return List.of(
                VertexAiGeminiChatModel.builder()
                        .project(System.getenv("GCP_PROJECT_ID"))
                        .location(System.getenv("GCP_LOCATION"))
                        .modelName("gemini-1.5-flash")
                        .build()
        );
    }

    protected boolean assertFinishReason() {
        return false; // TODO fix
    }
}
