package dev.langchain4j.model.googleai;

import static org.assertj.core.api.Assertions.assertThatCharSequence;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class GoogleAiGeminiStreamingChatModelTest {
    private static final ChatRequest DEFAULT_REQUEST =
            ChatRequest.builder().messages(new UserMessage("Hi")).build();

    @Test
    void should_fail_when_api_key_is_null() {
        // when/then
        assertThatThrownBy(() -> GoogleAiGeminiStreamingChatModel.builder()
                        .apiKey(null)
                        .modelName("ModelName")
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("apiKey");
    }

    @Test
    void should_fail_when_empty_messages_provided() {
        // when/then
        assertThatThrownBy(() -> ChatRequest.builder().messages().build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("messages cannot be null or empty");
    }

    @Nested
    class GoogleAiGeminiStreamingChatModelBuilder {

        @Test
        void seedParameterInContentRequest() {
            GoogleAiGeminiStreamingChatModel chatModel = GoogleAiGeminiStreamingChatModel.builder()
                    .apiKey("ApiKey")
                    .modelName("ModelName")
                    .seed(42)
                    .build();
            GeminiGenerateContentRequest result = chatModel.createGenerateContentRequest(DEFAULT_REQUEST);

            assertThatCharSequence(Json.toJson(result.getGenerationConfig())).contains("\"seed\" : 42");
        }

        @Test
        void defaultSeedInContentRequest() {
            GoogleAiGeminiStreamingChatModel chatModel = GoogleAiGeminiStreamingChatModel.builder()
                    .apiKey("ApiKey")
                    .modelName("ModelName")
                    .build();
            GeminiGenerateContentRequest result = chatModel.createGenerateContentRequest(DEFAULT_REQUEST);

            assertThatCharSequence(Json.toJson(result.getGenerationConfig())).doesNotContain("\"seed\"");
        }
    }
}
