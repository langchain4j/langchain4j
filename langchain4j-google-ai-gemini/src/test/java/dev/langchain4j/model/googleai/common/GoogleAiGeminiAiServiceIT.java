package dev.langchain4j.model.googleai.common;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.common.AbstractAiServiceIT;

import java.util.List;

import static dev.langchain4j.model.googleai.common.GoogleAiGeminiChatModelIT.GOOGLE_AI_GEMINI_CHAT_MODEL;

class GoogleAiGeminiAiServiceIT extends AbstractAiServiceIT {
// TODO add streaming counterpart

    @Override
    protected List<ChatLanguageModel> models() {
        return List.of(GOOGLE_AI_GEMINI_CHAT_MODEL);
    }

    @Override
    protected boolean supportsToolsAndJsonResponseFormatWithSchema() {
        return false; // TODO fix
    }
}
