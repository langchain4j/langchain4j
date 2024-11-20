package dev.langchain4j.model.vertexai.common;

import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.service.common.AbstractStreamingAiServiceIT;

import java.util.List;

import static dev.langchain4j.model.vertexai.common.VertexAiGeminiStreamingChatModelIT.VERTEX_AI_GEMINI_STREAMING_CHAT_MODEL;

class VertexAiGeminiStreamingAiServiceIT extends AbstractStreamingAiServiceIT {

    @Override
    protected List<StreamingChatLanguageModel> models() {
        return List.of(VERTEX_AI_GEMINI_STREAMING_CHAT_MODEL);
    }
}
