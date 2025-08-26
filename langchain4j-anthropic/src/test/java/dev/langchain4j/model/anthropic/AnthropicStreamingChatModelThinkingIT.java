package dev.langchain4j.model.anthropic;

import static dev.langchain4j.JsonTestUtils.jsonify;
import static dev.langchain4j.model.anthropic.AnthropicChatModelName.CLAUDE_3_7_SONNET_20250219;
import static dev.langchain4j.model.anthropic.AnthropicChatModelName.CLAUDE_OPUS_4_20250514;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.EnumSource.Mode.INCLUDE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
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
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.SpyingHttpClient;
import dev.langchain4j.http.client.jdk.JdkHttpClient;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.TestStreamingChatResponseHandler;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;

@Disabled("Run manually before release. Expensive to run very often.")
class AnthropicStreamingChatModelThinkingIT {

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

        StreamingChatModel model = AnthropicStreamingChatModel.builder()
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
        TestStreamingChatResponseHandler spyHandler1 = spy(new TestStreamingChatResponseHandler());
        model.chat(List.of(userMessage1), spyHandler1);

        // then
        AiMessage aiMessage1 = spyHandler1.get().aiMessage();
        assertThat(aiMessage1.text()).containsIgnoringCase("Berlin");
        assertThat(aiMessage1.thinking())
                .containsIgnoringCase("Berlin")
                .isEqualTo(spyHandler1.getThinking());
        String signature1 = aiMessage1.attribute("thinking_signature", String.class);
        assertThat(signature1).isNotBlank();

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

        StreamingChatModel model = AnthropicStreamingChatModel.builder()
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
        TestStreamingChatResponseHandler spyHandler1 = spy(new TestStreamingChatResponseHandler());
        model.chat(List.of(userMessage1), spyHandler1);

        // then
        AiMessage aiMessage1 = spyHandler1.get().aiMessage();
        assertThat(aiMessage1.text()).containsIgnoringCase("Berlin");
        assertThat(aiMessage1.thinking()).isNotBlank();
        String signature1 = aiMessage1.attribute("thinking_signature", String.class);
        assertThat(signature1).isNotBlank();

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

        StreamingChatModel model = AnthropicStreamingChatModel.builder()
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
        TestStreamingChatResponseHandler spyHandler1 = spy(new TestStreamingChatResponseHandler());
        model.chat(List.of(userMessage1), spyHandler1);

