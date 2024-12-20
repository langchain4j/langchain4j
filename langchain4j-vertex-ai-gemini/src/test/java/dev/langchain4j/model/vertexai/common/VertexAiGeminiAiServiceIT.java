package dev.langchain4j.model.vertexai.common;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.common.AbstractAiServiceIT;

import java.util.List;

import static dev.langchain4j.model.vertexai.common.VertexAiGeminiChatModelIT.VERTEX_AI_GEMINI_CHAT_MODEL;

class VertexAiGeminiAiServiceIT extends AbstractAiServiceIT {

    @Override
    protected List<ChatLanguageModel> models() {
        return List.of(VERTEX_AI_GEMINI_CHAT_MODEL);
    }

    @Override
    protected boolean supportsJsonResponseFormatWithSchema() {
        return false; // TODO implement
    }
}
