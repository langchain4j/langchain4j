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
    void should_ignore_trailing_sentinel_chunk_from_deepseek_v4_flash() {
        // Given: deepseek-v4-flash (OpenAI-compatible) emits a trailing sentinel chunk after all
        // tool_calls have streamed:
        //   {"index": 0, "id": "", "type": "function", "function": {"arguments": null}}
        // It carries an empty string id, no function.name, and null arguments. The accumulator
        // must treat this as an end-of-stream marker and not produce a ghost ToolExecutionRequest.
        OpenAiStreamingResponseBuilder builder = new OpenAiStreamingResponseBuilder();

        // Header chunk: id + name, empty arguments
        builder.append(chatCompletionResponse(ToolCall.builder()
                .id("call_abc")
                .index(0)
                .type(ToolType.FUNCTION)
                .function(
                        FunctionCall.builder().name("getWeather").arguments("").build())
                .build()));

        // Argument fragments: empty id/name, partial arguments
        builder.append(chatCompletionResponse(ToolCall.builder()
                .id("")
                .index(0)
                .type(ToolType.FUNCTION)
                .function(
                        FunctionCall.builder().name("").arguments("{\"city\":").build())
                .build()));
        builder.append(chatCompletionResponse(ToolCall.builder()
                .id("")
                .index(0)
                .type(ToolType.FUNCTION)
                .function(FunctionCall.builder()
                        .name("")
                        .arguments(" \"Berlin\"}")
                        .build())
                .build()));

        // Trailing sentinel: empty id, no function.name, null arguments
        builder.append(chatCompletionResponse(ToolCall.builder()
                .id("")
                .index(0)
                .type(ToolType.FUNCTION)
                .function(FunctionCall.builder().arguments(null).build())
                .build()));

        ChatResponse response = builder.build();
        List<ToolExecutionRequest> requests = response.aiMessage().toolExecutionRequests();
        assertThat(requests).hasSize(1);
        assertThat(requests.get(0).id()).isEqualTo("call_abc");
        assertThat(requests.get(0).name()).isEqualTo("getWeather");
        assertThat(requests.get(0).arguments()).isEqualTo("{\"city\": \"Berlin\"}");
    }

    @Test
    void should_not_produce_ghost_for_orphan_sentinel_chunk() {
        // Given: a sentinel-style chunk arrives at an index that has no prior data
        // (defensive — the deepseek sentinel carries index=0 in observed logs, but other
        // OpenAI-compatible providers may send a sentinel with no matching index).
        OpenAiStreamingResponseBuilder builder = new OpenAiStreamingResponseBuilder();

        // Real tool call streamed at index 0
        builder.append(chatCompletionResponse(ToolCall.builder()
                .id("call_xyz")
                .index(0)
                .type(ToolType.FUNCTION)
                .function(FunctionCall.builder().name("getTime").arguments("{}").build())
                .build()));

        // Orphan sentinel at an unrelated index — id empty, no function.name, null arguments
        builder.append(chatCompletionResponse(ToolCall.builder()
                .id("")
                .index(99)
                .type(ToolType.FUNCTION)
                .function(FunctionCall.builder().arguments(null).build())
                .build()));

        ChatResponse response = builder.build();
        List<ToolExecutionRequest> requests = response.aiMessage().toolExecutionRequests();
        assertThat(requests).hasSize(1);
        assertThat(requests.get(0).id()).isEqualTo("call_xyz");
        assertThat(requests.get(0).name()).isEqualTo("getTime");
    }

    @Test
    void should_keep_all_tool_calls_from_same_delta() {

        // given
        OpenAiStreamingResponseBuilder builder = new OpenAiStreamingResponseBuilder();

        ToolCall tc1 = ToolCall.builder()
                .id("call_1")
                .index(0)
                .type(ToolType.FUNCTION)
                .function(
                        FunctionCall.builder().name("tool_name").arguments("{}").build())
                .build();

        ToolCall tc2 = ToolCall.builder()
                .id("call_2")
                .index(1)
                .type(ToolType.FUNCTION)
                .function(
                        FunctionCall.builder().name("tool_name").arguments("{}").build())
                .build();

        ChatCompletionResponse partial = ChatCompletionResponse.builder()
                .id("resp_1")
                .model("openai-compatible-model")
                .choices(List.of(ChatCompletionChoice.builder()
                        .index(0)
                        .delta(Delta.builder().toolCalls(List.of(tc1, tc2)).build())
                        .build()))
                .build();

        // when
        builder.append(partial);
        ChatResponse response = builder.build();

        // then
        List<ToolExecutionRequest> toolExecutionRequests = response.aiMessage().toolExecutionRequests();
        assertThat(toolExecutionRequests).hasSize(2);
        assertThat(toolExecutionRequests).extracting(ToolExecutionRequest::id).containsExactly("call_1", "call_2");
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
