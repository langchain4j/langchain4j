package dev.langchain4j.model.anthropic;

import static dev.langchain4j.model.anthropic.internal.mapper.AnthropicMapper.SERVER_TOOL_RESULTS_KEY;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AnthropicSkillsExtractFileIdsTest {

    @Test
    void shouldExtractFileIdsFromCodeExecutionToolResult() {
        AnthropicServerToolResult result = AnthropicServerToolResult.builder()
                .type("code_execution_tool_result")
                .toolUseId("toolu_123")
                .content(Map.of(
                        "type", "code_execution_result",
                        "stdout", "",
                        "content", List.of(Map.of("type", "code_execution_output", "file_id", "file_abc123"))))
                .build();

        ChatResponse chatResponse = chatResponseWithServerToolResults(List.of(result));

        assertThat(AnthropicSkills.extractFileIds(chatResponse)).containsExactly("file_abc123");
    }

    @Test
    void shouldExtractMultipleFileIdsInOrderWithoutDuplicates() {
        AnthropicServerToolResult result = AnthropicServerToolResult.builder()
                .type("code_execution_tool_result")
                .toolUseId("toolu_123")
                .content(Map.of(
                        "content",
                        List.of(
                                Map.of("file_id", "file_1"),
                                Map.of("file_id", "file_2"),
                                Map.of("file_id", "file_1"))))
                .build();

        ChatResponse chatResponse = chatResponseWithServerToolResults(List.of(result));

        assertThat(AnthropicSkills.extractFileIds(chatResponse)).containsExactly("file_1", "file_2");
    }

    @Test
    void shouldReturnEmptyListWhenNoServerToolResults() {
        ChatResponse chatResponse = ChatResponse.builder()
                .aiMessage(AiMessage.from("no files here"))
                .build();

        assertThat(AnthropicSkills.extractFileIds(chatResponse)).isEmpty();
    }

    @Test
    void shouldReturnEmptyListWhenServerToolResultHasNoFileId() {
        AnthropicServerToolResult result = AnthropicServerToolResult.builder()
                .type("web_search_tool_result")
                .toolUseId("toolu_456")
                .content(List.of(Map.of("title", "Some search result")))
                .build();

        ChatResponse chatResponse = chatResponseWithServerToolResults(List.of(result));

        assertThat(AnthropicSkills.extractFileIds(chatResponse)).isEmpty();
    }

    private static ChatResponse chatResponseWithServerToolResults(List<AnthropicServerToolResult> results) {
        AiMessage aiMessage = AiMessage.builder()
                .text("Here is your file")
                .attributes(Map.of(SERVER_TOOL_RESULTS_KEY, results))
                .build();

        return ChatResponse.builder().aiMessage(aiMessage).build();
    }
}
