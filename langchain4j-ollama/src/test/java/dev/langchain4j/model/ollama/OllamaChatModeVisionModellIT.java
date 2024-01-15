package dev.langchain4j.model.ollama;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class OllamaChatModeVisionModellIT extends AbstractOllamaInfrastructureVisionModel {
    static final String CAT_IMAGE_URL = "https://upload.wikimedia.org/wikipedia/commons/e/e9/Felis_silvestris_silvestris_small_gradual_decrease_of_quality.png";
    static final String DICE_IMAGE_URL = "https://upload.wikimedia.org/wikipedia/commons/4/47/PNG_transparency_demonstration_1.png";

    @Test
    void should_see_cat() {

        // given
        ChatLanguageModel model = OllamaChatModel.builder()
                .baseUrl(getBaseUrl())
                .timeout(Duration.ofMinutes(3))
                .modelName(MODEL)
                .temperature(0.0)
                .build();

        // when
        Response<AiMessage> response = model.generate(UserMessage.userMessage(
                TextContent.from("What is on the picture?"),
                ImageContent.from(CAT_IMAGE_URL)
        ));

        // then
        assertThat(response.content().text())
                .containsIgnoringCase("cat");
    }

    @Test
    void should_see_dice() {

        // given
        ChatLanguageModel model = OllamaChatModel.builder()
                .baseUrl(getBaseUrl())
                .timeout(Duration.ofMinutes(3))
                .modelName(MODEL)
                .temperature(0.0)
                .build();

        // when
        Response<AiMessage> response = model.generate(UserMessage.userMessage(
                TextContent.from("What is on the picture?"),
                ImageContent.from(DICE_IMAGE_URL)
        ));

        // then
        assertThat(response.content().text())
                .containsIgnoringCase("dice");
    }

}