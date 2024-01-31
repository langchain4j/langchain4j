package dev.langchain4j.model.vertexai;

import com.google.cloud.vertexai.VertexAI;
import com.google.cloud.vertexai.api.GenerationConfig;
import com.google.cloud.vertexai.generativeai.preview.GenerativeModel;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Base64;

import static dev.langchain4j.internal.Utils.readBytes;
import static dev.langchain4j.model.output.FinishReason.LENGTH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VertexAiGeminiChatModelIT {

    static final String CAT_IMAGE_URL = "https://upload.wikimedia.org/wikipedia/commons/e/e9/Felis_silvestris_silvestris_small_gradual_decrease_of_quality.png";
    static final String DICE_IMAGE_URL = "https://upload.wikimedia.org/wikipedia/commons/4/47/PNG_transparency_demonstration_1.png";

    ChatLanguageModel model = VertexAiGeminiChatModel.builder()
            .project(System.getenv("GCP_PROJECT_ID"))
            .location(System.getenv("GCP_LOCATION"))
            .modelName("gemini-pro")
            .build();

    ChatLanguageModel visionModel = VertexAiGeminiChatModel.builder()
            .project(System.getenv("GCP_PROJECT_ID"))
            .location(System.getenv("GCP_LOCATION"))
            .modelName("gemini-pro-vision")
            .build();

    @Test
    void should_generate_response() {

        // given
        UserMessage userMessage = UserMessage.from("What is the capital of Germany?");

        // when
        Response<AiMessage> response = model.generate(userMessage);
        System.out.println(response);

        // then
        assertThat(response.content().text()).contains("Berlin");

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(7);
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(FinishReason.STOP);
    }

    @Test
    void should_deny_system_message() {

        // given
        SystemMessage systemMessage = SystemMessage.from("Be polite");
        UserMessage userMessage = UserMessage.from("Tell me a joke");

        // when-then
        assertThatThrownBy(() -> model.generate(systemMessage, userMessage))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("SystemMessage is currently not supported by Gemini");
    }

    @Test
    void should_respect_maxOutputTokens() {

        // given
        ChatLanguageModel model = VertexAiGeminiChatModel.builder()
                .project(System.getenv("GCP_PROJECT_ID"))
                .location(System.getenv("GCP_LOCATION"))
                .modelName("gemini-pro")
                .maxOutputTokens(1)
                .build();

        UserMessage userMessage = UserMessage.from("Tell me a joke");

        // when
        Response<AiMessage> response = model.generate(userMessage);
        System.out.println(response);

        // then
        assertThat(response.content().text()).isNotBlank();

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(4);
        assertThat(tokenUsage.outputTokenCount()).isEqualTo(1);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(LENGTH);
    }

    @Test
    void should_allow_custom_generativeModel_and_generationConfig() throws IOException {

        // given
        VertexAI vertexAi = new VertexAI(System.getenv("GCP_PROJECT_ID"), System.getenv("GCP_LOCATION"));
        GenerativeModel generativeModel = new GenerativeModel("gemini-pro", vertexAi);
        GenerationConfig generationConfig = GenerationConfig.getDefaultInstance();

        ChatLanguageModel model = new VertexAiGeminiChatModel(generativeModel, generationConfig);

        UserMessage userMessage = UserMessage.from("What is the capital of Germany?");

        // when
        Response<AiMessage> response = model.generate(userMessage);
        System.out.println(response);

        // then
        assertThat(response.content().text()).contains("Berlin");
    }

    @Test
    void should_accept_text_and_image_from_public_url() {

        // given
        UserMessage userMessage = UserMessage.from(
                ImageContent.from(CAT_IMAGE_URL),
                TextContent.from("What do you see? Reply in one word.")
        );

        // when
        Response<AiMessage> response = visionModel.generate(userMessage);

        // then
        assertThat(response.content().text()).containsIgnoringCase("cat");
    }

    @Test
    void should_accept_text_and_image_from_google_storage_url() {

        // given
        UserMessage userMessage = UserMessage.from(
                ImageContent.from("gs://langchain4j-test/cat.png"),
                TextContent.from("What do you see? Reply in one word.")
        );

        // when
        Response<AiMessage> response = visionModel.generate(userMessage);

        // then
        assertThat(response.content().text()).containsIgnoringCase("cat");
    }

    @Test
    void should_accept_text_and_base64_image() {

        // given
        String base64Data = Base64.getEncoder().encodeToString(readBytes(CAT_IMAGE_URL));
        UserMessage userMessage = UserMessage.from(
                ImageContent.from(base64Data, "image/png"),
                TextContent.from("What do you see? Reply in one word.")
        );

        // when
        Response<AiMessage> response = visionModel.generate(userMessage);

        // then
        assertThat(response.content().text()).containsIgnoringCase("cat");
    }

    @Test
    void should_accept_text_and_multiple_images_from_public_urls() {

        // given
        UserMessage userMessage = UserMessage.from(
                ImageContent.from(CAT_IMAGE_URL),
                ImageContent.from(DICE_IMAGE_URL),
                TextContent.from("What do you see? Reply with one word per image.")
        );

        // when
        Response<AiMessage> response = visionModel.generate(userMessage);

        // then
        assertThat(response.content().text())
                .containsIgnoringCase("cat")
                .containsIgnoringCase("dice");
    }

    @Test
    void should_accept_text_and_multiple_images_from_google_storage_urls() {

        // given
        UserMessage userMessage = UserMessage.from(
                ImageContent.from("gs://langchain4j-test/cat.png"),
                ImageContent.from("gs://langchain4j-test/dice.png"),
                TextContent.from("What do you see? Reply with one word per image.")
        );

        // when
        Response<AiMessage> response = visionModel.generate(userMessage);

        // then
        assertThat(response.content().text())
                .containsIgnoringCase("cat")
                .containsIgnoringCase("dice");
    }

    @Test
    void should_accept_text_and_multiple_base64_images() {

        // given
        String catBase64Data = Base64.getEncoder().encodeToString(readBytes(CAT_IMAGE_URL));
        String diceBase64Data = Base64.getEncoder().encodeToString(readBytes(DICE_IMAGE_URL));
        UserMessage userMessage = UserMessage.from(
                ImageContent.from(catBase64Data, "image/png"),
                ImageContent.from(diceBase64Data, "image/png"),
                TextContent.from("What do you see? Reply with one word per image.")
        );

        // when
        Response<AiMessage> response = visionModel.generate(userMessage);

        // then
        assertThat(response.content().text())
                .containsIgnoringCase("cat")
                .containsIgnoringCase("dice");
    }

    @Test
    void should_accept_text_and_multiple_images_from_different_sources() {

        // given
        UserMessage userMessage = UserMessage.from(
                ImageContent.from(CAT_IMAGE_URL),
                ImageContent.from("gs://langchain4j-test/dog.jpg"),
                ImageContent.from(Base64.getEncoder().encodeToString(readBytes(DICE_IMAGE_URL)), "image/png"),
                TextContent.from("What do you see? Reply with one word per image.")
        );

        // when
        Response<AiMessage> response = visionModel.generate(userMessage);

        // then
        assertThat(response.content().text())
                .containsIgnoringCase("cat")
                .containsIgnoringCase("dog")
                .containsIgnoringCase("dice");
    }
}