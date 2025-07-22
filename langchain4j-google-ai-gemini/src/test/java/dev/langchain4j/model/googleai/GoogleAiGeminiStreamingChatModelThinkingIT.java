package dev.langchain4j.model.googleai;

import static dev.langchain4j.JsonTestUtils.jsonify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
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
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;

class GoogleAiGeminiStreamingChatModelThinkingIT {

    private static final String GOOGLE_AI_GEMINI_API_KEY = System.getenv("GOOGLE_AI_GEMINI_API_KEY");

    private final SpyingHttpClient spyingHttpClient = new SpyingHttpClient(JdkHttpClient.builder().build());

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void should_return_thinking(boolean preserveThinking) throws Exception { // TODO name

        // given
        boolean returnThinking = true;

        GeminiThinkingConfig thinkingConfig = GeminiThinkingConfig.builder()
                .includeThoughts(returnThinking) // TODO name discrepancy, everywhere
                .thinkingBudget(20)
                .build();

        StreamingChatModel model = GoogleAiGeminiStreamingChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(spyingHttpClient))
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName("gemini-2.5-flash")
                .thinkingConfig(thinkingConfig)
                .preserveThinking(preserveThinking)
                .logRequestsAndResponses(true)
                .build();

        UserMessage userMessage1 = UserMessage.from("What is the capital of Germany?");

        // when
        TestStreamingChatResponseHandler spyHandler1 = spy(new TestStreamingChatResponseHandler());
        model.chat(List.of(userMessage1), spyHandler1);

        // then
        AiMessage aiMessage1 = spyHandler1.get().aiMessage();
        assertThat(aiMessage1.text()).containsIgnoringCase("Berlin");
        assertThat(aiMessage1.thinking())
                .isNotBlank()
                .isEqualTo(spyHandler1.getThinking());
        assertThat(aiMessage1.metadata()).isEmpty();

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
        TestStreamingChatResponseHandler handler2 = new TestStreamingChatResponseHandler();
        model.chat(List.of(userMessage1, aiMessage1, userMessage2), handler2);

        // then
        AiMessage aiMessage2 = handler2.get().aiMessage();
        assertThat(aiMessage2.text()).containsIgnoringCase("Paris");
        assertThat(aiMessage2.thinking()).isNotBlank();
        assertThat(aiMessage2.metadata()).isEmpty();

        // should preserve thinking in the follow-up request
        List<HttpRequest> httpRequests = spyingHttpClient.requests();
        assertThat(httpRequests).hasSize(2);
        assertThat(httpRequests.get(1).body()).contains(jsonify(aiMessage1.text()));
        if (preserveThinking) {
            assertThat(httpRequests.get(1).body()).contains(jsonify(aiMessage1.thinking()));
        } else {
            assertThat(httpRequests.get(1).body()).doesNotContain(jsonify(aiMessage1.thinking()));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void should_return_thinking_with_tools(boolean preserveThinking) { // TODO name

        // given
        boolean returnThinking = true;

        GeminiThinkingConfig thinkingConfig = GeminiThinkingConfig.builder()
                .includeThoughts(returnThinking) // TODO name discrepancy, everywhere
                .thinkingBudget(20)
                .build();

        ToolSpecification toolSpecification = ToolSpecification.builder()
                .name("getWeather")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("city")
                        .required("city")
                        .build())
                .build();

        StreamingChatModel model = GoogleAiGeminiStreamingChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(spyingHttpClient))
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName("gemini-2.5-flash")
                .thinkingConfig(thinkingConfig)
                .preserveThinking(preserveThinking)
                .defaultRequestParameters(ChatRequestParameters.builder()
                        .toolSpecifications(toolSpecification)
                        .build())
                .logRequestsAndResponses(true)
                .build();

        UserMessage userMessage1 = UserMessage.from("What is the weather in Munich?");

        // when
        TestStreamingChatResponseHandler spyHandler1 = spy(new TestStreamingChatResponseHandler());
        model.chat(List.of(userMessage1), spyHandler1);

