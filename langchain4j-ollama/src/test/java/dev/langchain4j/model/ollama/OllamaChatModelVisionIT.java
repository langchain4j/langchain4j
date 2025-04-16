package dev.langchain4j.model.ollama;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static dev.langchain4j.model.ollama.AbstractOllamaLanguageModelInfrastructure.ollamaBaseUrl;
import static dev.langchain4j.model.ollama.OllamaImage.BAKLLAVA_MODEL;
import static org.assertj.core.api.Assertions.assertThat;

class OllamaChatModelVisionIT extends AbstractOllamaVisionModelInfrastructure {

    static final String CAT_IMAGE_URL = "https://upload.wikimedia.org/wikipedia/commons/e/e9/Felis_silvestris_silvestris_small_gradual_decrease_of_quality.png";

    @Test
    void should_see_cat() {

        // given
        ChatModel model = OllamaChatModel.builder()
                .baseUrl(ollamaBaseUrl(ollama))
                .timeout(Duration.ofMinutes(3))
                .modelName(BAKLLAVA_MODEL)
                .temperature(0.0)
                .build();

        // when
        ChatResponse response = model.chat(UserMessage.userMessage(
                TextContent.from("What animal is on this picture?"),
                ImageContent.from(CAT_IMAGE_URL)
        ));

        // then
        assertThat(response.aiMessage().text())
                .containsIgnoringCase("cat");
    }
}
