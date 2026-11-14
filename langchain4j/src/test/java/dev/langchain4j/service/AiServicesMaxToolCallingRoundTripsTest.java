package dev.langchain4j.service;

import static dev.langchain4j.data.message.ChatMessageType.AI;
import static dev.langchain4j.data.message.ChatMessageType.TOOL_EXECUTION_RESULT;
import static dev.langchain4j.data.message.ChatMessageType.USER;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AiServicesMaxToolCallingRoundTripsTest {

    private static final AiMessage TOOL_CALL = AiMessage.from(ToolExecutionRequest.builder()
            .id("1")
            .name("currentTemperature")
            .arguments("{}")
            .build());

    private static final AiMessage ANSWER = AiMessage.from("It is 42 degrees");

    interface Assistant {

        String chat(String userMessage);
    }

    interface AsyncAssistant {

        CompletableFuture<String> chat(String userMessage);
    }

    static class Tools {

        final AtomicInteger invocations = new AtomicInteger();

        @Tool
        String currentTemperature() {
            invocations.incrementAndGet();
            return "42";
        }
    }

    @Test
    void should_return_answer_when_max_tool_calling_round_trips_is_reached() {

        // given: 2 LLM responses with tool calls, then the answer
        Tools tools = new Tools();
        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(ChatModelMock.thatAlwaysResponds(TOOL_CALL, TOOL_CALL, ANSWER))
                .tools(tools)
                .maxToolCallingRoundTrips(2)
                .build();

        // when
        String answer = assistant.chat("What is the temperature?");

        // then
        assertThat(answer).isEqualTo("It is 42 degrees");
        assertThat(tools.invocations).hasValue(2);
    }

    @Test
    void should_fail_when_max_tool_calling_round_trips_is_exceeded() {

        // given: 2 LLM responses with tool calls, but only 1 round trip is allowed
        Tools tools = new Tools();
        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);
        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(ChatModelMock.thatAlwaysResponds(TOOL_CALL, TOOL_CALL, ANSWER))
                .chatMemory(chatMemory)
                .tools(tools)
                .maxToolCallingRoundTrips(1)
                .build();

        // when-then
        assertThatThrownBy(() -> assistant.chat("What is the temperature?"))
                .hasMessage("Something is wrong, exceeded 1 tool calling round trips (maxToolCallingRoundTrips)");
        assertThat(tools.invocations).hasValue(1);
        // the rejected response is not added to the chat memory
        assertThat(chatMemory.messages())
                .extracting(ChatMessage::type)
                .containsExactly(USER, AI, TOOL_EXECUTION_RESULT);
    }

    @Test
    void should_return_answer_without_tool_calls_when_max_tool_calling_round_trips_is_zero() {

        // given: an answer without tool calls, and no round trip allowed
        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(ChatModelMock.thatAlwaysResponds(ANSWER))
                .tools(new Tools())
                .maxToolCallingRoundTrips(0)
                .build();

        // when
        String answer = assistant.chat("What is the temperature?");

        // then
        assertThat(answer).isEqualTo("It is 42 degrees");
    }

    @Test
    void should_complete_future_with_answer_when_max_tool_calling_round_trips_is_reached() throws Exception {

        // given: 2 LLM responses with tool calls, then the answer
        Tools tools = new Tools();
        AsyncAssistant assistant = AiServices.builder(AsyncAssistant.class)
                .chatModel(ChatModelMock.thatAlwaysResponds(TOOL_CALL, TOOL_CALL, ANSWER))
                .tools(tools)
                .maxToolCallingRoundTrips(2)
                .build();

        // when
        String answer = assistant.chat("What is the temperature?").get(10, SECONDS);

        // then
        assertThat(answer).isEqualTo("It is 42 degrees");
        assertThat(tools.invocations).hasValue(2);
    }

    @Test
    void should_fail_future_when_max_tool_calling_round_trips_is_exceeded() {

        // given: 2 LLM responses with tool calls, but only 1 round trip is allowed
        Tools tools = new Tools();
        AsyncAssistant assistant = AiServices.builder(AsyncAssistant.class)
                .chatModel(ChatModelMock.thatAlwaysResponds(TOOL_CALL, TOOL_CALL, ANSWER))
                .tools(tools)
                .maxToolCallingRoundTrips(1)
                .build();

        // when-then
        CompletableFuture<String> future = assistant.chat("What is the temperature?");
        assertThatThrownBy(() -> future.get(10, SECONDS))
                .isInstanceOf(ExecutionException.class)
                .rootCause()
                .hasMessage("Something is wrong, exceeded 1 tool calling round trips (maxToolCallingRoundTrips)");
        assertThat(tools.invocations).hasValue(1);
    }
}
