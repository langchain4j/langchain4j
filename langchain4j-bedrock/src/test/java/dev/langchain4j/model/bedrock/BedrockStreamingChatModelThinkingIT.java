package dev.langchain4j.model.bedrock;

import static dev.langchain4j.model.bedrock.BedrockChatModelThinkingIT.SLEEPING_TIME_MULTIPLIER;
import static dev.langchain4j.model.bedrock.BedrockChatModelThinkingIT.THINKING_BUDGET_TOKENS;
import static dev.langchain4j.model.bedrock.common.BedrockAiServicesIT.sleepIfNeeded;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.util.List;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.TestStreamingChatResponseHandler;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;

@EnabledIfEnvironmentVariable(named = "AWS_SECRET_ACCESS_KEY", matches = ".+")
class BedrockStreamingChatModelThinkingIT {

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

        StreamingChatModel model = BedrockStreamingChatModel.builder()
                .modelId(modelId)

                .returnThinking(returnThinking)
                .defaultRequestParameters(parameters)

                .logRequests(true)
                .logResponses(true)
                .build();

        UserMessage userMessage1 = UserMessage.from("What is the capital of Germany?");

        // when
        TestStreamingChatResponseHandler spyHandler1 = spy(new TestStreamingChatResponseHandler());
        model.chat(List.of(userMessage1), spyHandler1);

        // then
        AiMessage aiMessage1 = spyHandler1.get().aiMessage();
        assertThat(aiMessage1.text()).containsIgnoringCase("Berlin");
        assertThat(aiMessage1.thinking()).isNotBlank();
        assertThat(aiMessage1.attribute("thinking_signature", String.class)).isNotBlank();

        InOrder inOrder1 = inOrder(spyHandler1);
        inOrder1.verify(spyHandler1).get();
        inOrder1.verify(spyHandler1, atLeastOnce()).onPartialThinking(any());
        inOrder1.verify(spyHandler1, atLeastOnce()).onPartialResponse(any());
        inOrder1.verify(spyHandler1).onCompleteResponse(any());
        inOrder1.verifyNoMoreInteractions();
        verifyNoMoreInteractions(spyHandler1);

        // given
        UserMessage userMessage2 = UserMessage.from("What is the capital of France?");

        // when
        sleepIfNeeded(SLEEPING_TIME_MULTIPLIER);
        TestStreamingChatResponseHandler spyHandler2 = spy(new TestStreamingChatResponseHandler());
        model.chat(List.of(userMessage1, aiMessage1, userMessage2), spyHandler2);

        // then
        AiMessage aiMessage2 = spyHandler2.get().aiMessage();
        assertThat(aiMessage2.text()).containsIgnoringCase("Paris");
        assertThat(aiMessage2.thinking()).isNotBlank();
        assertThat(aiMessage2.attribute("thinking_signature", String.class)).isNotBlank();

        InOrder inOrder2 = inOrder(spyHandler2);
        inOrder2.verify(spyHandler2).get();
        inOrder2.verify(spyHandler2, atLeastOnce()).onPartialThinking(any());
        inOrder2.verify(spyHandler2, atLeastOnce()).onPartialResponse(any());
        inOrder2.verify(spyHandler2).onCompleteResponse(any());
        inOrder2.verifyNoMoreInteractions();
        verifyNoMoreInteractions(spyHandler2);
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

        StreamingChatModel model = BedrockStreamingChatModel.builder()
                .modelId(modelId)

                .returnThinking(returnThinking)
                .sendThinking(sendThinking)
                .defaultRequestParameters(parameters)

                .logRequests(true)
                .logResponses(true)
                .build();

        UserMessage userMessage = UserMessage.from("What is the capital of Germany?");

        // when
        TestStreamingChatResponseHandler spyHandler1 = spy(new TestStreamingChatResponseHandler());
        model.chat(List.of(userMessage), spyHandler1);

        // then
        AiMessage aiMessage1 = spyHandler1.get().aiMessage();
        assertThat(aiMessage1.text()).containsIgnoringCase("Berlin");
        assertThat(aiMessage1.thinking())
                .containsIgnoringCase("Berlin")
                .isEqualTo(spyHandler1.getThinking());
        if (!modelId.contains("deepseek")) {
            assertThat(aiMessage1.attribute("thinking_signature", String.class)).isNotBlank();
        }

        InOrder inOrder1 = inOrder(spyHandler1);
        inOrder1.verify(spyHandler1).get();
        inOrder1.verify(spyHandler1, atLeastOnce()).onPartialThinking(any());
        inOrder1.verify(spyHandler1, atLeastOnce()).onPartialResponse(any());
        inOrder1.verify(spyHandler1).onCompleteResponse(any());
        inOrder1.verify(spyHandler1).getThinking();
        inOrder1.verifyNoMoreInteractions();
        verifyNoMoreInteractions(spyHandler1);

