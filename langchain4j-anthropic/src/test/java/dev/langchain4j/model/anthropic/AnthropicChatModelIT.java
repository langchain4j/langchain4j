package dev.langchain4j.model.anthropic;

import dev.langchain4j.data.message.*;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Duration;
import java.util.Base64;
import java.util.List;

import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.internal.Utils.readBytes;
import static dev.langchain4j.model.output.FinishReason.*;
import static java.lang.System.getenv;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnthropicChatModelIT {

    static final String CAT_IMAGE_URL = "https://upload.wikimedia.org/wikipedia/commons/e/e9/Felis_silvestris_silvestris_small_gradual_decrease_of_quality.png";
    static final String DICE_IMAGE_URL = "https://upload.wikimedia.org/wikipedia/commons/4/47/PNG_transparency_demonstration_1.png";

    ChatLanguageModel model = AnthropicChatModel.builder()
            .apiKey(System.getenv("ANTHROPIC_API_KEY"))
            .maxTokens(20)
            .logRequests(true)
            .logResponses(true)
            .build();

    ChatLanguageModel visionModel = AnthropicChatModel.builder()
            .apiKey(System.getenv("ANTHROPIC_API_KEY"))
            .maxTokens(20)
            .logRequests(false) // base64-encoded images are huge
            .logResponses(true)
            .build();

    @AfterEach
    void afterEach() throws InterruptedException {
        Thread.sleep(10_000L); // to avoid hitting rate limits
    }

    @Test
    void should_generate_answer_and_return_token_usage_and_finish_reason_stop() {

        // given
        ChatLanguageModel model = AnthropicChatModel.withApiKey(getenv("ANTHROPIC_API_KEY"));

        UserMessage userMessage = userMessage("What is the capital of Germany?");

        // when
        Response<AiMessage> response = model.generate(userMessage);
        System.out.println(response);

        // then
        assertThat(response.content().text()).contains("Berlin");

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(14);
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(1);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(STOP);
    }

    @Test
    void should_accept_base64_image() {

        // given
        String base64Data = Base64.getEncoder().encodeToString(readBytes(CAT_IMAGE_URL));
        ImageContent imageContent = ImageContent.from(base64Data, "image/png");
        UserMessage userMessage = UserMessage.from(imageContent);

        // when
        Response<AiMessage> response = visionModel.generate(userMessage);

        // then
        assertThat(response.content().text()).containsIgnoringCase("cat");
    }

    @Test
    void should_not_accept_image_url() {

        // given
        ImageContent imageAsURL = ImageContent.from(CAT_IMAGE_URL);

        UserMessage userMessage = UserMessage.from(imageAsURL);

        // when-then
        assertThatThrownBy(() -> visionModel.generate(userMessage))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("Anthropic does not support images as URLs, only as Base64-encoded strings");
    }

    @Test
    void should_accept_text_and_image() {

        // given
        String base64Data = Base64.getEncoder().encodeToString(readBytes(CAT_IMAGE_URL));

        UserMessage userMessage = UserMessage.from(
                TextContent.from("What do you see? Reply in one word."),
                ImageContent.from(base64Data, "image/png")
        );

        // when
        Response<AiMessage> response = visionModel.generate(userMessage);

        // then
        assertThat(response.content().text()).containsIgnoringCase("cat");
    }

    @Test
    void should_accept_text_and_multiple_images() {

        // given
        String catBase64Data = Base64.getEncoder().encodeToString(readBytes(CAT_IMAGE_URL));
        String diceBase64Data = Base64.getEncoder().encodeToString(readBytes(DICE_IMAGE_URL));

        UserMessage userMessage = UserMessage.from(
                TextContent.from("What do you see? Reply with one word per image."),
                ImageContent.from(catBase64Data, "image/png"),
                ImageContent.from(diceBase64Data, "image/png")
        );

        // when
        Response<AiMessage> response = visionModel.generate(userMessage);

        // then
        assertThat(response.content().text())
                .containsIgnoringCase("cat")
                .containsIgnoringCase("dice");
    }

    @Test
    void should_respect_maxTokens() {

        // given
        int maxTokens = 3;

        ChatLanguageModel model = AnthropicChatModel.builder()
                .apiKey(System.getenv("ANTHROPIC_API_KEY"))
                .maxTokens(maxTokens)
                .build();

        UserMessage userMessage = userMessage("What is the capital of Germany?");

        // when
        Response<AiMessage> response = model.generate(userMessage);
        System.out.println(response);

        // then
        assertThat(response.content().text()).isNotBlank();

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.outputTokenCount()).isEqualTo(maxTokens);

        assertThat(response.finishReason()).isEqualTo(LENGTH);
    }

    @Test
    void should_respect_system_message() {

        // given
        SystemMessage systemMessage = SystemMessage.from("You are a professional translator into German language");
        UserMessage userMessage = UserMessage.from("Translate: I love you");

        // when
        Response<AiMessage> response = model.generate(systemMessage, userMessage);
        System.out.println(response);

        // then
        assertThat(response.content().text()).containsIgnoringCase("liebe");
    }

    @Test
    void should_respect_stop_sequences() {

        // given
        List<String> stopSequences = singletonList("World");

        ChatLanguageModel model = AnthropicChatModel.builder()
                .apiKey(System.getenv("ANTHROPIC_API_KEY"))
                .stopSequences(stopSequences)
                .logRequests(true)
                .logResponses(true)
                .build();

        UserMessage userMessage = userMessage("Say 'Hello World'");

        // when
        Response<AiMessage> response = model.generate(userMessage);
        System.out.println(response);

        // then
        assertThat(response.content().text()).containsIgnoringCase("hello");
        assertThat(response.content().text()).doesNotContainIgnoringCase("world");

        assertThat(response.finishReason()).isEqualTo(OTHER);
    }

    @Test
    void test_all_parameters() {

        // given
        ChatLanguageModel model = AnthropicChatModel.builder()
                .baseUrl("https://api.anthropic.com/v1/")
                .apiKey(System.getenv("ANTHROPIC_API_KEY"))
                .version("2023-06-01")
                .modelName("claude-3-opus-20240229")
                .temperature(1.0)
                .topP(1.0)
                .topK(1)
                .maxTokens(3)
                .stopSequences(asList("hello", "world"))
                .timeout(Duration.ofSeconds(30))
                .maxRetries(1)
                .logRequests(true)
                .logResponses(true)
                .build();

        UserMessage userMessage = userMessage("Hi");

        // when
        Response<AiMessage> response = model.generate(userMessage);
        System.out.println(response);

        // then
        assertThat(response.content().text()).isNotBlank();
    }

    @ParameterizedTest
    @EnumSource(AnthropicChatModelName.class)
    void should_support_all_enum_model_names(AnthropicChatModelName modelName) {

        // given
        ChatLanguageModel model = AnthropicChatModel.builder()
                .apiKey(System.getenv("ANTHROPIC_API_KEY"))
                .modelName(modelName)
                .maxTokens(1)
                .logRequests(true)
                .logResponses(true)
                .build();

        UserMessage userMessage = userMessage("Hi");

        // when
        Response<AiMessage> response = model.generate(userMessage);
        System.out.println(response);

        // then
        assertThat(response.content().text()).isNotBlank();
    }

    @ParameterizedTest
    @EnumSource(AnthropicChatModelName.class)
    void should_support_all_string_model_names(AnthropicChatModelName modelName) {

        // given
        String modelNameString = modelName.toString();

        ChatLanguageModel model = AnthropicChatModel.builder()
                .apiKey(System.getenv("ANTHROPIC_API_KEY"))
                .modelName(modelNameString)
                .maxTokens(1)
                .logRequests(true)
                .logResponses(true)
                .build();

        UserMessage userMessage = userMessage("Hi");

        // when
        Response<AiMessage> response = model.generate(userMessage);
        System.out.println(response);

        // then
        assertThat(response.content().text()).isNotBlank();
    }

    @Test
    void should_fail_to_create_without_api_key() {

        assertThatThrownBy(() -> AnthropicChatModel.withApiKey(null))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("Anthropic API key must be defined. " +
                        "It can be generated here: https://console.anthropic.com/settings/keys");
    }

    @Test
    void should_fail_with_rate_limit_error() {

        ChatLanguageModel model = AnthropicChatModel.builder()
                .apiKey(System.getenv("ANTHROPIC_API_KEY"))
                .maxTokens(1)
                .logRequests(true)
                .logResponses(true)
                .build();

        assertThatThrownBy(() -> {
            for (int i = 0; i < 100; i++) {
                model.generate("Hi");
            }
        })
                .isExactlyInstanceOf(RuntimeException.class) // TODO return AnthropicHttpException (not wrapped)?
                .hasRootCauseExactlyInstanceOf(AnthropicHttpException.class)
                .hasMessageContaining("rate_limit_error");
    }
}