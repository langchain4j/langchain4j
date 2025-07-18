package dev.langchain4j.model.anthropic;

import static dev.langchain4j.JsonTestUtils.jsonify;
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

@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class AnthropicChatModelThinkingIT {

    private static final int THINKING_BUDGET_TOKENS = 1024;

    private final SpyingHttpClient spyingHttpClient = new SpyingHttpClient(JdkHttpClient.builder().build());

    // TODO ensure no breaking (behaviour) changes for all providers

    @ParameterizedTest
    @EnumSource(value = AnthropicChatModelName.class, mode = INCLUDE, names = {
//            "CLAUDE_OPUS_4_20250514",
            "CLAUDE_SONNET_4_20250514",
//            "CLAUDE_3_7_SONNET_20250219"
    })
    void should_return_and_preserve_thinking(AnthropicChatModelName modelName) { // TODO name

        // given
        boolean returnThinking = true;
        boolean preserveThinking = true;
        // preserveThinking = true by default

        ChatModel model = AnthropicChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(spyingHttpClient))
                .apiKey(System.getenv("ANTHROPIC_API_KEY"))
                .modelName(modelName)

                .thinkingType("enabled")
                .thinkingBudgetTokens(THINKING_BUDGET_TOKENS)
                .maxTokens(THINKING_BUDGET_TOKENS + 100)
                .returnThinking(returnThinking)
                .preserveThinking(preserveThinking)

                .logRequests(true)
                .logResponses(true)
                .build();

        UserMessage userMessage = UserMessage.from("What is the capital of Germany?");

        // when
        ChatResponse chatResponse = model.chat(userMessage);

        // then
        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.text()).containsIgnoringCase("Berlin");
        assertThat(aiMessage.thinking()).containsIgnoringCase("Berlin");
        String signature = (String) aiMessage.metadata().get("thinking_signature");
        assertThat(signature).isNotBlank();

        // given
        UserMessage userMessage2 = UserMessage.from("What is the capital of France?");

        // when
        ChatResponse chatResponse2 = model.chat(userMessage, aiMessage, userMessage2);

        // then
        AiMessage aiMessage2 = chatResponse2.aiMessage();
        assertThat(aiMessage2.text()).containsIgnoringCase("Paris");
        assertThat(aiMessage2.thinking()).containsIgnoringCase("Paris");
        assertThat((String) aiMessage2.metadata().get("thinking_signature")).isNotBlank();

        // should preserve thinking in the follow-up request
        List<HttpRequest> httpRequests = spyingHttpClient.requests();
        assertThat(httpRequests).hasSize(2);
        assertThat(httpRequests.get(1).body())
                .contains(jsonify(aiMessage.text()))
                .contains(jsonify(aiMessage.thinking()))
                .contains(jsonify(signature));
    }

    @ParameterizedTest
    @EnumSource(value = AnthropicChatModelName.class, mode = INCLUDE, names = {
            "CLAUDE_OPUS_4_20250514",
            "CLAUDE_SONNET_4_20250514",
            "CLAUDE_3_7_SONNET_20250219"
    })
    void should_return_and_NOT_preserve_thinking(AnthropicChatModelName modelName) { // TODO name

        // given
        boolean returnThinking = true;
        boolean preserveThinking = false; // TODO name, everywhere

        ChatModel model = AnthropicChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(spyingHttpClient))
                .apiKey(System.getenv("ANTHROPIC_API_KEY"))
                .modelName(modelName)

                .thinkingType("enabled")
                .thinkingBudgetTokens(THINKING_BUDGET_TOKENS)
                .maxTokens(THINKING_BUDGET_TOKENS + 100)
                .returnThinking(returnThinking)
                .preserveThinking(preserveThinking)

                .logRequests(true)
                .logResponses(true)
                .build();

        UserMessage userMessage = UserMessage.from("What is the capital of Germany?");

        // when
        ChatResponse chatResponse = model.chat(userMessage);

        // then
        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.text()).containsIgnoringCase("Berlin");
        assertThat(aiMessage.thinking()).containsIgnoringCase("Berlin");
        String signature = (String) aiMessage.metadata().get("thinking_signature");
        assertThat(signature).isNotBlank();

        // given
        UserMessage userMessage2 = UserMessage.from("What is the capital of France?");

        // when
        ChatResponse chatResponse2 = model.chat(userMessage, aiMessage, userMessage2);

        // then
        AiMessage aiMessage2 = chatResponse2.aiMessage();
        assertThat(aiMessage2.text()).containsIgnoringCase("Paris");
        assertThat(aiMessage2.thinking()).containsIgnoringCase("Paris");
        assertThat((String) aiMessage2.metadata().get("thinking_signature")).isNotBlank();

        // should NOT preserve thinking in the follow-up request
        List<HttpRequest> httpRequests = spyingHttpClient.requests();
        assertThat(httpRequests).hasSize(2);
        assertThat(httpRequests.get(1).body())
                .contains(jsonify(aiMessage.text()))
                .doesNotContain(jsonify(aiMessage.thinking()))
                .doesNotContain(jsonify(signature));
    }

    @ParameterizedTest
    @EnumSource(value = AnthropicChatModelName.class, mode = INCLUDE, names = {
            "CLAUDE_OPUS_4_20250514",
            "CLAUDE_SONNET_4_20250514",
            "CLAUDE_3_7_SONNET_20250219"
    })
    void should_return_and_preserve_thinking_with_tools(AnthropicChatModelName modelName) { // TODO name

        // given
        boolean returnThinking = true;
        // preserveThinking = true by default

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
        String signature1 = (String) aiMessage1.metadata().get("thinking_signature");
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
        assertThat(aiMessage2.metadata()).isEmpty();
        assertThat(aiMessage2.toolExecutionRequests()).isEmpty();

        // given
        UserMessage userMessage2 = UserMessage.from("What is the weather in Paris?");

        // when
        ChatResponse chatResponse3 = model.chat(userMessage1, aiMessage1, toolResultMessage1, aiMessage2, userMessage2);

        // then
        AiMessage aiMessage3 = chatResponse3.aiMessage();
        String thinking2 = aiMessage3.thinking();
        assertThat(thinking2).isNotBlank();
        String signature2 = (String) aiMessage3.metadata().get("thinking_signature");
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
        assertThat(aiMessage4.metadata()).isEmpty();
        assertThat(aiMessage4.toolExecutionRequests()).isEmpty();

        // should preserve thinking in the follow-up requests
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
    void test_interleaved_thinking() { // TODO name

        // given
        String beta = "interleaved-thinking-2025-05-14";
        AnthropicChatModelName modelName = CLAUDE_OPUS_4_20250514;

        boolean returnThinking = true;
        // preserveThinking = true by default

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

        String signature1 = (String) aiMessage1.metadata().get("thinking_signature");
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

        String signature2 = (String) aiMessage2.metadata().get("thinking_signature");
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

        String signature3 = (String) aiMessage3.metadata().get("thinking_signature");
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

        String signature4 = (String) aiMessage4.metadata().get("thinking_signature");
        assertThat(signature4).isNotBlank();

        assertThat(aiMessage4.toolExecutionRequests()).isEmpty();

        // should preserve thinking in the follow-up requests
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

    @ParameterizedTest
    @EnumSource(value = AnthropicChatModelName.class, mode = INCLUDE, names = {
            "CLAUDE_OPUS_4_20250514",
            "CLAUDE_SONNET_4_20250514",
            "CLAUDE_3_7_SONNET_20250219"
    })
    void should_answer_without_thinking_when_returnThinking_is_false(AnthropicChatModelName modelName) { // TODO name

        // given
        boolean returnThinking = false;

        ChatModel model = AnthropicChatModel.builder()
                .apiKey(System.getenv("ANTHROPIC_API_KEY"))
                .modelName(modelName)

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
        assertThat(aiMessage.metadata()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = AnthropicChatModelName.class, mode = INCLUDE, names = {
            "CLAUDE_OPUS_4_20250514",
            "CLAUDE_SONNET_4_20250514",
            "CLAUDE_3_7_SONNET_20250219"
    })
    void should_answer_without_thinking_when_returnThinking_is_not_set(AnthropicChatModelName modelName) { // TODO name

        // given
        ChatModel model = AnthropicChatModel.builder()
                .apiKey(System.getenv("ANTHROPIC_API_KEY"))
                .modelName(modelName)

                .thinkingType("enabled")
                .thinkingBudgetTokens(THINKING_BUDGET_TOKENS)
                .maxTokens(THINKING_BUDGET_TOKENS + 100)

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
        assertThat(aiMessage.metadata()).isEmpty();
    }
}