        // given
        UserMessage userMessage2 = UserMessage.from("What is the capital of France?");

        // when
        sleepIfNeeded(SLEEPING_TIME_MULTIPLIER);
        TestStreamingChatResponseHandler spyHandler2 = spy(new TestStreamingChatResponseHandler());
        model.chat(List.of(userMessage, aiMessage1, userMessage2), spyHandler2);

        // then
        AiMessage aiMessage2 = spyHandler2.get().aiMessage();
        assertThat(aiMessage2.text()).containsIgnoringCase("Paris");
        assertThat(aiMessage2.thinking()).isNotBlank();
        if (!modelId.contains("deepseek")) {
            assertThat(aiMessage2.attribute("thinking_signature", String.class)).isNotBlank();
        }

        InOrder inOrder2 = inOrder(spyHandler2);
        inOrder2.verify(spyHandler2).get();
        inOrder2.verify(spyHandler2, atLeastOnce()).onPartialThinking(any());
        inOrder2.verify(spyHandler2, atLeastOnce()).onPartialResponse(any());
        inOrder2.verify(spyHandler2).onCompleteResponse(any());
        inOrder2.verifyNoMoreInteractions();
        verifyNoMoreInteractions(spyHandler2);
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

        StreamingChatModel model = BedrockStreamingChatModel.builder()
                .modelId(modelId)

                .returnThinking(returnThinking)
                .defaultRequestParameters(parameters)

                .logRequests(true)
                .logResponses(true)
                .build();

        UserMessage userMessage1 = UserMessage.from("What is the weather in Munich?");

        // when
        TestStreamingChatResponseHandler spyHandler1 = spy(new TestStreamingChatResponseHandler());
        model.chat(List.of(userMessage1), spyHandler1);

