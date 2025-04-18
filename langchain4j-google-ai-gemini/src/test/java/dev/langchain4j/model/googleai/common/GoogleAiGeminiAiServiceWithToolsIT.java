package dev.langchain4j.model.googleai.common;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.service.common.AbstractAiServiceWithToolsIT;

import java.util.Collections;
import java.util.List;

class GoogleAiGeminiAiServiceWithToolsIT extends AbstractAiServiceWithToolsIT {

    @Override
    protected List<ChatModel> models() {
        return Collections.singletonList(
                GoogleAiGeminiChatModel.builder()
                        .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
                        .modelName("gemini-1.5-flash")
                        .logRequestsAndResponses(true)
                        .temperature(0.0)
                        .build()
        );
    }

    @Override
    protected boolean supportsMapParameters() {
        return false;
    }
}
