package dev.langchain4j.model.googleai.common;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.service.common.AbstractAiServiceWithToolsIT;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Collections;
import java.util.List;

class GoogleAiGeminiAiServiceWithToolsIT extends AbstractAiServiceWithToolsIT {

    @Override
    protected List<ChatModel> models() {
        return Collections.singletonList(
                GoogleAiGeminiChatModel.builder()
                        .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
                        .modelName("gemini-2.0-flash")
                        .temperature(0.0)
                        .logRequestsAndResponses(true)
                        .build()
        );
    }

    @Disabled("Gemini cannot do it properly")
    @Override
    @ParameterizedTest
    @MethodSource("models")
    protected void should_execute_tool_with_pojo_with_nested_pojo(ChatModel model) {
    }

    @Disabled("Gemini cannot do it properly")
    @Override
    @ParameterizedTest
    @MethodSource("models")
    protected void should_execute_tool_with_list_of_strings_parameter(ChatModel model) {
    }
}
