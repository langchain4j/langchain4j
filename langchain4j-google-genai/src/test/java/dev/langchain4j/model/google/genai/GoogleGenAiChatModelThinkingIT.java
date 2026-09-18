package dev.langchain4j.model.google.genai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "GOOGLE_AI_GEMINI_API_KEY", matches = ".+")
class GoogleGenAiChatModelThinkingIT {

    private static final String GOOGLE_AI_GEMINI_API_KEY = System.getenv("GOOGLE_AI_GEMINI_API_KEY");
    private static final String MODEL_NAME = "gemini-2.5-flash";

    // gemini-2.5-flash does not return a thought signature on parts that carry no function call
    private static final String THOUGHT_SIGNATURE_MODEL_NAME = "gemini-3.1-pro-preview";

    private static final UserMessage QUESTION = UserMessage.from("What are the best tourist spots in San Francisco?");

    private static final UserMessage MULTIPLICATION = UserMessage.from("What is 17 times 24? Think it through.");

    private static GoogleGenAiChatModel.Builder modelBuilder() {
        return GoogleGenAiChatModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName(MODEL_NAME)
                .thinkingBudget(1024)
                .includeThoughts(true);
    }

    private static GoogleGenAiChatModel.Builder thoughtSignatureModelBuilder() {
        return GoogleGenAiChatModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName(THOUGHT_SIGNATURE_MODEL_NAME)
                .includeThoughts(true)
                .returnThinking(true);
    }

    @Test
    void should_return_thinking() {
        ChatResponse chatResponse = modelBuilder().returnThinking(true).build().chat(QUESTION);

        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.text()).containsIgnoringCase("Golden Gate");
        assertThat(aiMessage.thinking()).isNotBlank();
        assertThat(aiMessage.text()).doesNotContain(aiMessage.thinking());
    }

    @Test
    void should_not_return_thinking() {
        ChatResponse chatResponse = modelBuilder().returnThinking(false).build().chat(QUESTION);

        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.text()).containsIgnoringCase("Golden Gate");
        assertThat(aiMessage.thinking()).isNull();
    }

    @Test
    void should_not_return_thinking_when_return_thinking_is_not_set() {
        ChatResponse chatResponse = modelBuilder().build().chat(QUESTION);

        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.text()).containsIgnoringCase("Golden Gate");
        assertThat(aiMessage.thinking()).isNull();
    }

    @Test
    void should_send_thinking_in_a_follow_up_request() {
        AiMessage answer = AiMessage.builder()
                .text("Start with the Golden Gate Bridge.")
                .thinking("The user wants sightseeing recommendations for San Francisco. The landmark most visitors "
                        + "start with is the Golden Gate Bridge, followed by Alcatraz Island and Fisherman's Wharf.")
                .build();
        UserMessage followUp = UserMessage.from("Which of those is best at sunset?");

        ChatResponse sent = modelBuilder().sendThinking(true).build().chat(QUESTION, answer, followUp);
        ChatResponse notSent = modelBuilder().sendThinking(false).build().chat(QUESTION, answer, followUp);

        assertThat(sent.aiMessage().text()).isNotBlank();
        assertThat(sent.tokenUsage().inputTokenCount())
                .isGreaterThan(notSent.tokenUsage().inputTokenCount());
    }

    @Test
    void should_return_thought_signature() {
        ChatResponse chatResponse = thoughtSignatureModelBuilder().build().chat(MULTIPLICATION);

        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.text()).contains("408");
        assertThat(aiMessage.attribute("thought_signature", String.class)).isNotBlank();
    }

    @Test
    void should_send_thought_signature_in_a_follow_up_request() {
        GoogleGenAiChatModel model =
                thoughtSignatureModelBuilder().sendThinking(true).build();

        AiMessage aiMessage = model.chat(MULTIPLICATION).aiMessage();
        assertThat(aiMessage.attribute("thought_signature", String.class)).isNotBlank();

        ChatResponse followUp = model.chat(MULTIPLICATION, aiMessage, UserMessage.from("Now multiply that by 2."));

        // this asserts that Gemini accepts a signature sent back on a text part, which is the part
        // of the contract no unit test can cover; that it is attached at all is covered by
        // GoogleGenAiContentMapperTest, and this test still passes if the mapper stops attaching it
        assertThat(followUp.aiMessage().text()).contains("816");
    }
}
