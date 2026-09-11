package dev.langchain4j.memory.chat;

import static dev.langchain4j.data.message.AiMessage.aiMessage;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import java.util.List;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

class ToolAwareMessageSanitizerTest implements WithAssertions {

    private static ToolExecutionRequest toolExecutionRequest(String id) {
        return toolExecutionRequest(id, "calculator");
    }

    private static ToolExecutionRequest toolExecutionRequest(String id, String name) {
        return ToolExecutionRequest.builder()
                .id(id)
                .name(name)
                .arguments("{ \"a\": 2, \"b\": 2 }")
                .build();
    }

    @Test
    void should_return_same_instance_when_list_is_empty() {
        List<ChatMessage> messages = emptyList();
        assertThat(ToolAwareMessageSanitizer.sanitize(messages)).isSameAs(messages);
    }

    @Test
    void should_return_same_instance_when_history_is_fully_valid() {
        UserMessage userMessage = userMessage("How much is 2+2?");
        AiMessage aiMessage = AiMessage.from(toolExecutionRequest("1"));
        ToolExecutionResultMessage result = ToolExecutionResultMessage.from(toolExecutionRequest("1"), "4");

        List<ChatMessage> messages = asList(userMessage, aiMessage, result);

        assertThat(ToolAwareMessageSanitizer.sanitize(messages)).isSameAs(messages);
    }

    @Test
    void should_drop_orphaned_result_at_the_head_of_the_history() {
        ToolExecutionResultMessage orphan = ToolExecutionResultMessage.from(toolExecutionRequest("1"), "4");
        AiMessage followUp = aiMessage("2 + 2 = 4");

        List<ChatMessage> sanitized = ToolAwareMessageSanitizer.sanitize(asList(orphan, followUp));

        assertThat(sanitized).containsExactly(followUp);
    }

    @Test
    void should_drop_orphaned_result_appearing_before_any_AiMessage() {
        ToolExecutionResultMessage orphan = ToolExecutionResultMessage.from(toolExecutionRequest("1"), "4");
        UserMessage userMessage = userMessage("hello");
        AiMessage aiMessage = aiMessage("hi there");

        List<ChatMessage> sanitized = ToolAwareMessageSanitizer.sanitize(asList(orphan, userMessage, aiMessage));

        assertThat(sanitized).containsExactly(userMessage, aiMessage);
    }

    @Test
    void should_drop_only_the_orphaned_result_in_the_middle_of_the_history() {
        AiMessage aiMessage1 = AiMessage.from(toolExecutionRequest("1"));
        ToolExecutionResultMessage result1 = ToolExecutionResultMessage.from(toolExecutionRequest("1"), "4");
        // "2" has no matching AiMessage still present in the list
        ToolExecutionResultMessage orphanResult2 = ToolExecutionResultMessage.from(toolExecutionRequest("2"), "9");
        UserMessage userMessage = userMessage("thanks");

        List<ChatMessage> sanitized =
                ToolAwareMessageSanitizer.sanitize(asList(aiMessage1, result1, orphanResult2, userMessage));

        assertThat(sanitized).containsExactly(aiMessage1, result1, userMessage);
    }

    @Test
    void should_drop_multiple_orphaned_results() {
        ToolExecutionResultMessage orphan1 = ToolExecutionResultMessage.from(toolExecutionRequest("1"), "4");
        ToolExecutionResultMessage orphan2 = ToolExecutionResultMessage.from(toolExecutionRequest("2"), "9");
        UserMessage userMessage = userMessage("hi");

        List<ChatMessage> sanitized = ToolAwareMessageSanitizer.sanitize(asList(orphan1, orphan2, userMessage));

        assertThat(sanitized).containsExactly(userMessage);
    }

    @Test
    void should_not_let_a_result_answer_an_AiMessage_whose_window_has_already_closed() {
        // "1" is only satisfied positionally by the AiMessage that immediately precedes it;
        // once a UserMessage intervenes, a later result with the same id must not be treated as covering it
        AiMessage aiMessage = AiMessage.from(toolExecutionRequest("1"));
        UserMessage interveningMessage = userMessage("still waiting?");
        ToolExecutionResultMessage lateResult = ToolExecutionResultMessage.from(toolExecutionRequest("1"), "4");

        List<ChatMessage> sanitized =
                ToolAwareMessageSanitizer.sanitize(asList(aiMessage, interveningMessage, lateResult));

        // the AiMessage's only tool call is unanswered and it has no text, so it is dropped entirely;
        // the late result no longer matches anything open and is dropped as orphaned
        assertThat(sanitized).containsExactly(interveningMessage);
    }

    @Test
    void should_drop_AiMessage_entirely_when_all_tool_calls_are_unanswered_and_it_has_no_text() {
        AiMessage aiMessage = AiMessage.from(toolExecutionRequest("1"), toolExecutionRequest("2"));
        UserMessage nextMessage = userMessage("hello?");

        List<ChatMessage> sanitized = ToolAwareMessageSanitizer.sanitize(asList(aiMessage, nextMessage));

        assertThat(sanitized).containsExactly(nextMessage);
    }

