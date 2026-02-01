package dev.langchain4j.model.googleai.common;

import static dev.langchain4j.model.googleai.common.GoogleAiGeminiChatModelIT.GOOGLE_AI_GEMINI_CHAT_MODEL;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.common.AbstractAiServiceIT;
import java.util.List;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "GOOGLE_AI_GEMINI_API_KEY", matches = ".+")
class GoogleAiGeminiAiServiceIT extends AbstractAiServiceIT {

    @Override
    protected List<ChatModel> models() {
        return List.of(GOOGLE_AI_GEMINI_CHAT_MODEL);
    }

    @Override
    protected boolean supportsToolsAndJsonResponseFormatWithSchema() {
        return false; // Gemini does not support tools and responses format simultaneously
    }
}
