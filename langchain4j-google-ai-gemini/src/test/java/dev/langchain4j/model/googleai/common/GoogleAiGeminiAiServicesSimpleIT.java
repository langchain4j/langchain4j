package dev.langchain4j.model.googleai.common;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.service.AiServicesSimpleIT;

import java.util.List;

class GoogleAiGeminiAiServicesSimpleIT extends AiServicesSimpleIT {

    @Override
    protected List<ChatLanguageModel> models() {
        return List.of(
                GoogleAiGeminiChatModel.builder()
                        .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
                        .modelName("gemini-1.5-flash")
                        .build()
        );
    }
}