    @Test
    void should_strip_all_tool_calls_but_keep_the_AiMessage_when_it_has_text() {
        AiMessage aiMessage = AiMessage.builder()
                .text("let me check that")
                .toolExecutionRequests(List.of(toolExecutionRequest("1"), toolExecutionRequest("2")))
                .build();
        UserMessage nextMessage = userMessage("hello?");

        List<ChatMessage> sanitized = ToolAwareMessageSanitizer.sanitize(asList(aiMessage, nextMessage));

        assertThat(sanitized).hasSize(2);
        AiMessage repaired = (AiMessage) sanitized.get(0);
        assertThat(repaired.text()).isEqualTo("let me check that");
        assertThat(repaired.hasToolExecutionRequests()).isFalse();
        assertThat(sanitized.get(1)).isEqualTo(nextMessage);
    }

    @Test
    void should_strip_only_the_unanswered_tool_calls_when_partially_answered() {
        ToolExecutionRequest request1 = toolExecutionRequest("1", "weather");
        ToolExecutionRequest request2 = toolExecutionRequest("2", "calculator");
        ToolExecutionRequest request3 = toolExecutionRequest("3", "search");
        AiMessage aiMessage = AiMessage.builder()
                .text("working on it")
                .toolExecutionRequests(List.of(request1, request2, request3))
                .build();
        // only request "2" is answered
        ToolExecutionResultMessage result2 = ToolExecutionResultMessage.from(request2, "4");
        // a subsequent message proves the window is closed: requests "1" and "3" are never coming
        UserMessage nextTurn = userMessage("thanks");

        List<ChatMessage> sanitized = ToolAwareMessageSanitizer.sanitize(asList(aiMessage, result2, nextTurn));

        assertThat(sanitized).hasSize(3);
        AiMessage repaired = (AiMessage) sanitized.get(0);
        assertThat(repaired.text()).isEqualTo("working on it");
        assertThat(repaired.toolExecutionRequests()).containsExactly(request2);
        assertThat(sanitized.get(1)).isEqualTo(result2);
        assertThat(sanitized.get(2)).isEqualTo(nextTurn);
    }

    @Test
    void should_keep_answered_calls_and_their_results_and_drop_only_unanswered_calls() {
        ToolExecutionRequest request1 = toolExecutionRequest("1");
        ToolExecutionRequest request2 = toolExecutionRequest("2");
        AiMessage aiMessage = AiMessage.from(request1, request2);
        ToolExecutionResultMessage result1 = ToolExecutionResultMessage.from(request1, "4");
        // request2 has no result at all, and a subsequent message proves it is never coming
        UserMessage nextTurn = userMessage("thanks");

        List<ChatMessage> sanitized = ToolAwareMessageSanitizer.sanitize(asList(aiMessage, result1, nextTurn));

        assertThat(sanitized).hasSize(3);
        AiMessage repaired = (AiMessage) sanitized.get(0);
        assertThat(repaired.toolExecutionRequests()).containsExactly(request1);
        assertThat(sanitized.get(1)).isEqualTo(result1);
        assertThat(sanitized.get(2)).isEqualTo(nextTurn);
    }

    @Test
    void should_not_touch_an_AiMessage_with_fully_unanswered_tool_calls_when_it_is_the_last_message() {
        // an AiMessage with unanswered tool calls in tail position is indistinguishable from one that is
        // still awaiting its result(s) on a subsequent turn (e.g. concurrent tool execution, or the result
        // is about to be added next) - it must be left completely untouched, never repaired or dropped
        UserMessage userMessage = userMessage("what's 2+2?");
        AiMessage pending = AiMessage.from(toolExecutionRequest("1"), toolExecutionRequest("2"));

        List<ChatMessage> messages = asList(userMessage, pending);

        assertThat(ToolAwareMessageSanitizer.sanitize(messages)).isSameAs(messages);
    }

    @Test
    void should_not_touch_an_AiMessage_with_partially_unanswered_tool_calls_when_it_is_the_last_message() {
        // same reasoning as the fully-unanswered case: a partially answered AiMessage in tail position is
        // exactly what concurrent/parallel tool execution looks like mid-flight, not corruption
        ToolExecutionRequest request1 = toolExecutionRequest("1");
        ToolExecutionRequest request2 = toolExecutionRequest("2");
        AiMessage aiMessage = AiMessage.from(request1, request2);
        ToolExecutionResultMessage result1 = ToolExecutionResultMessage.from(request1, "4");
        // request2's result has not arrived yet, and nothing follows to prove it never will

        List<ChatMessage> messages = asList(aiMessage, result1);

        assertThat(ToolAwareMessageSanitizer.sanitize(messages)).isSameAs(messages);
    }

