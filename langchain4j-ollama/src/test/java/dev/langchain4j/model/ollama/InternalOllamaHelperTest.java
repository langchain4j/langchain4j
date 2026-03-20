package dev.langchain4j.model.ollama;

import static dev.langchain4j.model.ollama.OllamaJsonUtils.toJsonWithoutIdent;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InternalOllamaHelperTest {

    @Test
    void should_include_thinking_and_attributes_for_ai_messages() {
        AiMessage aiMessage = AiMessage.builder()
                .text("The answer is 4.")
                .thinking("Calculate the square root.")
                .attributes(Map.of("source", "reasoning"))
                .build();

        Message message =
                InternalOllamaHelper.toOllamaMessages(List.of(aiMessage)).get(0);
        String json = toJsonWithoutIdent(message);

        assertThat(message.getRole()).isEqualTo(Role.ASSISTANT);
        assertThat(message.getContent()).isEqualTo("The answer is 4.");
        assertThat(json)
                .contains("\"thinking\":\"Calculate the square root.\"")
                .contains("\"attributes\":{\"source\":\"reasoning\"}");
    }

    @Test
    void should_include_tool_execution_result_metadata() {
        ToolExecutionResultMessage toolExecutionResultMessage = ToolExecutionResultMessage.builder()
                .id("call-1")
                .toolName("brewCoffee")
                .text("Out of coffee beans.")
                .isError(true)
                .attributes(Map.of("code", "OUT_OF_BEANS"))
                .build();

        Message message = InternalOllamaHelper.toOllamaMessages(List.of(toolExecutionResultMessage))
                .get(0);
        String json = toJsonWithoutIdent(message);

        assertThat(message.getRole()).isEqualTo(Role.TOOL);
        assertThat(message.getContent()).isEqualTo("Out of coffee beans.");
        assertThat(json)
                .contains("\"id\":\"call-1\"")
                .contains("\"tool_name\":\"brewCoffee\"")
                .contains("\"is_error\":true")
                .contains("\"attributes\":{\"code\":\"OUT_OF_BEANS\"}");
    }
}
