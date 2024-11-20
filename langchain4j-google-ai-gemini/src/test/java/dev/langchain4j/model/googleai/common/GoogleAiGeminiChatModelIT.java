package dev.langchain4j.model.googleai.common;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.common.AbstractChatModelIT;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;

import java.util.List;

class GoogleAiGeminiChatModelIT extends AbstractChatModelIT {

    @Override
    protected List<ChatLanguageModel> models() {
        return List.of(
                GoogleAiGeminiChatModel.builder()
                        .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
                        .modelName("gemini-1.5-flash")
                        .build()
        );
    }

    @Override
    protected boolean supportsToolChoiceAnyWithMultipleTools() {
        return false; // TODO implement
    }

    protected boolean assertFinishReason() {
        return false; // TODO fix
    }
}
