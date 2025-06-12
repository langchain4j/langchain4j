package dev.langchain4j.model.ollama;

import static dev.langchain4j.model.ollama.AbstractOllamaLanguageModelInfrastructure.ollamaBaseUrl;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class OllamaChatModelVisionIT extends AbstractOllamaVisionModelInfrastructure {

    static final String CAT_IMAGE_URL =
            "https://upload.wikimedia.org/wikipedia/commons/e/e9/Felis_silvestris_silvestris_small_gradual_decrease_of_quality.png";

    @Test
    void should_see_cat() {

        // given
        ChatModel model = OllamaChatModel.builder()
                .baseUrl(ollamaBaseUrl(ollama))
                .timeout(Duration.ofMinutes(30))
                .modelName(MODEL_NAME)
                .temperature(0.0)
                .logRequests(false) // base64-encoded images are huge in logs
                .logResponses(true)
                .build();

        // when
        ChatResponse response = model.chat(UserMessage.userMessage(
                TextContent.from("What animal is on this picture?"), ImageContent.from(CAT_IMAGE_URL)));

        // then
        assertThat(response.aiMessage().text()).containsIgnoringCase("cat");
    }
}
