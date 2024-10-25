package dev.langchain4j.model.vertexai.common;

import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModelIT;
import dev.langchain4j.model.vertexai.VertexAiGeminiStreamingChatModel;

import java.util.List;

class VertexAiGeminiStreamingChatModelIT extends StreamingChatLanguageModelIT {

    @Override
    protected List<StreamingChatLanguageModel> models() {
        return List.of(
                VertexAiGeminiStreamingChatModel.builder()
                        .project(System.getenv("GCP_PROJECT_ID"))
                        .location(System.getenv("GCP_LOCATION"))
                        .modelName("gemini-1.5-flash")
                        .build()
        );
    }

    @Override
    protected boolean assertThreads() {
        return false; // TODO why?
    }
}
