package dev.langchain4j.model.openai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.internal.chat.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class OpenAiStreamingResponseBuilderTest {

    @Test
    void should_handle_null_tool_call_index() {
        // Given: a tool call with null index (as Gemini's OpenAI-compatible API returns)
        OpenAiStreamingResponseBuilder builder = new OpenAiStreamingResponseBuilder();

        ToolCall toolCall = ToolCall.builder()
                .id("call_123")
                .index(null)
                .type(ToolType.FUNCTION)
                .function(FunctionCall.builder()
                        .name("getWeather")
                        .arguments("{\"city\": \"Berlin\"}")
                        .build())
                .build();

        ChatCompletionResponse response = chatCompletionResponse(toolCall);

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
    void should_handle_multiple_tool_calls_with_null_index() {
        // Given: two distinct tool calls, both with null index (Gemini parallel tool calls)
        OpenAiStreamingResponseBuilder builder = new OpenAiStreamingResponseBuilder();

        // First tool call streaming chunks
        ToolCall firstToolCall = ToolCall.builder()
                .id("call_aaa")
                .index(null)
                .type(ToolType.FUNCTION)
                .function(FunctionCall.builder()
                        .name("getWeather")
                        .arguments("{\"city\": \"Berlin\"}")
                        .build())
                .build();
        builder.append(chatCompletionResponse(firstToolCall));

        // Second tool call streaming chunks — different id, still null index
        ToolCall secondToolCall = ToolCall.builder()
                .id("call_bbb")
                .index(null)
                .type(ToolType.FUNCTION)
                .function(FunctionCall.builder()
                        .name("getTemperature")
                        .arguments("{\"city\": \"Paris\"}")
                        .build())
                .build();
        builder.append(chatCompletionResponse(secondToolCall));

        // Then: both tool execution requests should be built separately
        ChatResponse chatResponse = builder.build();
        assertThat(chatResponse.aiMessage().toolExecutionRequests()).hasSize(2);
        assertThat(chatResponse.aiMessage().toolExecutionRequests().get(0).name())
                .isEqualTo("getWeather");
        assertThat(chatResponse.aiMessage().toolExecutionRequests().get(0).id()).isEqualTo("call_aaa");
        assertThat(chatResponse.aiMessage().toolExecutionRequests().get(1).name())
                .isEqualTo("getTemperature");
        assertThat(chatResponse.aiMessage().toolExecutionRequests().get(1).id()).isEqualTo("call_bbb");
    }

    @Test
    void should_handle_non_null_tool_call_index() {
        // Given: a standard tool call with non-null index
        OpenAiStreamingResponseBuilder builder = new OpenAiStreamingResponseBuilder();

        ToolCall toolCall = ToolCall.builder()
                .id("call_456")
                .index(0)
                .type(ToolType.FUNCTION)
                .function(FunctionCall.builder()
                        .name("getTemperature")
                        .arguments("{\"city\": \"Paris\"}")
                        .build())
                .build();

        builder.append(chatCompletionResponse(toolCall));

        // Then
        ChatResponse chatResponse = builder.build();
        assertThat(chatResponse.aiMessage().toolExecutionRequests()).hasSize(1);
        assertThat(chatResponse.aiMessage().toolExecutionRequests().get(0).name())
                .isEqualTo("getTemperature");
    }

    @Test
    void should_keep_all_tool_calls_from_same_delta() {

        // given
        OpenAiStreamingResponseBuilder builder = new OpenAiStreamingResponseBuilder();

        ToolCall tc1 = ToolCall.builder()
                .id("call_1")
                .index(0)
                .type(ToolType.FUNCTION)
                .function(FunctionCall.builder()
                        .name("tool_name")
                        .arguments("{}")
                        .build())
                .build();

        ToolCall tc2 = ToolCall.builder()
                .id("call_2")
                .index(1)
                .type(ToolType.FUNCTION)
                .function(FunctionCall.builder()
                        .name("tool_name")
                        .arguments("{}")
                        .build())
                .build();

        ChatCompletionResponse partial = ChatCompletionResponse.builder()
                .id("resp_1")
                .model("openai-compatible-model")
                .choices(List.of(ChatCompletionChoice.builder()
                        .index(0)
                        .delta(Delta.builder()
                                .toolCalls(List.of(tc1, tc2))
                                .build())
                        .build()))
                .build();

        // when
        builder.append(partial);
        ChatResponse response = builder.build();

        // then
        List<ToolExecutionRequest> toolExecutionRequests = response.aiMessage().toolExecutionRequests();
        assertThat(toolExecutionRequests).hasSize(2);
        assertThat(toolExecutionRequests)
                .extracting(ToolExecutionRequest::id)
                .containsExactly("call_1", "call_2");
    }

    private static ChatCompletionResponse chatCompletionResponse(ToolCall toolCall) {
        return ChatCompletionResponse.builder()
                .id("resp_1")
                .model("gemini-2.0-flash")
                .choices(List.of(ChatCompletionChoice.builder()
                        .index(0)
                        .delta(Delta.builder().toolCalls(List.of(toolCall)).build())
                        .build()))
                .build();
    }
}
