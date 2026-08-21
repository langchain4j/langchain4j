package dev.langchain4j.model.google.genai;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.common.AbstractAiServiceWithToolsIT;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "GOOGLE_AI_GEMINI_API_KEY", matches = ".+")
class GoogleGenAiAiServiceWithToolsIT extends AbstractAiServiceWithToolsIT {

    @Override
    protected List<ChatModel> models() {
        return Collections.singletonList(GoogleGenAiChatModel.builder()
                .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
                .modelName("gemini-2.5-flash")
                .temperature(0.0)
                .build());
    }

    @Override
    protected void should_execute_tool_with_pojo_with_nested_pojo(ChatModel chatModel) {
        // Disabled: Google Gen AI model sometimes struggles with correctly forming nested POJO arguments
    }
}
