package dev.langchain4j.model.anthropic;

import static dev.langchain4j.JsonTestUtils.jsonify;
import static dev.langchain4j.model.anthropic.AnthropicChatModelName.CLAUDE_3_7_SONNET_20250219;
import static dev.langchain4j.model.anthropic.AnthropicChatModelName.CLAUDE_OPUS_4_20250514;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.EnumSource.Mode.INCLUDE;

import java.util.List;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.SpyingHttpClient;
import dev.langchain4j.http.client.jdk.JdkHttpClient;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class AnthropicChatModelThinkingIT {

    private static final int THINKING_BUDGET_TOKENS = 1024;

    private final SpyingHttpClient spyingHttpClient = new SpyingHttpClient(JdkHttpClient.builder().build());

    @ParameterizedTest
    @EnumSource(value = AnthropicChatModelName.class, mode = INCLUDE, names = {
            "CLAUDE_OPUS_4_20250514",
            "CLAUDE_SONNET_4_20250514",
            "CLAUDE_3_7_SONNET_20250219"
    })
    void should_return_and_send_thinking(AnthropicChatModelName modelName) {

        // given
        boolean returnThinking = true;
        // sendThinking = true by default

        ChatModel model = AnthropicChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(spyingHttpClient))
                .apiKey(System.getenv("ANTHROPIC_API_KEY"))
                .modelName(modelName)

                .thinkingType("enabled")
                .thinkingBudgetTokens(THINKING_BUDGET_TOKENS)
                .maxTokens(THINKING_BUDGET_TOKENS + 100)
                .returnThinking(returnThinking)

                .logRequests(true)
                .logResponses(true)
                .build();

        UserMessage userMessage1 = UserMessage.from("What is the capital of Germany?");

        // when
        ChatResponse chatResponse1 = model.chat(userMessage1);

        // then
        AiMessage aiMessage1 = chatResponse1.aiMessage();
        assertThat(aiMessage1.text()).containsIgnoringCase("Berlin");
        assertThat(aiMessage1.thinking()).isNotBlank();
        String signature1 = aiMessage1.attribute("thinking_signature", String.class);
        assertThat(signature1).isNotBlank();

        // given
        UserMessage userMessage2 = UserMessage.from("What is the capital of France?");

        // when
        ChatResponse chatResponse2 = model.chat(userMessage1, aiMessage1, userMessage2);

        // then
        AiMessage aiMessage2 = chatResponse2.aiMessage();
        assertThat(aiMessage2.text()).containsIgnoringCase("Paris");
        assertThat(aiMessage2.thinking()).isNotBlank();
        assertThat(aiMessage2.attribute("thinking_signature", String.class)).isNotBlank();

        // should send thinking in the follow-up request
        List<HttpRequest> httpRequests = spyingHttpClient.requests();
        assertThat(httpRequests).hasSize(2);
        assertThat(httpRequests.get(1).body())
                .contains(jsonify(aiMessage1.text()))
                .contains(jsonify(aiMessage1.thinking()))
                .contains(jsonify(signature1));
    }

    @ParameterizedTest
    @EnumSource(value = AnthropicChatModelName.class, mode = INCLUDE, names = {
            "CLAUDE_OPUS_4_20250514",
            "CLAUDE_SONNET_4_20250514",
            "CLAUDE_3_7_SONNET_20250219"
    })
    void should_return_and_NOT_send_thinking(AnthropicChatModelName modelName) {

        // given
        boolean returnThinking = true;
        boolean sendThinking = false;

        ChatModel model = AnthropicChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(spyingHttpClient))
                .apiKey(System.getenv("ANTHROPIC_API_KEY"))
                .modelName(modelName)

                .thinkingType("enabled")
                .thinkingBudgetTokens(THINKING_BUDGET_TOKENS)
                .maxTokens(THINKING_BUDGET_TOKENS + 100)
                .returnThinking(returnThinking)
                .sendThinking(sendThinking)

                .logRequests(true)
                .logResponses(true)
                .build();

        UserMessage userMessage1 = UserMessage.from("What is the capital of Germany?");

        // when
        ChatResponse chatResponse1 = model.chat(userMessage1);

        // then
        AiMessage aiMessage1 = chatResponse1.aiMessage();
        assertThat(aiMessage1.text()).containsIgnoringCase("Berlin");
        assertThat(aiMessage1.thinking()).isNotBlank();
        String signature1 = aiMessage1.attribute("thinking_signature", String.class);
        assertThat(signature1).isNotBlank();

        // given
        UserMessage userMessage2 = UserMessage.from("What is the capital of France?");

        // when
        ChatResponse chatResponse2 = model.chat(userMessage1, aiMessage1, userMessage2);

        // then
        AiMessage aiMessage2 = chatResponse2.aiMessage();
        assertThat(aiMessage2.text()).containsIgnoringCase("Paris");
        assertThat(aiMessage2.thinking()).isNotBlank();
        assertThat(aiMessage2.attribute("thinking_signature", String.class)).isNotBlank();

        // should NOT send thinking in the follow-up request
        List<HttpRequest> httpRequests = spyingHttpClient.requests();
        assertThat(httpRequests).hasSize(2);
        assertThat(httpRequests.get(1).body())
                .contains(jsonify(aiMessage1.text()))
                .doesNotContain(jsonify(aiMessage1.thinking()))
                .doesNotContain(jsonify(signature1));
    }

    @ParameterizedTest
    @EnumSource(value = AnthropicChatModelName.class, mode = INCLUDE, names = {
            "CLAUDE_OPUS_4_20250514",
            "CLAUDE_SONNET_4_20250514",
            "CLAUDE_3_7_SONNET_20250219"
    })
    void should_return_and_send_thinking_with_tools(AnthropicChatModelName modelName) {

        // given
        boolean returnThinking = true;
        // sendThinking = true by default

        ToolSpecification toolSpecification = ToolSpecification.builder()
                .name("getWeather")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("city")
                        .required("city")
                        .build())
                .build();

        ChatModel model = AnthropicChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(spyingHttpClient))
                .apiKey(System.getenv("ANTHROPIC_API_KEY"))
                .modelName(modelName)

                .thinkingType("enabled")
                .thinkingBudgetTokens(THINKING_BUDGET_TOKENS)
                .maxTokens(THINKING_BUDGET_TOKENS + 100)
                .returnThinking(returnThinking)
                .toolSpecifications(toolSpecification)

                .logRequests(true)
                .logResponses(true)
                .build();

        UserMessage userMessage1 = UserMessage.from("What is the weather in Munich?");

        // when
        ChatResponse chatResponse1 = model.chat(userMessage1);

        // then
        AiMessage aiMessage1 = chatResponse1.aiMessage();
        String thinking1 = aiMessage1.thinking();
        assertThat(thinking1).isNotBlank();
        String signature1 = aiMessage1.attribute("thinking_signature", String.class);
        assertThat(signature1).isNotBlank();
        assertThat(aiMessage1.toolExecutionRequests()).hasSize(1);
        ToolExecutionRequest toolExecutionRequest1 = aiMessage1.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest1.name()).isEqualTo(toolSpecification.name());
        assertThat(toolExecutionRequest1.arguments()).contains("Munich");

        // given
        ToolExecutionResultMessage toolResultMessage1 = ToolExecutionResultMessage.from(toolExecutionRequest1, "sunny");

        // when
        ChatResponse chatResponse2 = model.chat(userMessage1, aiMessage1, toolResultMessage1);

        // then
        AiMessage aiMessage2 = chatResponse2.aiMessage();
        assertThat(aiMessage2.text()).containsIgnoringCase("sun");
        assertThat(aiMessage2.thinking()).isNull();
        assertThat(aiMessage2.attributes()).isEmpty();
        assertThat(aiMessage2.toolExecutionRequests()).isEmpty();

        // given
        UserMessage userMessage2 = UserMessage.from("What is the weather in Paris?");

        // when
        ChatResponse chatResponse3 = model.chat(userMessage1, aiMessage1, toolResultMessage1, aiMessage2, userMessage2);

        // then
        AiMessage aiMessage3 = chatResponse3.aiMessage();
        String thinking2 = aiMessage3.thinking();
        assertThat(thinking2).isNotBlank();
        String signature2 = aiMessage3.attribute("thinking_signature", String.class);
        assertThat(signature2).isNotBlank();
        assertThat(aiMessage3.toolExecutionRequests()).hasSize(1);
        ToolExecutionRequest toolExecutionRequest2 = aiMessage3.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest2.name()).isEqualTo(toolSpecification.name());
        assertThat(toolExecutionRequest2.arguments()).contains("Paris");

        // given
        ToolExecutionResultMessage toolResultMessage2 = ToolExecutionResultMessage.from(toolExecutionRequest2, "rainy");

        // when
        ChatResponse chatResponse4 = model.chat(userMessage1, aiMessage1, toolResultMessage1, aiMessage2, userMessage2, aiMessage3, toolResultMessage2);

        // then
        AiMessage aiMessage4 = chatResponse4.aiMessage();
        assertThat(aiMessage4.text()).containsIgnoringCase("rain");
        assertThat(aiMessage4.thinking()).isNull();
        assertThat(aiMessage4.attributes()).isEmpty();
        assertThat(aiMessage4.toolExecutionRequests()).isEmpty();

        // should send thinking in the follow-up requests
        List<HttpRequest> httpRequests = spyingHttpClient.requests();
        assertThat(httpRequests).hasSize(4);
        assertThat(httpRequests.get(1).body())
                .contains(jsonify(thinking1))
                .contains(jsonify(signature1));
        assertThat(httpRequests.get(3).body())
                .contains(jsonify(thinking2))
                .contains(jsonify(signature2));
    }

    @Test
    void test_interleaved_thinking() {

        // given
        String beta = "interleaved-thinking-2025-05-14";
        AnthropicChatModelName modelName = CLAUDE_OPUS_4_20250514;

        boolean returnThinking = true;
        // sendThinking = true by default

        ToolSpecification toolSpecification = ToolSpecification.builder()
                .name("getWeather")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("city")
                        .required("city")
                        .build())
                .build();

        ChatModel model = AnthropicChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(spyingHttpClient))
                .apiKey(System.getenv("ANTHROPIC_API_KEY"))

                .beta(beta)
                .modelName(modelName)
                .thinkingType("enabled")
                .thinkingBudgetTokens(THINKING_BUDGET_TOKENS)
                .maxTokens(THINKING_BUDGET_TOKENS + 100)
                .returnThinking(returnThinking)
                .toolSpecifications(toolSpecification)

                .logRequests(true)
                .logResponses(true)
                .build();

        UserMessage userMessage1 = UserMessage.from("What is the weather in Munich?");

        // when
        ChatResponse chatResponse1 = model.chat(userMessage1);

        // then
        AiMessage aiMessage1 = chatResponse1.aiMessage();

        String thinking1 = aiMessage1.thinking();
        assertThat(thinking1).isNotBlank();

        String signature1 = aiMessage1.attribute("thinking_signature", String.class);
        assertThat(signature1).isNotBlank();

        assertThat(aiMessage1.toolExecutionRequests()).hasSize(1);
        ToolExecutionRequest toolExecutionRequest1 = aiMessage1.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest1.name()).isEqualTo(toolSpecification.name());
        assertThat(toolExecutionRequest1.arguments()).contains("Munich");

        // given
        ToolExecutionResultMessage toolResultMessage1 = ToolExecutionResultMessage.from(toolExecutionRequest1, "sunny");

        // when
        ChatResponse chatResponse2 = model.chat(userMessage1, aiMessage1, toolResultMessage1);

        // then
        AiMessage aiMessage2 = chatResponse2.aiMessage();
        assertThat(aiMessage2.text()).containsIgnoringCase("sun");

        String thinking2 = aiMessage2.thinking();
        assertThat(thinking2).isNotBlank();

        String signature2 = aiMessage2.attribute("thinking_signature", String.class);
        assertThat(signature2).isNotBlank();

        assertThat(aiMessage2.toolExecutionRequests()).isEmpty();

        // given
        UserMessage userMessage2 = UserMessage.from("What is the weather in Paris?");

        // when
        ChatResponse chatResponse3 = model.chat(userMessage1, aiMessage1, toolResultMessage1, aiMessage2, userMessage2);

        // then
        AiMessage aiMessage3 = chatResponse3.aiMessage();

        String thinking3 = aiMessage3.thinking();
        assertThat(thinking3).isNotBlank();

        String signature3 = aiMessage3.attribute("thinking_signature", String.class);
        assertThat(signature3).isNotBlank();

        assertThat(aiMessage3.toolExecutionRequests()).hasSize(1);
        ToolExecutionRequest toolExecutionRequest2 = aiMessage3.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest2.name()).isEqualTo(toolSpecification.name());
        assertThat(toolExecutionRequest2.arguments()).contains("Paris");

        // given
        ToolExecutionResultMessage toolResultMessage2 = ToolExecutionResultMessage.from(toolExecutionRequest2, "rainy");

        // when
        ChatResponse chatResponse4 = model.chat(userMessage1, aiMessage1, toolResultMessage1, aiMessage2, userMessage2, aiMessage3, toolResultMessage2);

        // then
        AiMessage aiMessage4 = chatResponse4.aiMessage();
        assertThat(aiMessage4.text()).containsIgnoringCase("rain");

        String thinking4 = aiMessage4.thinking();
        assertThat(thinking4).isNotBlank();

        String signature4 = aiMessage4.attribute("thinking_signature", String.class);
        assertThat(signature4).isNotBlank();

        assertThat(aiMessage4.toolExecutionRequests()).isEmpty();

        // should send thinking in the follow-up requests
        List<HttpRequest> httpRequests = spyingHttpClient.requests();
        assertThat(httpRequests).hasSize(4);
        assertThat(httpRequests.get(1).body())
                .contains(jsonify(thinking1))
                .contains(jsonify(signature1));
        assertThat(httpRequests.get(2).body())
                .contains(jsonify(thinking2))
                .contains(jsonify(signature2));
        assertThat(httpRequests.get(3).body())
                .contains(jsonify(thinking3))
                .contains(jsonify(signature3));
    }

    @Test
    void test_redacted_thinking() {

        // given
        boolean returnThinking = true;
        // sendThinking = true by default

        ChatModel model = AnthropicChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(spyingHttpClient))
                .apiKey(System.getenv("ANTHROPIC_API_KEY"))
                .modelName(CLAUDE_3_7_SONNET_20250219)

                .thinkingType("enabled")
                .thinkingBudgetTokens(THINKING_BUDGET_TOKENS)
                .maxTokens(THINKING_BUDGET_TOKENS + 100)
                .returnThinking(returnThinking)

                .logRequests(true)
                .logResponses(true)
                .build();

        UserMessage userMessage1 = UserMessage.from("ANTHROPIC_MAGIC_STRING_TRIGGER_REDACTED_THINKING_46C9A13E193C177646C7398A98432ECCCE4C1253D5E2D82641AC0E52CC2876CB");

        // when
        ChatResponse chatResponse1 = model.chat(userMessage1);

        // then
        AiMessage aiMessage1 = chatResponse1.aiMessage();
        assertThat(aiMessage1.text()).isNotBlank();
        assertThat(aiMessage1.thinking()).isNull();
        assertThat(aiMessage1.attributes()).hasSize(1);
        List<String> redactedThinkings = aiMessage1.attribute("redacted_thinking", List.class);
        assertThat(redactedThinkings).hasSize(1);
        assertThat(redactedThinkings.get(0)).isNotBlank();

        // given
        UserMessage userMessage2 = UserMessage.from("What is the capital of Germany?");

        // when
        ChatResponse chatResponse2 = model.chat(userMessage1, aiMessage1, userMessage2);

        // then
        AiMessage aiMessage2 = chatResponse2.aiMessage();
        assertThat(aiMessage2.text()).contains("Berlin");

        // should send redacted thinking in the follow-up requests
        List<HttpRequest> httpRequests = spyingHttpClient.requests();
        assertThat(httpRequests).hasSize(2);
        assertThat(httpRequests.get(1).body())
                .contains(jsonify(aiMessage1.text()))
                .contains(jsonify(redactedThinkings.get(0)));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(booleans = false)
    void should_NOT_return_thinking(Boolean returnThinking) {

        // given
        ChatModel model = AnthropicChatModel.builder()
                .apiKey(System.getenv("ANTHROPIC_API_KEY"))
                .modelName(CLAUDE_3_7_SONNET_20250219)

                .thinkingType("enabled")
                .thinkingBudgetTokens(THINKING_BUDGET_TOKENS)
                .maxTokens(THINKING_BUDGET_TOKENS + 100)
                .returnThinking(returnThinking)

                .logRequests(true)
                .logResponses(true)
                .build();

        UserMessage userMessage = UserMessage.from("What is the capital of Germany?");

        // when
        ChatResponse chatResponse = model.chat(userMessage);

        // then
        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.text()).containsIgnoringCase("Berlin");
        assertThat(aiMessage.thinking()).isNull();
        assertThat(aiMessage.attributes()).isEmpty();
    }
}