        // then
        AiMessage aiMessage1 = spyHandler1.get().aiMessage();
        assertThat(aiMessage1.thinking()).isNotBlank();
        assertThat(aiMessage1.attribute("thinking_signature", String.class)).isNotBlank();
        assertThat(aiMessage1.toolExecutionRequests()).hasSize(1);
        ToolExecutionRequest toolExecutionRequest1 = aiMessage1.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest1.name()).isEqualTo(toolSpecification.name());
        assertThat(toolExecutionRequest1.arguments()).contains("Munich");

        InOrder inOrder1 = inOrder(spyHandler1);
        inOrder1.verify(spyHandler1).get();
        inOrder1.verify(spyHandler1, atLeastOnce()).onPartialThinking(any());
        inOrder1.verify(spyHandler1, atLeast(0)).onPartialResponse(any()); // do not care if onPartialResponse was called
        inOrder1.verify(spyHandler1).onCompleteToolCall(any());
        inOrder1.verify(spyHandler1).onCompleteResponse(any());
        inOrder1.verifyNoMoreInteractions();
        verifyNoMoreInteractions(spyHandler1);

        // given
        ToolExecutionResultMessage toolResultMessage1 = ToolExecutionResultMessage.from(toolExecutionRequest1, "sunny");

        // when
        sleepIfNeeded(SLEEPING_TIME_MULTIPLIER);
        TestStreamingChatResponseHandler spyHandler2 = spy(new TestStreamingChatResponseHandler());
        model.chat(List.of(userMessage1, aiMessage1, toolResultMessage1), spyHandler2);

        // then
        AiMessage aiMessage2 = spyHandler2.get().aiMessage();
        assertThat(aiMessage2.text()).containsIgnoringCase("sun");
        assertThat(aiMessage2.thinking()).isNull();
        assertThat(aiMessage2.attributes()).isEmpty();
        assertThat(aiMessage2.toolExecutionRequests()).isEmpty();

        InOrder inOrder2 = inOrder(spyHandler2);
        inOrder2.verify(spyHandler2).get();
        inOrder2.verify(spyHandler2, atLeastOnce()).onPartialResponse(any());
        inOrder2.verify(spyHandler2).onCompleteResponse(any());
        inOrder2.verifyNoMoreInteractions();
        verifyNoMoreInteractions(spyHandler2);

        // given
        UserMessage userMessage2 = UserMessage.from("What is the weather in Paris?");

        // when
        sleepIfNeeded(SLEEPING_TIME_MULTIPLIER);
        TestStreamingChatResponseHandler spyHandler3 = spy(new TestStreamingChatResponseHandler());
        model.chat(List.of(userMessage1, aiMessage1, toolResultMessage1, aiMessage2, userMessage2), spyHandler3);

        // then
        AiMessage aiMessage3 = spyHandler3.get().aiMessage();
        assertThat(aiMessage3.thinking()).isNotBlank();
        assertThat(aiMessage3.attribute("thinking_signature", String.class)).isNotBlank();
        assertThat(aiMessage3.toolExecutionRequests()).hasSize(1);
        ToolExecutionRequest toolExecutionRequest2 = aiMessage3.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest2.name()).isEqualTo(toolSpecification.name());
        assertThat(toolExecutionRequest2.arguments()).contains("Paris");

        InOrder inOrder3 = inOrder(spyHandler3);
        inOrder3.verify(spyHandler3).get();
        inOrder3.verify(spyHandler3, atLeastOnce()).onPartialThinking(any());
        inOrder3.verify(spyHandler3, atLeast(0)).onPartialResponse(any()); // do not care if onPartialResponse was called
        inOrder3.verify(spyHandler3).onCompleteToolCall(any());
        inOrder3.verify(spyHandler3).onCompleteResponse(any());
        inOrder3.verifyNoMoreInteractions();
        verifyNoMoreInteractions(spyHandler3);

        // given
        ToolExecutionResultMessage toolResultMessage2 = ToolExecutionResultMessage.from(toolExecutionRequest2, "rainy");

        // when
        sleepIfNeeded(SLEEPING_TIME_MULTIPLIER);
        TestStreamingChatResponseHandler spyHandler4 = spy(new TestStreamingChatResponseHandler());
        model.chat(List.of(userMessage1, aiMessage1, toolResultMessage1, aiMessage2, userMessage2, aiMessage3, toolResultMessage2), spyHandler4);

        // then
        AiMessage aiMessage4 = spyHandler4.get().aiMessage();
        assertThat(aiMessage4.text()).containsIgnoringCase("rain");
        assertThat(aiMessage4.thinking()).isNull();
        assertThat(aiMessage4.attributes()).isEmpty();
        assertThat(aiMessage4.toolExecutionRequests()).isEmpty();

        InOrder inOrder4 = inOrder(spyHandler4);
        inOrder4.verify(spyHandler4).get();
        inOrder4.verify(spyHandler4, atLeastOnce()).onPartialResponse(any());
        inOrder4.verify(spyHandler4).onCompleteResponse(any());
        inOrder4.verifyNoMoreInteractions();
        verifyNoMoreInteractions(spyHandler4);
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

        StreamingChatModel model = BedrockStreamingChatModel.builder()

                .modelId(modelId)
                .returnThinking(returnThinking)
                .defaultRequestParameters(parameters)

                .logRequests(true)
                .logResponses(true)
                .build();

        UserMessage userMessage1 = UserMessage.from("What is the weather in Munich?");

        // when
        TestStreamingChatResponseHandler spyHandler1 = spy(new TestStreamingChatResponseHandler());
        model.chat(List.of(userMessage1), spyHandler1);

        // then
        AiMessage aiMessage1 = spyHandler1.get().aiMessage();

        assertThat(aiMessage1.thinking()).isNotBlank();
        assertThat(aiMessage1.attribute("thinking_signature", String.class)).isNotBlank();

        assertThat(aiMessage1.toolExecutionRequests()).hasSize(1);
        ToolExecutionRequest toolExecutionRequest1 = aiMessage1.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest1.name()).isEqualTo(toolSpecification.name());
        assertThat(toolExecutionRequest1.arguments()).contains("Munich");

        InOrder inOrder1 = inOrder(spyHandler1);
        inOrder1.verify(spyHandler1).get();
        inOrder1.verify(spyHandler1, atLeastOnce()).onPartialThinking(any());
        inOrder1.verify(spyHandler1, atLeastOnce()).onPartialResponse(any());
        inOrder1.verify(spyHandler1).onCompleteToolCall(any());
        inOrder1.verify(spyHandler1).onCompleteResponse(any());
        inOrder1.verifyNoMoreInteractions();
        verifyNoMoreInteractions(spyHandler1);

        // given
        ToolExecutionResultMessage toolResultMessage1 = ToolExecutionResultMessage.from(toolExecutionRequest1, "sunny");

        // when
        sleepIfNeeded(SLEEPING_TIME_MULTIPLIER);
        TestStreamingChatResponseHandler spyHandler2 = spy(new TestStreamingChatResponseHandler());
        model.chat(List.of(userMessage1, aiMessage1, toolResultMessage1), spyHandler2);

        // then
        AiMessage aiMessage2 = spyHandler2.get().aiMessage();
        assertThat(aiMessage2.text()).containsIgnoringCase("sun");

        assertThat(aiMessage2.thinking()).isNotBlank();
        assertThat(aiMessage2.attribute("thinking_signature", String.class)).isNotBlank();

        assertThat(aiMessage2.toolExecutionRequests()).isEmpty();

        InOrder inOrder2 = inOrder(spyHandler2);
        inOrder2.verify(spyHandler2).get();
        inOrder2.verify(spyHandler2, atLeastOnce()).onPartialThinking(any());
        inOrder2.verify(spyHandler2, atLeastOnce()).onPartialResponse(any());
        inOrder2.verify(spyHandler2).onCompleteResponse(any());
        inOrder2.verifyNoMoreInteractions();
        verifyNoMoreInteractions(spyHandler2);

        // given
        UserMessage userMessage2 = UserMessage.from("What is the weather in Paris?");

        // when
        sleepIfNeeded(SLEEPING_TIME_MULTIPLIER);
        TestStreamingChatResponseHandler spyHandler3 = spy(new TestStreamingChatResponseHandler());
        model.chat(List.of(userMessage1, aiMessage1, toolResultMessage1, aiMessage2, userMessage2), spyHandler3);

        // then
        AiMessage aiMessage3 = spyHandler3.get().aiMessage();

        assertThat(aiMessage3.thinking()).isNotBlank();
        assertThat(aiMessage3.attribute("thinking_signature", String.class)).isNotBlank();

        assertThat(aiMessage3.toolExecutionRequests()).hasSize(1);
        ToolExecutionRequest toolExecutionRequest2 = aiMessage3.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest2.name()).isEqualTo(toolSpecification.name());
        assertThat(toolExecutionRequest2.arguments()).contains("Paris");

        InOrder inOrder3 = inOrder(spyHandler3);
        inOrder3.verify(spyHandler3).get();
        inOrder3.verify(spyHandler3, atLeastOnce()).onPartialThinking(any());
        inOrder3.verify(spyHandler3, atLeastOnce()).onPartialResponse(any());
        inOrder3.verify(spyHandler3).onCompleteToolCall(any());
        inOrder3.verify(spyHandler3).onCompleteResponse(any());
        inOrder3.verifyNoMoreInteractions();
        verifyNoMoreInteractions(spyHandler3);

        // given
        ToolExecutionResultMessage toolResultMessage2 = ToolExecutionResultMessage.from(toolExecutionRequest2, "rainy");

        // when
        sleepIfNeeded(SLEEPING_TIME_MULTIPLIER);
        TestStreamingChatResponseHandler spyHandler4 = spy(new TestStreamingChatResponseHandler());
        model.chat(List.of(userMessage1, aiMessage1, toolResultMessage1, aiMessage2, userMessage2, aiMessage3, toolResultMessage2), spyHandler4);

        // then
        AiMessage aiMessage4 = spyHandler4.get().aiMessage();
        assertThat(aiMessage4.text()).containsIgnoringCase("rain");

        assertThat(aiMessage4.thinking()).isNotBlank();
        assertThat(aiMessage4.attribute("thinking_signature", String.class)).isNotBlank();

        assertThat(aiMessage4.toolExecutionRequests()).isEmpty();

        InOrder inOrder4 = inOrder(spyHandler4);
        inOrder4.verify(spyHandler4).get();
        inOrder4.verify(spyHandler4, atLeastOnce()).onPartialThinking(any());
        inOrder4.verify(spyHandler4, atLeastOnce()).onPartialResponse(any());
        inOrder4.verify(spyHandler4).onCompleteResponse(any());
        inOrder4.verifyNoMoreInteractions();
        verifyNoMoreInteractions(spyHandler4);
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

        StreamingChatModel model = BedrockStreamingChatModel.builder()
                .modelId(modelId)

                .returnThinking(returnThinking)
                .defaultRequestParameters(parameters)

                .logRequests(true)
                .logResponses(true)
                .build();

        String userMessage = "What is the capital of Germany?";

        // when
        TestStreamingChatResponseHandler spyHandler = spy(new TestStreamingChatResponseHandler());
        model.chat(userMessage, spyHandler);

        // then
        AiMessage aiMessage = spyHandler.get().aiMessage();
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

        StreamingChatModel model = BedrockStreamingChatModel.builder()
                .modelId(modelId)

                .returnThinking(returnThinking)
                .defaultRequestParameters(parameters)

                .logRequests(true)
                .logResponses(true)
                .build();

        String userMessage = "What is the capital of Germany?";

        // when
        TestStreamingChatResponseHandler spyHandler = spy(new TestStreamingChatResponseHandler());
        model.chat(userMessage, spyHandler);

        // then
        AiMessage aiMessage = spyHandler.get().aiMessage();
        assertThat(aiMessage.text()).containsIgnoringCase("Berlin");
        assertThat(aiMessage.thinking()).isNull();
        assertThat(aiMessage.attributes()).isEmpty();
    }

    @AfterEach
    void afterEach() {
        sleepIfNeeded(SLEEPING_TIME_MULTIPLIER);
    }
}