        // then
        AiMessage aiMessage1 = spyHandler1.get().aiMessage();
        String thinking1 = aiMessage1.thinking();
        assertThat(thinking1).isNotBlank();
        String signature1 = aiMessage1.attribute("thinking_signature", String.class);
        assertThat(signature1).isNotBlank();
        assertThat(aiMessage1.toolExecutionRequests()).hasSize(1);
        ToolExecutionRequest toolExecutionRequest1 = aiMessage1.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest1.name()).isEqualTo(toolSpecification.name());
        assertThat(toolExecutionRequest1.arguments()).contains("Munich");

        InOrder inOrder1 = inOrder(spyHandler1);
        inOrder1.verify(spyHandler1).get();
        inOrder1.verify(spyHandler1, atLeastOnce()).onPartialThinking(any());
        inOrder1.verify(spyHandler1, atLeast(0)).onPartialResponse(any()); // do not care if onPartialResponse was called
        inOrder1.verify(spyHandler1, atLeastOnce()).onPartialToolCall(argThat(toolCall ->
                toolCall.name().equals(toolExecutionRequest1.name())));
        inOrder1.verify(spyHandler1).onCompleteToolCall(argThat(toolCall ->
                toolCall.toolExecutionRequest().equals(toolExecutionRequest1)));
        inOrder1.verify(spyHandler1).onCompleteResponse(any());
        inOrder1.verifyNoMoreInteractions();
        verifyNoMoreInteractions(spyHandler1);

        // given
        ToolExecutionResultMessage toolResultMessage1 = ToolExecutionResultMessage.from(toolExecutionRequest1, "sunny");

        // when
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
        TestStreamingChatResponseHandler spyHandler3 = spy(new TestStreamingChatResponseHandler());
        model.chat(List.of(userMessage1, aiMessage1, toolResultMessage1, aiMessage2, userMessage2), spyHandler3);

        // then
        AiMessage aiMessage3 = spyHandler3.get().aiMessage();
        String thinking2 = aiMessage3.thinking();
        assertThat(thinking2).isNotBlank();
        String signature2 = aiMessage3.attribute("thinking_signature", String.class);
        assertThat(signature2).isNotBlank();
        assertThat(aiMessage3.toolExecutionRequests()).hasSize(1);
        ToolExecutionRequest toolExecutionRequest2 = aiMessage3.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest2.name()).isEqualTo(toolSpecification.name());
        assertThat(toolExecutionRequest2.arguments()).contains("Paris");

        InOrder inOrder3 = inOrder(spyHandler3);
        inOrder3.verify(spyHandler3).get();
        inOrder3.verify(spyHandler3, atLeastOnce()).onPartialThinking(any());
        inOrder3.verify(spyHandler3, atLeast(0)).onPartialResponse(any()); // do not care if onPartialResponse was called
        inOrder3.verify(spyHandler3, atLeastOnce()).onPartialToolCall(argThat(toolCall ->
                toolCall.name().equals(toolExecutionRequest2.name())));
        inOrder3.verify(spyHandler3).onCompleteToolCall(argThat(toolCall ->
                toolCall.toolExecutionRequest().equals(toolExecutionRequest2)));
        inOrder3.verify(spyHandler3).onCompleteResponse(any());
        inOrder3.verifyNoMoreInteractions();
        verifyNoMoreInteractions(spyHandler3);

        // given
        ToolExecutionResultMessage toolResultMessage2 = ToolExecutionResultMessage.from(toolExecutionRequest2, "rainy");

        // when
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

        StreamingChatModel model = AnthropicStreamingChatModel.builder()
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
        TestStreamingChatResponseHandler spyHandler1 = spy(new TestStreamingChatResponseHandler());
        model.chat(List.of(userMessage1), spyHandler1);

        // then
        AiMessage aiMessage1 = spyHandler1.get().aiMessage();

        String thinking1 = aiMessage1.thinking();
        assertThat(thinking1).isNotBlank();

        String signature1 = aiMessage1.attribute("thinking_signature", String.class);
        assertThat(signature1).isNotBlank();

        assertThat(aiMessage1.toolExecutionRequests()).hasSize(1);
        ToolExecutionRequest toolExecutionRequest1 = aiMessage1.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest1.name()).isEqualTo(toolSpecification.name());
        assertThat(toolExecutionRequest1.arguments()).contains("Munich");

        InOrder inOrder1 = inOrder(spyHandler1);
        inOrder1.verify(spyHandler1).get();
        inOrder1.verify(spyHandler1, atLeastOnce()).onPartialThinking(any());
        inOrder1.verify(spyHandler1, atLeastOnce()).onPartialResponse(any());
        inOrder1.verify(spyHandler1, atLeastOnce()).onPartialToolCall(argThat(toolCall ->
                toolCall.name().equals(toolExecutionRequest1.name())));
        inOrder1.verify(spyHandler1).onCompleteToolCall(argThat(toolCall ->
                toolCall.toolExecutionRequest().equals(toolExecutionRequest1)));
        inOrder1.verify(spyHandler1).onCompleteResponse(any());
        inOrder1.verifyNoMoreInteractions();
        verifyNoMoreInteractions(spyHandler1);

        // given
        ToolExecutionResultMessage toolResultMessage1 = ToolExecutionResultMessage.from(toolExecutionRequest1, "sunny");

        // when
        TestStreamingChatResponseHandler spyHandler2 = spy(new TestStreamingChatResponseHandler());
        model.chat(List.of(userMessage1, aiMessage1, toolResultMessage1), spyHandler2);

        // then
        AiMessage aiMessage2 = spyHandler2.get().aiMessage();
        assertThat(aiMessage2.text()).containsIgnoringCase("sun");

        String thinking2 = aiMessage2.thinking();
        assertThat(thinking2).isNotBlank();

        String signature2 = aiMessage2.attribute("thinking_signature", String.class);
        assertThat(signature2).isNotBlank();

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
        TestStreamingChatResponseHandler spyHandler3 = spy(new TestStreamingChatResponseHandler());
        model.chat(List.of(userMessage1, aiMessage1, toolResultMessage1, aiMessage2, userMessage2), spyHandler3);

        // then
        AiMessage aiMessage3 = spyHandler3.get().aiMessage();

        String thinking3 = aiMessage3.thinking();
        assertThat(thinking3).isNotBlank();

        String signature3 = aiMessage3.attribute("thinking_signature", String.class);
        assertThat(signature3).isNotBlank();

        assertThat(aiMessage3.toolExecutionRequests()).hasSize(1);
        ToolExecutionRequest toolExecutionRequest2 = aiMessage3.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest2.name()).isEqualTo(toolSpecification.name());
        assertThat(toolExecutionRequest2.arguments()).contains("Paris");

        InOrder inOrder3 = inOrder(spyHandler3);
        inOrder3.verify(spyHandler3).get();
        inOrder3.verify(spyHandler3, atLeastOnce()).onPartialThinking(any());
        inOrder3.verify(spyHandler3, atLeastOnce()).onPartialResponse(any());
        inOrder3.verify(spyHandler3, atLeastOnce()).onPartialToolCall(argThat(toolCall ->
                toolCall.name().equals(toolExecutionRequest2.name())));
        inOrder3.verify(spyHandler3).onCompleteToolCall(argThat(toolCall ->
                toolCall.toolExecutionRequest().equals(toolExecutionRequest2)));
        inOrder3.verify(spyHandler3).onCompleteResponse(any());
        inOrder3.verifyNoMoreInteractions();
        verifyNoMoreInteractions(spyHandler3);

        // given
        ToolExecutionResultMessage toolResultMessage2 = ToolExecutionResultMessage.from(toolExecutionRequest2, "rainy");

        // when
        TestStreamingChatResponseHandler spyHandler4 = spy(new TestStreamingChatResponseHandler());
        model.chat(List.of(userMessage1, aiMessage1, toolResultMessage1, aiMessage2, userMessage2, aiMessage3, toolResultMessage2), spyHandler4);

        // then
        AiMessage aiMessage4 = spyHandler4.get().aiMessage();
        assertThat(aiMessage4.text()).containsIgnoringCase("rain");

        String thinking4 = aiMessage4.thinking();
        assertThat(thinking4).isNotBlank();

        String signature4 = aiMessage4.attribute("thinking_signature", String.class);
        assertThat(signature4).isNotBlank();

        assertThat(aiMessage4.toolExecutionRequests()).isEmpty();

        InOrder inOrder4 = inOrder(spyHandler4);
        inOrder4.verify(spyHandler4).get();
        inOrder4.verify(spyHandler4, atLeastOnce()).onPartialThinking(any());
        inOrder4.verify(spyHandler4, atLeastOnce()).onPartialResponse(any());
        inOrder4.verify(spyHandler4).onCompleteResponse(any());
        inOrder4.verifyNoMoreInteractions();
        verifyNoMoreInteractions(spyHandler4);

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

        StreamingChatModel model = AnthropicStreamingChatModel.builder()
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
        TestStreamingChatResponseHandler spyHandler1 = spy(new TestStreamingChatResponseHandler());
        model.chat(List.of(userMessage1), spyHandler1);

        // then
        AiMessage aiMessage1 = spyHandler1.get().aiMessage();
        assertThat(aiMessage1.text()).isNotBlank();
        assertThat(aiMessage1.thinking()).isNull();
        assertThat(aiMessage1.attributes()).hasSize(1);
        List<String> redactedThinkings = aiMessage1.attribute("redacted_thinking", List.class);
        assertThat(redactedThinkings).hasSizeGreaterThanOrEqualTo(1);
        redactedThinkings.forEach(redactedThinking -> assertThat(redactedThinking).isNotBlank());

        InOrder inOrder1 = inOrder(spyHandler1);
        inOrder1.verify(spyHandler1).get();
        inOrder1.verify(spyHandler1, atLeastOnce()).onPartialResponse(any());
        inOrder1.verify(spyHandler1).onCompleteResponse(any());
        inOrder1.verifyNoMoreInteractions();
        verifyNoMoreInteractions(spyHandler1);

        // given
        UserMessage userMessage2 = UserMessage.from("What is the capital of Germany?");

        // when
        TestStreamingChatResponseHandler spyHandler2 = spy(new TestStreamingChatResponseHandler());
        model.chat(List.of(userMessage1, aiMessage1, userMessage2), spyHandler2);

        // then
        AiMessage aiMessage2 = spyHandler2.get().aiMessage();
        assertThat(aiMessage2.text()).contains("Berlin");

        // should send redacted thinking in the follow-up requests
        List<HttpRequest> httpRequests = spyingHttpClient.requests();
        assertThat(httpRequests).hasSize(2);
        assertThat(httpRequests.get(1).body()).contains(jsonify(aiMessage1.text()));
        redactedThinkings.forEach(rt -> assertThat(httpRequests.get(1).body()).contains(jsonify(rt)));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(booleans = false)
    void should_NOT_return_thinking(Boolean returnThinking) {

        // given
        StreamingChatModel model = AnthropicStreamingChatModel.builder()
                .apiKey(System.getenv("ANTHROPIC_API_KEY"))
                .modelName(CLAUDE_3_7_SONNET_20250219)

                .thinkingType("enabled")
                .thinkingBudgetTokens(THINKING_BUDGET_TOKENS)
                .maxTokens(THINKING_BUDGET_TOKENS + 100)
                .returnThinking(returnThinking)

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

        InOrder inOrder = inOrder(spyHandler);
        inOrder.verify(spyHandler).get();
        inOrder.verify(spyHandler, atLeastOnce()).onPartialResponse(any());
        inOrder.verify(spyHandler).onCompleteResponse(any());
        inOrder.verifyNoMoreInteractions();
        verifyNoMoreInteractions(spyHandler);
    }
}