    @Test
    void should_still_drop_a_genuinely_orphaned_result_that_trails_a_pending_AiMessage() {
        // even though the AiMessage itself is left alone (tail position, still pending), a result in its
        // window that cannot possibly belong to it is unconditionally orphaned and must still be dropped
        AiMessage pending = AiMessage.from(toolExecutionRequest("1"));
        ToolExecutionResultMessage orphan = ToolExecutionResultMessage.from(toolExecutionRequest("99"), "9");

        List<ChatMessage> sanitized = ToolAwareMessageSanitizer.sanitize(asList(pending, orphan));

        assertThat(sanitized).containsExactly(pending);
    }

    @Test
    void should_never_treat_a_null_id_result_as_orphaned() {
        ToolExecutionResultMessage nullIdResult = ToolExecutionResultMessage.from(null, "calculator", "4");
        UserMessage userMessage = userMessage("hi");

        List<ChatMessage> messages = asList(nullIdResult, userMessage);

        assertThat(ToolAwareMessageSanitizer.sanitize(messages)).isSameAs(messages);
    }

    @Test
    void should_never_treat_a_null_id_result_as_orphaned_even_when_other_repairs_happen() {
        ToolExecutionResultMessage danglingOrphan = ToolExecutionResultMessage.from(toolExecutionRequest("1"), "4");
        ToolExecutionResultMessage nullIdResult = ToolExecutionResultMessage.from(null, "calculator", "9");
        UserMessage userMessage = userMessage("hi");

        List<ChatMessage> sanitized =
                ToolAwareMessageSanitizer.sanitize(asList(danglingOrphan, nullIdResult, userMessage));

        assertThat(sanitized).containsExactly(nullIdResult, userMessage);
    }

    @Test
    void should_not_let_a_null_id_request_block_sanitization_of_its_sibling_calls() {
        // request "null" cannot be verified either way and must not prevent request "1" from being
        // recognised as answered, nor from being stripped when it is unanswered
        ToolExecutionRequest nullIdRequest = toolExecutionRequest(null);
        ToolExecutionRequest request1 = toolExecutionRequest("1");
        AiMessage answered = AiMessage.from(nullIdRequest, request1);
        ToolExecutionResultMessage result1 = ToolExecutionResultMessage.from(request1, "4");

        List<ChatMessage> sanitizedWhenAnswered = ToolAwareMessageSanitizer.sanitize(asList(answered, result1));
        assertThat(sanitizedWhenAnswered).containsExactly(answered, result1);

        AiMessage unanswered = AiMessage.from(nullIdRequest, request1);
        UserMessage nextMessage = userMessage("moving on");

        List<ChatMessage> sanitizedWhenUnanswered = ToolAwareMessageSanitizer.sanitize(asList(unanswered, nextMessage));

        assertThat(sanitizedWhenUnanswered).hasSize(2);
        AiMessage repaired = (AiMessage) sanitizedWhenUnanswered.get(0);
        // the null-id call survives untouched, only the unanswered "1" call is stripped
        assertThat(repaired.toolExecutionRequests()).containsExactly(nullIdRequest);
        assertThat(sanitizedWhenUnanswered.get(1)).isEqualTo(nextMessage);
    }

    @Test
    void should_self_heal_the_original_issue_3133_shape_of_orphans_at_the_head_of_a_truncated_history() {
        // simulates a persistence layer that truncated the head of the history, leaving results
        // whose parent AiMessage(s) no longer exist
        ToolExecutionResultMessage orphan1 = ToolExecutionResultMessage.from(toolExecutionRequest("18"), "result18");
        ToolExecutionResultMessage orphan2 = ToolExecutionResultMessage.from(toolExecutionRequest("19"), "result19");
        AiMessage nextAiMessage = AiMessage.from(toolExecutionRequest("20"));
        ToolExecutionResultMessage nextResult = ToolExecutionResultMessage.from(toolExecutionRequest("20"), "r20");

        List<ChatMessage> sanitized =
                ToolAwareMessageSanitizer.sanitize(asList(orphan1, orphan2, nextAiMessage, nextResult));

        assertThat(sanitized).containsExactly(nextAiMessage, nextResult);
    }

    @Test
    void should_be_idempotent_when_dropping_orphans() {
        ToolExecutionResultMessage orphan = ToolExecutionResultMessage.from(toolExecutionRequest("1"), "4");
        AiMessage followUp = aiMessage("2 + 2 = 4");

        List<ChatMessage> once = ToolAwareMessageSanitizer.sanitize(asList(orphan, followUp));
        List<ChatMessage> twice = ToolAwareMessageSanitizer.sanitize(once);

        assertThat(twice).isSameAs(once).containsExactly(followUp);
    }

    @Test
    void should_be_idempotent_when_stripping_partially_answered_calls() {
        ToolExecutionRequest request1 = toolExecutionRequest("1");
        ToolExecutionRequest request2 = toolExecutionRequest("2");
        AiMessage aiMessage = AiMessage.from(request1, request2);
        ToolExecutionResultMessage result1 = ToolExecutionResultMessage.from(request1, "4");

        List<ChatMessage> once = ToolAwareMessageSanitizer.sanitize(asList(aiMessage, result1));
        List<ChatMessage> twice = ToolAwareMessageSanitizer.sanitize(once);

        assertThat(twice).isSameAs(once);
    }
}
