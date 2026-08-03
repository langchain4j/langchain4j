package dev.langchain4j.model.ollama;

import static dev.langchain4j.model.ollama.OllamaJsonUtils.toJsonWithoutIdent;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InternalOllamaHelperTest {

    @Test
    void toOllamaMessages_concatenatesMultipleTextContents() {
        UserMessage userMessage =
                UserMessage.from(TextContent.from("Hello"), TextContent.from("world"), TextContent.from("!"));

        List<Message> messages = InternalOllamaHelper.toOllamaMessages(List.<ChatMessage>of(userMessage));

        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).getContent()).isEqualTo("Hello\nworld\n!");
        assertThat(messages.get(0).getImages()).isNull();
    }

    @Test
    void toOllamaMessages_concatenatesMultipleTextContentsWithImage() {
        UserMessage userMessage = UserMessage.from(
                TextContent.from("Describe these"),
                TextContent.from("two parts"),
                ImageContent.from("aW1hZ2U=", "image/png"));

        List<Message> messages = InternalOllamaHelper.toOllamaMessages(List.<ChatMessage>of(userMessage));

        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).getContent()).isEqualTo("Describe these\ntwo parts");
        assertThat(messages.get(0).getImages()).containsExactly("aW1hZ2U=");
    }

    @Test
    void toOllamaMessages_imageOnlyMessageHasEmptyContent() {
        UserMessage userMessage = UserMessage.from(ImageContent.from("aW1hZ2U=", "image/png"));

        List<Message> messages = InternalOllamaHelper.toOllamaMessages(List.<ChatMessage>of(userMessage));

        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).getContent()).isEmpty();
        assertThat(messages.get(0).getImages()).containsExactly("aW1hZ2U=");
    }

    @Test
    void toOllamaMessages_preservesToolNameInToolExecutionResultMessage() {
        ToolExecutionResultMessage toolExecutionResultMessage = ToolExecutionResultMessage.builder()
                .id("call-1")
                .toolName("makeCoffee")
                .text("Out of coffee beans.")
                .isError(true)
                .attributes(Map.of("local", "metadata"))
                .build();

        List<Message> messages =
                InternalOllamaHelper.toOllamaMessages(List.<ChatMessage>of(toolExecutionResultMessage));

        assertThat(messages).hasSize(1);
        Message message = messages.get(0);
        assertThat(message.getRole()).isEqualTo(Role.TOOL);
        assertThat(message.getContent()).isEqualTo("Out of coffee beans.");
        assertThat(message.getAdditionalFields()).containsExactly(Map.entry("tool_name", "makeCoffee"));
        assertThat(toJsonWithoutIdent(message))
                .contains("\"role\":\"tool\"", "\"content\":\"Out of coffee beans.\"", "\"tool_name\":\"makeCoffee\"")
                .doesNotContain("is_error", "call-1", "local");
    }

    @Test
    void toToolExecutionRequests_mapsToolCalls() {
        ToolCall toolCall = ToolCall.builder()
                .id("tool-1")
                .function(FunctionCall.builder()
                        .name("lookupWeather")
                        .arguments(Map.of("city", "Shanghai"))
                        .build())
                .build();

        List<ToolExecutionRequest> result = InternalOllamaHelper.toToolExecutionRequests(List.of(toolCall));

        assertThat(result)
                .containsExactly(ToolExecutionRequest.builder()
                        .id("tool-1")
                        .name("lookupWeather")
                        .arguments("{\"city\":\"Shanghai\"}")
                        .build());
    }

    @Test
    void toToolExecutionRequests_handlesEmptyToolCalls() {
        List<ToolExecutionRequest> result = InternalOllamaHelper.toToolExecutionRequests(List.of());

        assertThat(result).isEmpty();
    }
}
