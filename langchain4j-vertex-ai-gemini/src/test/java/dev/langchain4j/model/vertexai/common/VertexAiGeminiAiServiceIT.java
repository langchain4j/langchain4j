package dev.langchain4j.model.vertexai.common;

import static dev.langchain4j.model.vertexai.common.VertexAiGeminiChatModelIT.VERTEX_AI_GEMINI_CHAT_MODEL;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.common.AbstractAiServiceIT;
import java.util.List;
import org.junit.jupiter.api.AfterEach;

class VertexAiGeminiAiServiceIT extends AbstractAiServiceIT {

    @Override
    protected List<ChatLanguageModel> models() {
        return List.of(VERTEX_AI_GEMINI_CHAT_MODEL.model());
    }

    @Override
    protected boolean supportsJsonResponseFormatWithSchema() {
        return false; // TODO implement
    }

    @AfterEach
    void afterEach() throws InterruptedException {
        String ciDelaySeconds = System.getenv("CI_DELAY_SECONDS_VERTEX_AI_GEMINI");
        if (ciDelaySeconds != null) {
            Thread.sleep(Integer.parseInt(ciDelaySeconds) * 1000L);
        }
    }
}
