package dev.langchain4j.service;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.test.mock.MockChatModel;
import dev.langchain4j.test.mock.MockStreamingChatModel;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * {@code maxToolCallingRoundTrips(n)} limits the number of model responses that contain tool calls. The final answer
 * contains no tool call, so it must not count: with a limit of n, n tool rounds followed by an answer succeed, in
 * every mode.
 */
class AiServicesMaxToolCallingRoundTripsTest {

    private static final ToolExecutionRequest GET_TEMPERATURE = ToolExecutionRequest.builder()
            .id("call-1")
            .name("temperature")
            .arguments("{}")
            .build();

    static class Weather {

        final AtomicInteger invocations = new AtomicInteger();

        @Tool
        String temperature() {
            invocations.incrementAndGet();
            return "42";
        }
    }

    interface SyncAssistant {

        String chat(String message);
    }

    interface AsyncAssistant {

        CompletableFuture<String> chat(String message);
    }

    interface StreamingAssistant {

        TokenStream chat(String message);
    }

    @Test
    void sync_service_should_allow_as_many_tool_rounds_as_the_limit_before_the_answer() {

        Weather weather = new Weather();
        SyncAssistant assistant = AiServices.builder(SyncAssistant.class)
                .chatModel(
                        MockChatModel.thatResponds(AiMessage.from(GET_TEMPERATURE), AiMessage.from("It is 42 degrees")))
                .tools(weather)
                .maxToolCallingRoundTrips(1)
                .build();

        assertThat(assistant.chat("What is the temperature?")).isEqualTo("It is 42 degrees");
        assertThat(weather.invocations).hasValue(1);
    }

    @Test
    void sync_service_should_allow_several_tool_rounds_up_to_the_limit() {

        Weather weather = new Weather();
        SyncAssistant assistant = AiServices.builder(SyncAssistant.class)
                .chatModel(MockChatModel.thatResponds(
                        AiMessage.from(GET_TEMPERATURE),
                        AiMessage.from(GET_TEMPERATURE),
                        AiMessage.from("It is still 42 degrees")))
                .tools(weather)
                .maxToolCallingRoundTrips(2)
                .build();

        assertThat(assistant.chat("What is the temperature?")).isEqualTo("It is still 42 degrees");
        assertThat(weather.invocations).hasValue(2);
    }

    @Test
    void sync_service_should_fail_without_running_tools_beyond_the_limit() {

        Weather weather = new Weather();
        SyncAssistant assistant = AiServices.builder(SyncAssistant.class)
                .chatModel(MockChatModel.thatResponds(
                        AiMessage.from(GET_TEMPERATURE), AiMessage.from(GET_TEMPERATURE), AiMessage.from("unused")))
                .tools(weather)
                .maxToolCallingRoundTrips(1)
                .build();

        assertThatThrownBy(() -> assistant.chat("What is the temperature?"))
                .hasMessageContaining("exceeded 1 tool calling round trips");
        assertThat(weather.invocations).hasValue(1);
    }

    @Test
    void async_service_should_allow_as_many_tool_rounds_as_the_limit_before_the_answer() throws Exception {

        Weather weather = new Weather();
        AsyncAssistant assistant = AiServices.builder(AsyncAssistant.class)
                .chatModel(
                        MockChatModel.thatResponds(AiMessage.from(GET_TEMPERATURE), AiMessage.from("It is 42 degrees")))
                .tools(weather)
                .maxToolCallingRoundTrips(1)
                .build();

        assertThat(assistant.chat("What is the temperature?").get(10, SECONDS)).isEqualTo("It is 42 degrees");
        assertThat(weather.invocations).hasValue(1);
    }

    @Test
    void async_service_should_fail_without_running_tools_beyond_the_limit() {

        Weather weather = new Weather();
        AsyncAssistant assistant = AiServices.builder(AsyncAssistant.class)
                .chatModel(MockChatModel.thatResponds(
                        AiMessage.from(GET_TEMPERATURE), AiMessage.from(GET_TEMPERATURE), AiMessage.from("unused")))
                .tools(weather)
                .maxToolCallingRoundTrips(1)
                .build();

        CompletableFuture<String> answer = assistant.chat("What is the temperature?");

        assertThatThrownBy(() -> answer.get(10, SECONDS))
                .isInstanceOf(ExecutionException.class)
                .rootCause()
                .hasMessageContaining("exceeded 1 tool calling round trips");
        assertThat(weather.invocations).hasValue(1);
    }

    @Test
    void streaming_service_should_allow_as_many_tool_rounds_as_the_limit_before_the_answer() throws Exception {

        Weather weather = new Weather();
        StreamingAssistant assistant = AiServices.builder(StreamingAssistant.class)
                .streamingChatModel(MockStreamingChatModel.thatResponds(
                        AiMessage.from(GET_TEMPERATURE), AiMessage.from("It is 42 degrees")))
                .tools(weather)
                .maxToolCallingRoundTrips(1)
                .build();

        CompletableFuture<ChatResponse> completed = new CompletableFuture<>();
        assistant
                .chat("What is the temperature?")
                .onPartialResponse(ignored -> {})
                .onCompleteResponse(completed::complete)
                .onError(completed::completeExceptionally)
                .start();

        assertThat(completed.get(10, SECONDS).aiMessage().text()).isEqualTo("It is 42 degrees");
        assertThat(weather.invocations).hasValue(1);
    }
}
