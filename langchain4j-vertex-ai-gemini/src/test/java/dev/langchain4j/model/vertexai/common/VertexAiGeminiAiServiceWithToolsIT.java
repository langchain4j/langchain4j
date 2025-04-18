package dev.langchain4j.model.vertexai.common;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.vertexai.VertexAiGeminiChatModel;
import dev.langchain4j.service.common.AbstractAiServiceWithToolsIT;
import org.junit.jupiter.api.AfterEach;

import java.util.List;

import static java.util.Collections.singletonList;

class VertexAiGeminiAiServiceWithToolsIT extends AbstractAiServiceWithToolsIT {

    @Override
    protected List<ChatModel> models() {
        return singletonList(
                VertexAiGeminiChatModel.builder()
                        .project(System.getenv("GCP_PROJECT_ID"))
                        .location(System.getenv("GCP_LOCATION"))
                        .modelName("gemini-1.5-flash")
                        .temperature(0.0f)
                        .logRequests(true)
                        .logResponses(true)
                        .build()
        );
    }

    @AfterEach
    void afterEach() throws InterruptedException {
        String ciDelaySeconds = System.getenv("CI_DELAY_SECONDS_VERTEX_AI_GEMINI");
        if (ciDelaySeconds != null) {
            Thread.sleep(Integer.parseInt(ciDelaySeconds) * 1000L);
        }
    }
}
