package dev.langchain4j.memory.chat;

import static dev.langchain4j.data.message.UserMessage.userMessage;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
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

    private static final ToolExecutionRequest TOOL_REQUEST_WITHOUT_ID =
            ToolExecutionRequest.builder().name("toolWithoutId").arguments("{}").build();

    private static final ToolExecutionResultMessage RESULT_A =
            ToolExecutionResultMessage.from(TOOL_REQUEST_A, "resultA");
    private static final ToolExecutionResultMessage RESULT_B =
            ToolExecutionResultMessage.from(TOOL_REQUEST_B, "resultB");
    private static final ToolExecutionResultMessage RESULT_C =
            ToolExecutionResultMessage.from(TOOL_REQUEST_C, "resultC");
    private static final ToolExecutionResultMessage RESULT_WITHOUT_ID =
            ToolExecutionResultMessage.from(TOOL_REQUEST_WITHOUT_ID, "resultWithoutId");

    private static List<ChatMessage> mutableListOf(ChatMessage... messages) {
        List<ChatMessage> list = new LinkedList<>();
        for (ChatMessage message : messages) {
            list.add(message);
        }
        return list;
    }

    @Test
    void should_do_nothing_for_empty_list() {
        List<ChatMessage> messages = mutableListOf();
        ChatMemoryUtils.removeOrphanedToolMessages(messages);
        assertThat(messages).isEmpty();
    }

    @Test
    void should_keep_non_tool_messages_unchanged() {
        // given
        SystemMessage systemMessage = SystemMessage.from("system");
        UserMessage userMessage = userMessage("hello");
        AiMessage aiMessage = AiMessage.from("hi");

        // when
        List<ChatMessage> messages = mutableListOf(systemMessage, userMessage, aiMessage);
        ChatMemoryUtils.removeOrphanedToolMessages(messages);

        // then
        assertThat(messages).containsExactly(systemMessage, userMessage, aiMessage);
    }

    @Test
    void should_remove_trailing_AiMessage_with_tool_calls_and_no_results() {
        // given: app restarts after AiMessage(tool_calls) is committed
        // but before any ToolExecutionResultMessage is written
        UserMessage userMessage = userMessage("hello");
        AiMessage aiMessage = AiMessage.from(TOOL_REQUEST_A);

        // when
        List<ChatMessage> messages = mutableListOf(userMessage, aiMessage);
        ChatMemoryUtils.removeOrphanedToolMessages(messages);

        // then
        assertThat(messages).containsExactly(userMessage);
    }

    @Test
    void should_remove_trailing_AiMessage_with_partial_tool_results() {
        // given: parallel tool calls, app restarts after some results are written but not all
        UserMessage userMessage = userMessage("hello");
        AiMessage aiMessage = AiMessage.from(TOOL_REQUEST_A, TOOL_REQUEST_B, TOOL_REQUEST_C);

        // when
        List<ChatMessage> messages = mutableListOf(userMessage, aiMessage, RESULT_A);
        ChatMemoryUtils.removeOrphanedToolMessages(messages);

        // then
        assertThat(messages).containsExactly(userMessage);
    }

    @Test
    void should_keep_complete_tool_block() {
        // given
        UserMessage userMessage = userMessage("hello");
        AiMessage aiMessage = AiMessage.from(TOOL_REQUEST_A);

        // when
        List<ChatMessage> messages = mutableListOf(userMessage, aiMessage, RESULT_A);
        ChatMemoryUtils.removeOrphanedToolMessages(messages);

        // then
        assertThat(messages).containsExactly(userMessage, aiMessage, RESULT_A);
    }

    @Test
    void should_keep_complete_multi_tool_block() {
        // given
        UserMessage userMessage = userMessage("hello");
        AiMessage aiMessage = AiMessage.from(TOOL_REQUEST_A, TOOL_REQUEST_B);
        AiMessage answer = AiMessage.from("done");

        // when
        List<ChatMessage> messages = mutableListOf(userMessage, aiMessage, RESULT_A, RESULT_B, answer);
        ChatMemoryUtils.removeOrphanedToolMessages(messages);

        // then
        assertThat(messages).containsExactly(userMessage, aiMessage, RESULT_A, RESULT_B, answer);
    }

    @Test
    void should_remove_tool_block_when_result_ids_do_not_match_requests() {
        // given
        UserMessage userMessage = userMessage("hello");
        AiMessage aiMessage = AiMessage.from(TOOL_REQUEST_A, TOOL_REQUEST_B);
        AiMessage answer = AiMessage.from("done");

        // when
        List<ChatMessage> messages = mutableListOf(userMessage, aiMessage, RESULT_A, RESULT_C, answer);
        ChatMemoryUtils.removeOrphanedToolMessages(messages);

        // then
        assertThat(messages).containsExactly(userMessage, answer);
    }

    @Test
    void should_remove_excess_tool_results() {
        // given
        UserMessage userMessage = userMessage("hello");
        AiMessage aiMessage = AiMessage.from(TOOL_REQUEST_A, TOOL_REQUEST_B);
        AiMessage answer = AiMessage.from("done");

        // when
        List<ChatMessage> messages = mutableListOf(userMessage, aiMessage, RESULT_A, RESULT_B, RESULT_C, answer);
        ChatMemoryUtils.removeOrphanedToolMessages(messages);

        // then
        assertThat(messages).containsExactly(userMessage, aiMessage, RESULT_A, RESULT_B, answer);
    }

    @Test
    void should_ignore_result_without_id_when_matching_by_request_ids() {
        // given
        UserMessage userMessage = userMessage("hello");
        AiMessage aiMessage = AiMessage.from(TOOL_REQUEST_A, TOOL_REQUEST_B);
        AiMessage answer = AiMessage.from("done");

        // when
        List<ChatMessage> messages =
                mutableListOf(userMessage, aiMessage, RESULT_A, RESULT_WITHOUT_ID, RESULT_B, answer);
        ChatMemoryUtils.removeOrphanedToolMessages(messages);

        // then
        assertThat(messages).containsExactly(userMessage, aiMessage, RESULT_A, RESULT_B, answer);
    }

    @Test
    void should_keep_tool_results_in_request_order() {
        // given
        UserMessage userMessage = userMessage("hello");
        AiMessage aiMessage = AiMessage.from(TOOL_REQUEST_A, TOOL_REQUEST_B);
        AiMessage answer = AiMessage.from("done");

        // when
        List<ChatMessage> messages = mutableListOf(userMessage, aiMessage, RESULT_B, RESULT_A, answer);
        ChatMemoryUtils.removeOrphanedToolMessages(messages);

        // then
        assertThat(messages).containsExactly(userMessage, aiMessage, RESULT_A, RESULT_B, answer);
    }

    @Test
    void should_remove_tool_block_when_request_ids_are_partially_available() {
        // given
        UserMessage userMessage = userMessage("hello");
        AiMessage aiMessage = AiMessage.from(TOOL_REQUEST_A, TOOL_REQUEST_WITHOUT_ID);
        AiMessage answer = AiMessage.from("done");

        // when
        List<ChatMessage> messages = mutableListOf(userMessage, aiMessage, RESULT_A, RESULT_WITHOUT_ID, answer);
        ChatMemoryUtils.removeOrphanedToolMessages(messages);

        // then
        assertThat(messages).containsExactly(userMessage, answer);
    }

    @Test
    void should_use_count_matching_when_request_ids_are_unavailable() {
        // given
        UserMessage userMessage = userMessage("hello");
        ToolExecutionRequest toolRequestWithoutId1 =
                ToolExecutionRequest.builder().name("tool1").arguments("{}").build();
        ToolExecutionRequest toolRequestWithoutId2 =
                ToolExecutionRequest.builder().name("tool2").arguments("{}").build();
        AiMessage aiMessage = AiMessage.from(toolRequestWithoutId1, toolRequestWithoutId2);
        ToolExecutionResultMessage resultWithoutId1 = ToolExecutionResultMessage.from(toolRequestWithoutId1, "result1");
        ToolExecutionResultMessage resultWithoutId2 = ToolExecutionResultMessage.from(toolRequestWithoutId2, "result2");
        AiMessage answer = AiMessage.from("done");

        // when
        List<ChatMessage> messages = mutableListOf(userMessage, aiMessage, resultWithoutId1, resultWithoutId2, answer);
        ChatMemoryUtils.removeOrphanedToolMessages(messages);

        // then
        assertThat(messages).containsExactly(userMessage, aiMessage, resultWithoutId1, resultWithoutId2, answer);
    }

    @Test
    void should_remove_incomplete_middle_block_followed_by_user_message() {
        // given: multi-round conversation, one tool call round was interrupted,
        // user sent a new message afterward
        UserMessage userMessage1 = userMessage("question 1");
        AiMessage aiMessage = AiMessage.from(TOOL_REQUEST_A, TOOL_REQUEST_B);
        UserMessage userMessage2 = userMessage("question 2");
        AiMessage answer = AiMessage.from("answer");

        // when
        List<ChatMessage> messages = mutableListOf(userMessage1, aiMessage, RESULT_A, userMessage2, answer);
        ChatMemoryUtils.removeOrphanedToolMessages(messages);

        // then
        assertThat(messages).containsExactly(userMessage1, userMessage2, answer);
    }
}
