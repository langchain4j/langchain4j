package dev.langchain4j.model.google.genai;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.common.AbstractAiServiceWithJsonSchemaIT;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "GOOGLE_AI_GEMINI_API_KEY", matches = ".+")
class GoogleGenAiAiServiceWithJsonSchemaIT extends AbstractAiServiceWithJsonSchemaIT {

    @Override
    protected List<ChatModel> models() {
        return Collections.singletonList(GoogleGenAiChatModel.builder()
                .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
                .modelName("gemini-2.5-flash")
                .temperature(0.0)
                .build());
    }

    @Override
    protected void should_extract_list_of_polymorphic_types(ChatModel model) {
        // Disabled: GoogleGenAiToolMapper does not support JsonAnyOfSchema yet
    }

    @Override
    protected void should_extract_polymorphic_type(ChatModel model) {
        // Disabled: GoogleGenAiToolMapper does not support JsonAnyOfSchema yet
    }

    @Override
    protected void should_extract_pojo_with_nested_polymorphic_field(ChatModel model) {
        // Disabled: GoogleGenAiToolMapper does not support JsonAnyOfSchema yet
    }
}
