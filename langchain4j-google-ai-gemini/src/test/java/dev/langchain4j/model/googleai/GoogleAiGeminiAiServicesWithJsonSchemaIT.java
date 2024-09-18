package dev.langchain4j.model.googleai;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServicesWithJsonSchemaIT;
import org.junit.jupiter.api.AfterEach;

import java.util.List;

import static dev.langchain4j.model.chat.request.ResponseFormat.JSON;
import static java.util.Collections.singletonList;

class GoogleAiGeminiAiServicesWithJsonSchemaIT extends AiServicesWithJsonSchemaIT {

    @AfterEach
    void afterEach() throws InterruptedException {
        Thread.sleep(2_000); // to prevent hitting rate limits
    }

    @Override
    protected List<ChatLanguageModel> models() {
        return singletonList(
                GoogleAiGeminiChatModel.builder()
                        .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
                        .modelName("gemini-1.5-flash")
                        .responseFormat(JSON)
                        .temperature(0.0)
                        .logRequestsAndResponses(true)
                        .build()
        );
    }
}
