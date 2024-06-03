package dev.langchain4j.model.anthropic;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.TestStreamingResponseHandler;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Duration;
import java.util.Base64;

import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.internal.Utils.readBytes;
import static dev.langchain4j.model.anthropic.AnthropicChatModelIT.CAT_IMAGE_URL;
import static dev.langchain4j.model.anthropic.AnthropicChatModelName.CLAUDE_3_SONNET_20240229;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static java.lang.System.getenv;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnthropicStreamingChatModelIT {

    StreamingChatLanguageModel model = AnthropicStreamingChatModel.builder()
            .apiKey(getenv("ANTHROPIC_API_KEY"))
            .maxTokens(20)
            .logRequests(true)
            .logResponses(true)
            .build();

    @Test
    void should_stream_answer_and_return_token_usage_and_finish_reason_stop() {

        // given
        String userMessage = "What is the capital of Germany?";

        // when
        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
        model.generate(userMessage, handler);
        Response<AiMessage> response = handler.get();

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
        StreamingChatLanguageModel visionModel = AnthropicStreamingChatModel.builder()
                .apiKey(getenv("ANTHROPIC_API_KEY"))
                .maxTokens(20)
                .logRequests(false) // base64-encoded images are huge
                .logResponses(true)
                .build();

        String base64Data = Base64.getEncoder().encodeToString(readBytes(CAT_IMAGE_URL));
        ImageContent imageContent = ImageContent.from(base64Data, "image/png");
        UserMessage userMessage = UserMessage.from(imageContent);

        // when
        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
        visionModel.generate(userMessage, handler);
        Response<AiMessage> response = handler.get();

        // then
        assertThat(response.content().text()).containsIgnoringCase("cat");
    }

    @ParameterizedTest
    @EnumSource(AnthropicChatModelName.class)
    void should_support_all_enum_model_names(AnthropicChatModelName modelName) {

        // given
        StreamingChatLanguageModel model = AnthropicStreamingChatModel.builder()
                .apiKey(getenv("ANTHROPIC_API_KEY"))
                .modelName(modelName)
                .maxTokens(1)
                .logRequests(true)
                .logResponses(true)
                .build();

        String userMessage = "Hi";

        // when
        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
        model.generate(userMessage, handler);
        Response<AiMessage> response = handler.get();

        // then
        assertThat(response.content().text()).isNotBlank();
    }

    @Test
    void test_all_parameters() {

        // given
        StreamingChatLanguageModel model = AnthropicStreamingChatModel.builder()
                .baseUrl("https://api.anthropic.com/v1/")
                .apiKey(System.getenv("ANTHROPIC_API_KEY"))
                .version("2023-06-01")
                .modelName(CLAUDE_3_SONNET_20240229)
                .temperature(1.0)
                .topP(1.0)
                .topK(1)
                .maxTokens(3)
                .stopSequences(asList("hello", "world"))
                .timeout(Duration.ofSeconds(30))
                .logRequests(true)
                .logResponses(true)
                .build();

        UserMessage userMessage = userMessage("Hi");

        // when
        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
        model.generate(userMessage, handler);
        Response<AiMessage> response = handler.get();

        // then
        assertThat(response.content().text()).isNotBlank();
    }

    @Test
    void should_fail_to_create_without_api_key() {

        assertThatThrownBy(() -> AnthropicStreamingChatModel.withApiKey(null))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("Anthropic API key must be defined. " +
                        "It can be generated here: https://console.anthropic.com/settings/keys");
    }
}