        // then
        AiMessage aiMessage1 = spyHandler1.get().aiMessage();
        String thinking1 = aiMessage1.thinking();
        assertThat(thinking1).isNotBlank();
        String signature1 = (String) aiMessage1.metadata().get("thinking_signature");
        assertThat(signature1).isNotBlank();
        assertThat(aiMessage1.toolExecutionRequests()).hasSize(1);
        ToolExecutionRequest toolExecutionRequest1 = aiMessage1.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest1.name()).isEqualTo(toolSpecification.name());
        assertThat(toolExecutionRequest1.arguments()).contains("Munich");

        InOrder inOrder1 = inOrder(spyHandler1);
        inOrder1.verify(spyHandler1, atLeastOnce()).onPartialThinking(any());
        inOrder1.verify(spyHandler1).onCompleteToolCall(any());
        inOrder1.verify(spyHandler1).onCompleteResponse(any());
        inOrder1.verifyNoMoreInteractions();
        verify(spyHandler1).get();
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
        assertThat(aiMessage2.metadata()).isEmpty();
        assertThat(aiMessage2.toolExecutionRequests()).isEmpty();

        InOrder inOrder2 = inOrder(spyHandler2);
        inOrder2.verify(spyHandler2, atLeastOnce()).onPartialResponse(any());
        inOrder2.verify(spyHandler2).onCompleteResponse(any());
        inOrder2.verifyNoMoreInteractions();
        verify(spyHandler2).get();
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
        String signature2 = (String) aiMessage3.metadata().get("thinking_signature");
        assertThat(signature2).isNotBlank();
        assertThat(aiMessage3.toolExecutionRequests()).hasSize(1);
        ToolExecutionRequest toolExecutionRequest2 = aiMessage3.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest2.name()).isEqualTo(toolSpecification.name());
        assertThat(toolExecutionRequest2.arguments()).contains("Paris");

        InOrder inOrder3 = inOrder(spyHandler3);
        inOrder3.verify(spyHandler3, atLeastOnce()).onPartialThinking(any());
        inOrder3.verify(spyHandler3).onCompleteToolCall(any());
        inOrder3.verify(spyHandler3).onCompleteResponse(any());
        inOrder3.verifyNoMoreInteractions();
        verify(spyHandler3).get();
        verifyNoMoreInteractions(spyHandler3);

        // given
        ToolExecutionResultMessage toolResultMessage2 = ToolExecutionResultMessage.from(toolExecutionRequest2, "rainy");

        // when
        TestStreamingChatResponseHandler spyHandler4 = spy(new TestStreamingChatResponseHandler());
        model.chat(List.of(userMessage1, aiMessage1, toolResultMessage1, aiMessage2, userMessage2, aiMessage3, toolResultMessage2), spyHandler4);

        // then
        AiMessage aiMessage4 = spyHandler4.get().aiMessage();
        assertThat(aiMessage4.text()).containsIgnoringCase("rain");
        if (preserveThinking) {
            assertThat(aiMessage4.thinking()).isNull();
            assertThat(aiMessage4.metadata()).isEmpty();
        } else {
            assertThat(aiMessage4.thinking()).isNotBlank();
            assertThat((String) aiMessage4.metadata().get("thinking_signature")).isNotBlank();
        }
        assertThat(aiMessage4.toolExecutionRequests()).isEmpty();

        InOrder inOrder4 = inOrder(spyHandler4);
        if (!preserveThinking) {
            inOrder4.verify(spyHandler4).onPartialThinking(any());
        }
        inOrder4.verify(spyHandler4, atLeastOnce()).onPartialResponse(any());
        inOrder4.verify(spyHandler4).onCompleteResponse(any());
        inOrder4.verifyNoMoreInteractions();
        verify(spyHandler4).get();
        verifyNoMoreInteractions(spyHandler4);

        // should preserve thinking in the follow-up requests
        List<HttpRequest> httpRequests = spyingHttpClient.requests();
        assertThat(httpRequests).hasSize(4);

        if (preserveThinking) {
            assertThat(httpRequests.get(1).body())
                    .contains(jsonify(thinking1))
                    .contains(jsonify(signature1));
        } else {
            assertThat(httpRequests.get(1).body())
                    .doesNotContain(jsonify(thinking1))
                    .doesNotContain(jsonify(signature1));
        }

        if (preserveThinking) {
            assertThat(httpRequests.get(3).body())
                    .contains(jsonify(thinking2))
                    .contains(jsonify(signature2));
        } else {
            assertThat(httpRequests.get(3).body())
                    .doesNotContain(jsonify(thinking2))
                    .doesNotContain(jsonify(signature2));
        }
    }


    @Test
    void should_NOT_return_thinking() { // TODO name TODO remove?

        // given
        boolean returnThinking = false;

        GeminiThinkingConfig thinkingConfig = GeminiThinkingConfig.builder()
                .includeThoughts(returnThinking) // TODO good name for generic?
                .thinkingBudget(20)
                .build();

        StreamingChatModel model = GoogleAiGeminiStreamingChatModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName("gemini-2.5-flash")
                .thinkingConfig(thinkingConfig)
                .logRequestsAndResponses(true)
                .build();

        String userMessage = "What is the capital of Germany?";

        // when
        TestStreamingChatResponseHandler spyHandler = spy(new TestStreamingChatResponseHandler());
        model.chat(userMessage, spyHandler);

        // then
        AiMessage aiMessage = spyHandler.get().aiMessage();
        assertThat(aiMessage.text()).contains("Berlin");
        assertThat(aiMessage.thinking()).isNull();
        assertThat(aiMessage.metadata()).isEmpty();

        InOrder inOrder = inOrder(spyHandler);
        inOrder.verify(spyHandler, atLeastOnce()).onPartialResponse(any());
        inOrder.verify(spyHandler).onCompleteResponse(any());
        inOrder.verifyNoMoreInteractions();
        verify(spyHandler).get();
        verifyNoMoreInteractions(spyHandler);
    }

    // TODO other tests?

    @AfterEach
    void afterEach() throws InterruptedException {
        String ciDelaySeconds = System.getenv("CI_DELAY_SECONDS_GOOGLE_AI_GEMINI");
        if (ciDelaySeconds != null) {
            Thread.sleep(Integer.parseInt(ciDelaySeconds) * 1000L);
        }
    }
}
