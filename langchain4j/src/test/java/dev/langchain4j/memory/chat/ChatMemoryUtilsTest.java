package dev.langchain4j.memory.chat;

import static dev.langchain4j.data.message.UserMessage.userMessage;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import java.util.LinkedList;
import java.util.List;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

class ChatMemoryUtilsTest implements WithAssertions {

    private static final ToolExecutionRequest TOOL_REQUEST_A =
            ToolExecutionRequest.builder().id("1").name("toolA").arguments("{}").build();

    private static final ToolExecutionRequest TOOL_REQUEST_B =
            ToolExecutionRequest.builder().id("2").name("toolB").arguments("{}").build();

    private static final ToolExecutionRequest TOOL_REQUEST_C =
            ToolExecutionRequest.builder().id("3").name("toolC").arguments("{}").build();

    private static final ToolExecutionResultMessage RESULT_A =
            ToolExecutionResultMessage.from(TOOL_REQUEST_A, "resultA");
    private static final ToolExecutionResultMessage RESULT_B =
            ToolExecutionResultMessage.from(TOOL_REQUEST_B, "resultB");
    private static final ToolExecutionResultMessage RESULT_C =
            ToolExecutionResultMessage.from(TOOL_REQUEST_C, "resultC");

    private static List<ChatMessage> mutableListOf(ChatMessage... messages) {
        return new LinkedList<>(List.of(messages));
    }

    @Test
    void should_remove_interrupted_tool_execution_from_middle_of_history() {
        // given: a parallel tool execution was interrupted before any result
        UserMessage firstUserMessage = userMessage("question 1");
        AiMessage interruptedAiMessage = AiMessage.from(TOOL_REQUEST_A, TOOL_REQUEST_B);
        UserMessage secondUserMessage = userMessage("question 2");
        AiMessage answer = AiMessage.from("answer");
        List<ChatMessage> messages = mutableListOf(firstUserMessage, interruptedAiMessage, secondUserMessage, answer);

        // when
        ChatMemoryUtils.removeInterruptedToolExecutions(messages);

        // then
        assertThat(messages).containsExactly(firstUserMessage, secondUserMessage, answer);
    }

    @Test
    void should_preserve_non_interrupted_tool_messages_in_original_order() {
        // given: standalone and excess results are outside interrupted-execution recovery
        ToolExecutionResultMessage standaloneResult = ToolExecutionResultMessage.from(TOOL_REQUEST_C, "standalone");
        ToolExecutionResultMessage excessResult = ToolExecutionResultMessage.from(TOOL_REQUEST_C, "excess");
        UserMessage firstUserMessage = userMessage("hello");
        AiMessage completeAiMessage = AiMessage.from(TOOL_REQUEST_A, TOOL_REQUEST_B);
        UserMessage secondUserMessage = userMessage("next");
        AiMessage aiMessageWithExcessResult = AiMessage.from(TOOL_REQUEST_A, TOOL_REQUEST_B);
        AiMessage answer = AiMessage.from("done");
        List<ChatMessage> messages = mutableListOf(
                standaloneResult,
                firstUserMessage,
                completeAiMessage,
                RESULT_B,
                RESULT_A,
                secondUserMessage,
                aiMessageWithExcessResult,
                RESULT_A,
                RESULT_B,
                excessResult,
                answer);

        // when
        ChatMemoryUtils.removeInterruptedToolExecutions(messages);

        // then
        assertThat(messages)
                .containsExactly(
                        standaloneResult,
                        firstUserMessage,
                        completeAiMessage,
                        RESULT_B,
                        RESULT_A,
                        secondUserMessage,
                        aiMessageWithExcessResult,
                        RESULT_A,
                        RESULT_B,
                        excessResult,
                        answer);
    }

    @Test
    void should_remove_tool_execution_when_result_ids_do_not_match_requests() {
        // given
        UserMessage userMessage = userMessage("hello");
        AiMessage aiMessage = AiMessage.from(TOOL_REQUEST_A, TOOL_REQUEST_B);
        AiMessage answer = AiMessage.from("done");
        List<ChatMessage> messages = mutableListOf(userMessage, aiMessage, RESULT_A, RESULT_C, answer);

        // when
        ChatMemoryUtils.removeInterruptedToolExecutions(messages);

        // then
        assertThat(messages).containsExactly(userMessage, answer);
    }

    @Test
    void should_use_result_count_when_ids_are_unavailable() {
        // given
        ToolExecutionRequest firstRequest =
                ToolExecutionRequest.builder().name("tool1").arguments("{}").build();
        ToolExecutionRequest secondRequest =
                ToolExecutionRequest.builder().name("tool2").arguments("{}").build();
        AiMessage aiMessage = AiMessage.from(firstRequest, secondRequest);
        ToolExecutionResultMessage firstResult = ToolExecutionResultMessage.from(firstRequest, "result1");
        ToolExecutionResultMessage secondResult = ToolExecutionResultMessage.from(secondRequest, "result2");
        List<ChatMessage> messages = mutableListOf(aiMessage, firstResult, secondResult);

        // when
        ChatMemoryUtils.removeInterruptedToolExecutions(messages);

        // then
        assertThat(messages).containsExactly(aiMessage, firstResult, secondResult);
    }
}
