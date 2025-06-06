package dev.langchain4j.model.googleai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormat;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class GoogleAiGeminiChatModelTest {

    @Nested
    class GoogleAiGeminiChatModelBuilder {

        @Test
        void seedParameterInContentRequest() {
            GoogleAiGeminiChatModel chatModel = GoogleAiGeminiChatModel.builder()
                    .apiKey("ApiKey")
                    .modelName("ModelName")
                    .seed(42)
                    .build();
            GeminiGenerateContentRequest result = chatModel.createGenerateContentRequest(
                    List.of(),
                    List.of(),
                    ResponseFormat.TEXT,
                    ChatRequestParameters.builder().build());

            assertThat(result.getGenerationConfig().getSeed()).isEqualTo(42);
        }

        @Test
        void defaultSeedInContentRequest() {
            GoogleAiGeminiChatModel chatModel = GoogleAiGeminiChatModel.builder()
                    .apiKey("ApiKey")
                    .modelName("ModelName")
                    .build();
            GeminiGenerateContentRequest result = chatModel.createGenerateContentRequest(
                    List.of(),
                    List.of(),
                    ResponseFormat.TEXT,
                    ChatRequestParameters.builder().build());

            assertThat(result.getGenerationConfig().getSeed()).isNull();
        }
    }
}
