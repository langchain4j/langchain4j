package dev.langchain4j.model.vertexai.common;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.common.AbstractStreamingChatModelIT;
import dev.langchain4j.model.vertexai.VertexAiGeminiStreamingChatModel;
import org.junit.jupiter.api.AfterEach;

import java.util.List;

class VertexAiGeminiStreamingChatModelIT extends AbstractStreamingChatModelIT {

    static final VertexAiGeminiStreamingChatModel VERTEX_AI_GEMINI_STREAMING_CHAT_MODEL =
            VertexAiGeminiStreamingChatModel.builder()
                    .project(System.getenv("GCP_PROJECT_ID"))
                    .location(System.getenv("GCP_LOCATION"))
                    .modelName("gemini-1.5-flash")
                    .build();

    @Override
    protected List<StreamingChatModel> models() {
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

    @AfterEach
    void afterEach() throws InterruptedException {
        String ciDelaySeconds = System.getenv("CI_DELAY_SECONDS_VERTEX_AI_GEMINI");
        if (ciDelaySeconds != null) {
            Thread.sleep(Integer.parseInt(ciDelaySeconds) * 1000L);
        }
    }
}
