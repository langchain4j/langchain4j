package dev.langchain4j.model.vertexai.common;

import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.common.AbstractStreamingChatModelIT;
import dev.langchain4j.model.vertexai.VertexAiGeminiStreamingChatModel;

import java.util.List;

class VertexAiGeminiStreamingChatModelIT extends AbstractStreamingChatModelIT {

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
    protected boolean supportsToolChoiceAnyWithMultipleTools() {
        return false; // TODO implement
    }

    @Override
    protected boolean supportsToolChoiceAnyWithSingleTool() {
        return false; // TODO implement
    }

    @Override
    protected boolean assertFinishReason() {
        return false; // TODO fix
    }

    @Override
    protected boolean assertThreads() {
        return false; // TODO fix
    }

    @Override
    protected boolean assertTimesOnPartialResponseIsCalled() {
        return false; // TODO fix
    }
}
