package dev.langchain4j.model.openai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.internal.chat.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class OpenAiStreamingResponseBuilderTest {

    @Test
    void should_handle_null_tool_call_index() {
        // Given: a tool call with null index (as Gemini's OpenAI-compatible API returns)
        OpenAiStreamingResponseBuilder builder = new OpenAiStreamingResponseBuilder();

        FunctionCall functionCall = FunctionCall.builder()
                .name("getWeather")
                .arguments("{\"city\": \"Berlin\"}")
                .build();

        ToolCall toolCall = ToolCall.builder()
                .id("call_123")
                .index(null) // Gemini returns null index
                .type(ToolType.FUNCTION)
                .function(functionCall)
                .build();

        Delta delta = Delta.builder().toolCalls(List.of(toolCall)).build();

        ChatCompletionChoice choice =
                ChatCompletionChoice.builder().index(0).delta(delta).build();

        ChatCompletionResponse response = ChatCompletionResponse.builder()
                .id("resp_1")
                .model("gemini-2.0-flash")
                .choices(List.of(choice))
                .build();

        // When: appending the response (should not throw NPE)
        builder.append(response);

        // Then: the tool execution request is built correctly
        ChatResponse chatResponse = builder.build();
        assertThat(chatResponse.aiMessage().toolExecutionRequests()).hasSize(1);
        assertThat(chatResponse.aiMessage().toolExecutionRequests().get(0).name())
                .isEqualTo("getWeather");
        assertThat(chatResponse.aiMessage().toolExecutionRequests().get(0).arguments())
                .isEqualTo("{\"city\": \"Berlin\"}");
        assertThat(chatResponse.aiMessage().toolExecutionRequests().get(0).id()).isEqualTo("call_123");
    }

    @Test
    void should_handle_non_null_tool_call_index() {
        // Given: a standard tool call with non-null index
        OpenAiStreamingResponseBuilder builder = new OpenAiStreamingResponseBuilder();

        FunctionCall functionCall = FunctionCall.builder()
                .name("getTemperature")
                .arguments("{\"city\": \"Paris\"}")
                .build();

        ToolCall toolCall = ToolCall.builder()
                .id("call_456")
                .index(0)
                .type(ToolType.FUNCTION)
                .function(functionCall)
                .build();

        Delta delta = Delta.builder().toolCalls(List.of(toolCall)).build();

        ChatCompletionChoice choice =
                ChatCompletionChoice.builder().index(0).delta(delta).build();

        ChatCompletionResponse response = ChatCompletionResponse.builder()
                .id("resp_2")
                .model("gpt-4o")
                .choices(List.of(choice))
                .build();

        // When
        builder.append(response);

        // Then
        ChatResponse chatResponse = builder.build();
        assertThat(chatResponse.aiMessage().toolExecutionRequests()).hasSize(1);
        assertThat(chatResponse.aiMessage().toolExecutionRequests().get(0).name())
                .isEqualTo("getTemperature");
    }
}
