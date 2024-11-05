package dev.langchain4j.model.googleai;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServicesWithNewToolsIT;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

class GoogleAiGeminiAiServicesWithToolsIT extends AiServicesWithNewToolsIT {

    @Override
    protected List<ChatLanguageModel> models() {
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

    @Override
    protected boolean verifyModelInteractions() {
        return true;
    }

    @Test
    @Disabled("Does not support JSON element of type OBJECT without properties")
    void should_execute_tool_with_map_parameter() {
    }
}
