package dev.langchain4j.model.bedrock;

import static dev.langchain4j.model.bedrock.common.BedrockAiServicesIT.sleepIfNeeded;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Disabled("Run manually before release. Expensive to run very often.")
class BedrockChatModelThinkingIT {

    static final int THINKING_BUDGET_TOKENS = 1024;
    static final int SLEEPING_TIME_MULTIPLIER = 10;

    @ParameterizedTest
    @ValueSource(strings = {
            "us.anthropic.claude-sonnet-4-20250514-v1:0",
            "us.anthropic.claude-3-7-sonnet-20250219-v1:0",
    })
    void should_return_and_send_thinking(String modelId) {

        // given
        boolean returnThinking = true;
        // sendThinking = true by default

        BedrockChatRequestParameters parameters = BedrockChatRequestParameters.builder()
                .enableReasoning(THINKING_BUDGET_TOKENS)
                .build();

        ChatModel model = BedrockChatModel.builder()
                .modelId(modelId)

                .returnThinking(returnThinking)
                .defaultRequestParameters(parameters)

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
        assertThat(aiMessage1.attribute("thinking_signature", String.class)).isNotBlank();

        // given
        UserMessage userMessage2 = UserMessage.from("What is the capital of France?");

        // when
        sleepIfNeeded(SLEEPING_TIME_MULTIPLIER);
        ChatResponse chatResponse2 = model.chat(userMessage1, aiMessage1, userMessage2);

        // then
        AiMessage aiMessage2 = chatResponse2.aiMessage();
        assertThat(aiMessage2.text()).containsIgnoringCase("Paris");
        assertThat(aiMessage2.thinking()).isNotBlank();
        assertThat(aiMessage2.attribute("thinking_signature", String.class)).isNotBlank();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "us.anthropic.claude-sonnet-4-20250514-v1:0",
            "us.anthropic.claude-3-7-sonnet-20250219-v1:0",
            "us.deepseek.r1-v1:0"
    })
    void should_return_and_NOT_send_thinking(String modelId) {

        // given
        boolean returnThinking = true;
        boolean sendThinking = false;

        BedrockChatRequestParameters parameters = null;
        if (!modelId.contains("deepseek")) {
            parameters = BedrockChatRequestParameters.builder()
                    .enableReasoning(THINKING_BUDGET_TOKENS)
                    .build();
        }

        ChatModel model = BedrockChatModel.builder()
                .modelId(modelId)

                .returnThinking(returnThinking)
                .sendThinking(sendThinking)
                .defaultRequestParameters(parameters)

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
        if (!modelId.contains("deepseek")) {
            assertThat(aiMessage1.attribute("thinking_signature", String.class)).isNotBlank();
        }

        // given
        UserMessage userMessage2 = UserMessage.from("What is the capital of France?");

        // when
        sleepIfNeeded(SLEEPING_TIME_MULTIPLIER);
        ChatResponse chatResponse2 = model.chat(userMessage1, aiMessage1, userMessage2);

        // then
        AiMessage aiMessage2 = chatResponse2.aiMessage();
        assertThat(aiMessage2.text()).containsIgnoringCase("Paris");
        assertThat(aiMessage2.thinking()).isNotBlank();
        if (!modelId.contains("deepseek")) {
            assertThat(aiMessage2.attribute("thinking_signature", String.class)).isNotBlank();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "us.anthropic.claude-sonnet-4-20250514-v1:0",
            "us.anthropic.claude-3-7-sonnet-20250219-v1:0",
    })
    void should_return_and_send_thinking_with_tools(String modelId) {

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

        BedrockChatRequestParameters parameters = BedrockChatRequestParameters.builder()
                .toolSpecifications(List.of(toolSpecification))
                .enableReasoning(THINKING_BUDGET_TOKENS)
                .build();

        ChatModel model = BedrockChatModel.builder()
                .modelId(modelId)

                .returnThinking(returnThinking)
                .defaultRequestParameters(parameters)

                .logRequests(true)
                .logResponses(true)
                .build();

        UserMessage userMessage1 = UserMessage.from("What is the weather in Munich?");

        // when
        ChatResponse chatResponse1 = model.chat(userMessage1);

        // then
        AiMessage aiMessage1 = chatResponse1.aiMessage();
        assertThat(aiMessage1.thinking()).isNotBlank();
        assertThat(aiMessage1.attribute("thinking_signature", String.class)).isNotBlank();
        assertThat(aiMessage1.toolExecutionRequests()).hasSize(1);
        ToolExecutionRequest toolExecutionRequest1 = aiMessage1.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest1.name()).isEqualTo(toolSpecification.name());
        assertThat(toolExecutionRequest1.arguments()).contains("Munich");

        // given
        ToolExecutionResultMessage toolResultMessage1 = ToolExecutionResultMessage.from(toolExecutionRequest1, "sunny");

        // when
        sleepIfNeeded(SLEEPING_TIME_MULTIPLIER);
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
        sleepIfNeeded(SLEEPING_TIME_MULTIPLIER);
        ChatResponse chatResponse3 = model.chat(userMessage1, aiMessage1, toolResultMessage1, aiMessage2, userMessage2);

        // then
        AiMessage aiMessage3 = chatResponse3.aiMessage();
        assertThat(aiMessage3.thinking()).isNotBlank();
        assertThat(aiMessage3.attribute("thinking_signature", String.class)).isNotBlank();
        assertThat(aiMessage3.toolExecutionRequests()).hasSize(1);
        ToolExecutionRequest toolExecutionRequest2 = aiMessage3.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest2.name()).isEqualTo(toolSpecification.name());
        assertThat(toolExecutionRequest2.arguments()).contains("Paris");

        // given
        ToolExecutionResultMessage toolResultMessage2 = ToolExecutionResultMessage.from(toolExecutionRequest2, "rainy");

        // when
        sleepIfNeeded(SLEEPING_TIME_MULTIPLIER);
        ChatResponse chatResponse4 = model.chat(userMessage1, aiMessage1, toolResultMessage1, aiMessage2, userMessage2, aiMessage3, toolResultMessage2);

        // then
        AiMessage aiMessage4 = chatResponse4.aiMessage();
        assertThat(aiMessage4.text()).containsIgnoringCase("rain");
        assertThat(aiMessage4.thinking()).isNull();
        assertThat(aiMessage4.attributes()).isEmpty();
        assertThat(aiMessage4.toolExecutionRequests()).isEmpty();
    }

    @Test
    void test_interleaved_thinking() {

        // given
        String beta = "interleaved-thinking-2025-05-14";
        String modelId = "us.anthropic.claude-opus-4-20250514-v1:0";

        boolean returnThinking = true;
        // sendThinking = true by default

        ToolSpecification toolSpecification = ToolSpecification.builder()
                .name("getWeather")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("city")
                        .required("city")
                        .build())
                .build();

        BedrockChatRequestParameters parameters = BedrockChatRequestParameters.builder()
                .toolSpecifications(toolSpecification)
                .additionalModelRequestField("anthropic_beta", List.of(beta))
                .enableReasoning(THINKING_BUDGET_TOKENS)
                .build();

        ChatModel model = BedrockChatModel.builder()

                .modelId(modelId)
                .returnThinking(returnThinking)
                .defaultRequestParameters(parameters)

                .logRequests(true)
                .logResponses(true)
                .build();

        UserMessage userMessage1 = UserMessage.from("What is the weather in Munich?");

        // when
        ChatResponse chatResponse1 = model.chat(userMessage1);

        // then
        AiMessage aiMessage1 = chatResponse1.aiMessage();

        assertThat(aiMessage1.thinking()).isNotBlank();
        assertThat(aiMessage1.attribute("thinking_signature", String.class)).isNotBlank();

        assertThat(aiMessage1.toolExecutionRequests()).hasSize(1);
        ToolExecutionRequest toolExecutionRequest1 = aiMessage1.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest1.name()).isEqualTo(toolSpecification.name());
        assertThat(toolExecutionRequest1.arguments()).contains("Munich");

        // given
        ToolExecutionResultMessage toolResultMessage1 = ToolExecutionResultMessage.from(toolExecutionRequest1, "sunny");

        // when
        sleepIfNeeded(SLEEPING_TIME_MULTIPLIER);
        ChatResponse chatResponse2 = model.chat(userMessage1, aiMessage1, toolResultMessage1);

        // then
        AiMessage aiMessage2 = chatResponse2.aiMessage();
        assertThat(aiMessage2.text()).containsIgnoringCase("sun");

        assertThat(aiMessage2.thinking()).isNotBlank();
        assertThat(aiMessage2.attribute("thinking_signature", String.class)).isNotBlank();

        assertThat(aiMessage2.toolExecutionRequests()).isEmpty();

        // given
        UserMessage userMessage2 = UserMessage.from("What is the weather in Paris?");

        // when
        sleepIfNeeded(SLEEPING_TIME_MULTIPLIER);
        ChatResponse chatResponse3 = model.chat(userMessage1, aiMessage1, toolResultMessage1, aiMessage2, userMessage2);

        // then
        AiMessage aiMessage3 = chatResponse3.aiMessage();

        assertThat(aiMessage3.thinking()).isNotBlank();
        assertThat(aiMessage3.attribute("thinking_signature", String.class)).isNotBlank();

        assertThat(aiMessage3.toolExecutionRequests()).hasSize(1);
        ToolExecutionRequest toolExecutionRequest2 = aiMessage3.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest2.name()).isEqualTo(toolSpecification.name());
        assertThat(toolExecutionRequest2.arguments()).contains("Paris");

        // given
        ToolExecutionResultMessage toolResultMessage2 = ToolExecutionResultMessage.from(toolExecutionRequest2, "rainy");

        // when
        sleepIfNeeded(SLEEPING_TIME_MULTIPLIER);
        ChatResponse chatResponse4 = model.chat(userMessage1, aiMessage1, toolResultMessage1, aiMessage2, userMessage2, aiMessage3, toolResultMessage2);

        // then
        AiMessage aiMessage4 = chatResponse4.aiMessage();
        assertThat(aiMessage4.text()).containsIgnoringCase("rain");

        assertThat(aiMessage4.thinking()).isNotBlank();
        assertThat(aiMessage4.attribute("thinking_signature", String.class)).isNotBlank();

        assertThat(aiMessage4.toolExecutionRequests()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "us.anthropic.claude-sonnet-4-20250514-v1:0",
            "us.anthropic.claude-3-7-sonnet-20250219-v1:0",
            "us.deepseek.r1-v1:0"
    })
    void should_NOT_return_thinking(String modelId) {

        // given
        boolean returnThinking = false;

        BedrockChatRequestParameters parameters = null;
        if (!modelId.contains("deepseek")) {
            parameters = BedrockChatRequestParameters.builder()
                    .enableReasoning(THINKING_BUDGET_TOKENS)
                    .build();
        }

        ChatModel model = BedrockChatModel.builder()
                .modelId(modelId)

                .returnThinking(returnThinking)
                .defaultRequestParameters(parameters)

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

    @ParameterizedTest
    @ValueSource(strings = {
            "us.anthropic.claude-sonnet-4-20250514-v1:0",
            "us.anthropic.claude-3-7-sonnet-20250219-v1:0",
            "us.deepseek.r1-v1:0"
    })
    void should_NOT_return_thinking_when_returnThinking_is_not_set(String modelId) {

        // given
        Boolean returnThinking = null;

        BedrockChatRequestParameters parameters = null;
        if (!modelId.contains("deepseek")) {
            parameters = BedrockChatRequestParameters.builder()
                    .enableReasoning(THINKING_BUDGET_TOKENS)
                    .build();
        }

        ChatModel model = BedrockChatModel.builder()
                .modelId(modelId)

                .returnThinking(returnThinking)
                .defaultRequestParameters(parameters)

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

    @AfterEach
    void afterEach() {
        sleepIfNeeded(SLEEPING_TIME_MULTIPLIER);
    }
}
