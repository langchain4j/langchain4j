package dev.langchain4j.model.google.genai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "GOOGLE_AI_GEMINI_API_KEY", matches = ".+")
class GoogleGenAiChatModelThoughtSignatureIT {

    private static final String GOOGLE_AI_GEMINI_API_KEY = System.getenv("GOOGLE_AI_GEMINI_API_KEY");
    private static final String MODEL_NAME = "gemini-3.1-pro-preview";

    @Test
    void should_keep_the_thought_signature_of_a_text_response() {
        ChatModel model = GoogleGenAiChatModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName(MODEL_NAME)
                .includeThoughts(true)
                .build();

        AiMessage aiMessage = model.chat(UserMessage.from("What is 17 times 24? Think it through."))
                .aiMessage();

        assertThat(aiMessage.text()).contains("408");
        assertThat(aiMessage.attribute("thought_signature", String.class)).isNotBlank();
    }

    @Test
    void should_send_the_thought_signature_back_on_the_text_part() {
        ChatModel model = GoogleGenAiChatModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName(MODEL_NAME)
                .includeThoughts(true)
                .build();

        UserMessage question = UserMessage.from("What is 17 times 24? Think it through.");
        AiMessage aiMessage = model.chat(question).aiMessage();
        assertThat(aiMessage.attribute("thought_signature", String.class)).isNotBlank();

        ChatResponse followUp = model.chat(question, aiMessage, UserMessage.from("Now multiply that by 2."));

        assertThat(followUp.aiMessage().text()).contains("816");
    }
}
