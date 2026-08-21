package dev.langchain4j.model.mistralai;

import static dev.langchain4j.data.message.UserMessage.userMessage;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.SpyingHttpClient;
import dev.langchain4j.http.client.jdk.JdkHttpClient;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.TestStreamingChatResponseHandler;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

@EnabledIfEnvironmentVariable(named = "MISTRAL_AI_API_KEY", matches = ".+")
class MistralAiStreamingChatModelThinkingIT {

    @ParameterizedTest
    @EnumSource(
            value = MistralAiChatModelName.class,
            names = {"MAGISTRAL_SMALL_LATEST", "MAGISTRAL_MEDIUM_LATEST"})
    void should_answer_correctly_with_reasoning_model(MistralAiChatModelName modelName) {
        // given
        StreamingChatModel model = MistralAiStreamingChatModel.builder()
                .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
                .modelName(modelName)
                .logRequests(true)
                .logResponses(true)
                .build();

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat("What is the capital of Germany? Answer in one word.", handler);
        AiMessage aiMessage = handler.get().aiMessage();

        // then
        assertThat(aiMessage.text()).containsIgnoringCase("Berlin");
    }

    @Test
    void should_send_thinking_in_follow_up_request_when_sendThinking_is_true() {
        // given
        SpyingHttpClient spyingHttpClient =
                new SpyingHttpClient(JdkHttpClient.builder().build());

        StreamingChatModel model = MistralAiStreamingChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(spyingHttpClient))
                .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
                .modelName(MistralAiChatModelName.MAGISTRAL_MEDIUM_LATEST)
                .sendThinking(true)
                .build();

        // Simulate a previous AI response with thinking
        AiMessage previousAiMessage = AiMessage.builder()
                .text("The answer is 42.")
                .thinking("Let me reason through this problem step by step...")
                .build();

        // when - send follow-up with previous AI message that has thinking
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat(
                List.of(userMessage("What is 6 times 7?"), previousAiMessage, userMessage("Are you sure?")), handler);
        handler.get();

        // then - verify the request body contains the thinking
        HttpRequest request = spyingHttpClient.requests().get(0);
        assertThat(request.body())
                .contains("Let me reason through this problem step by step...")
                .contains("The answer is 42.");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(booleans = false)
    void should_NOT_send_thinking_when_sendThinking_is_false_or_not_set(Boolean sendThinking) {
        // given
        SpyingHttpClient spyingHttpClient =
                new SpyingHttpClient(JdkHttpClient.builder().build());

        StreamingChatModel model = MistralAiStreamingChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(spyingHttpClient))
                .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
                .modelName(MistralAiChatModelName.MAGISTRAL_MEDIUM_LATEST)
                .sendThinking(sendThinking)
                .build();

        // Simulate a previous AI response with thinking
        AiMessage previousAiMessage = AiMessage.builder()
                .text("The answer is 42.")
                .thinking("Let me reason through this problem step by step...")
                .build();

        // when - send follow-up with previous AI message that has thinking
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat(
                List.of(userMessage("What is 6 times 7?"), previousAiMessage, userMessage("Are you sure?")), handler);
        handler.get();

        // then - verify the request body does NOT contain the thinking, but contains the text
        HttpRequest request = spyingHttpClient.requests().get(0);
        assertThat(request.body())
                .doesNotContain("Let me reason through this problem step by step...")
                .contains("The answer is 42.");
    }

    @Test
    void should_send_thinking_with_tool_calls_when_sendThinking_is_true() {
        // given
        SpyingHttpClient spyingHttpClient =
                new SpyingHttpClient(JdkHttpClient.builder().build());

        StreamingChatModel model = MistralAiStreamingChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(spyingHttpClient))
                .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
                .modelName(MistralAiChatModelName.MAGISTRAL_MEDIUM_LATEST)
                .sendThinking(true)
                .build();

        // Simulate a previous AI response with thinking and tool call
        ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                .id("ggLXfzi8o")
                .name("getWeather")
                .arguments("{\"city\": \"Munich\"}")
                .build();

        AiMessage aiMessageWithToolCall = AiMessage.builder()
                .thinking("I need to check the weather in Munich to answer this question.")
                .toolExecutionRequests(singletonList(toolExecutionRequest))
                .build();

        ToolExecutionResultMessage toolResult =
                ToolExecutionResultMessage.from(toolExecutionRequest, "Munich: 22°C, sunny");

        // when - send conversation with AI message that has thinking + tool call
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat(List.of(userMessage("What's the weather in Munich?"), aiMessageWithToolCall, toolResult), handler);
        handler.get();

        // then - verify the request body contains the thinking along with tool call
        HttpRequest request = spyingHttpClient.requests().get(0);
        assertThat(request.body())
                .contains("I need to check the weather in Munich to answer this question.")
                .contains("getWeather")
                .contains("ggLXfzi8o");
    }

    @Test
    void should_send_thinking_in_multi_turn_conversation_when_sendThinking_is_true() {
        // given
        SpyingHttpClient spyingHttpClient =
                new SpyingHttpClient(JdkHttpClient.builder().build());

        StreamingChatModel model = MistralAiStreamingChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(spyingHttpClient))
                .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
                .modelName(MistralAiChatModelName.MAGISTRAL_MEDIUM_LATEST)
                .sendThinking(true)
                .build();

        // when - send multi-turn conversation
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat(
                List.of(
                        userMessage("What is 6 times 7?"),
                        AiMessage.builder()
                                .text("6 times 7 equals 42.")
                                .thinking("First thinking: Let me calculate 6 * 7...")
                                .build(),
                        userMessage("Are you sure?"),
                        AiMessage.builder()
                                .text("Yes, I am sure. 6 * 7 = 42.")
                                .thinking("Second thinking: The user wants confirmation...")
                                .build(),
                        userMessage("Thank you!")),
                handler);
        handler.get();

        // then - verify the request body contains thinking from both AI messages
        HttpRequest request = spyingHttpClient.requests().get(0);
        assertThat(request.body())
                .contains("First thinking: Let me calculate 6 * 7...")
                .contains("6 times 7 equals 42.")
                .contains("Second thinking: The user wants confirmation...")
                .contains("Yes, I am sure. 6 * 7 = 42.");
    }
}
