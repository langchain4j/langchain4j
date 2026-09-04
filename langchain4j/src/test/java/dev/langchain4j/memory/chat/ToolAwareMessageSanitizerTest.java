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
        return ToolExecutionRequest.builder()
                .id(id)
                .name("calculator")
                .arguments("{ \"a\": 2, \"b\": 2 }")
                .build();
    }

    @Test
    void should_return_same_instance_when_list_is_empty() {
        List<ChatMessage> messages = emptyList();
        assertThat(ToolAwareMessageSanitizer.sanitize(messages)).isSameAs(messages);
    }

    @Test
    void should_return_same_instance_when_no_message_is_orphaned() {
        UserMessage userMessage = userMessage("How much is 2+2?");
        AiMessage aiMessage = AiMessage.from(toolExecutionRequest("1"));
        ToolExecutionResultMessage toolExecutionResultMessage =
                ToolExecutionResultMessage.from(toolExecutionRequest("1"), "4");

        List<ChatMessage> messages = asList(userMessage, aiMessage, toolExecutionResultMessage);

        assertThat(ToolAwareMessageSanitizer.sanitize(messages)).isSameAs(messages);
    }

    @Test
    void should_drop_ToolExecutionResultMessage_whose_parent_AiMessage_is_missing() {
        ToolExecutionResultMessage orphan = ToolExecutionResultMessage.from(toolExecutionRequest("1"), "4");
        AiMessage followUp = aiMessage("2 + 2 = 4");

        List<ChatMessage> sanitized = ToolAwareMessageSanitizer.sanitize(asList(orphan, followUp));

        assertThat(sanitized).containsExactly(followUp);
    }

    @Test
    void should_drop_only_the_orphaned_result_and_keep_the_rest_of_the_history() {
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
    void should_never_treat_a_null_id_result_as_orphaned() {
        // Some providers (and several fixtures elsewhere in this codebase) never assign tool-call
        // ids. Without an id there's no reliable way to match a result back to its AiMessage, so a
        // null id must never be dropped - otherwise legitimate id-less histories get corrupted.
        ToolExecutionResultMessage nullIdResult = ToolExecutionResultMessage.from(null, "calculator", "4");
        UserMessage userMessage = userMessage("hi");

        List<ChatMessage> messages = asList(nullIdResult, userMessage);

        assertThat(ToolAwareMessageSanitizer.sanitize(messages)).isSameAs(messages);
    }

    @Test
    void should_be_idempotent() {
        ToolExecutionResultMessage orphan = ToolExecutionResultMessage.from(toolExecutionRequest("1"), "4");
        AiMessage followUp = aiMessage("2 + 2 = 4");

        List<ChatMessage> once = ToolAwareMessageSanitizer.sanitize(asList(orphan, followUp));
        List<ChatMessage> twice = ToolAwareMessageSanitizer.sanitize(once);

        assertThat(twice).isSameAs(once).containsExactly(followUp);
    }
}
