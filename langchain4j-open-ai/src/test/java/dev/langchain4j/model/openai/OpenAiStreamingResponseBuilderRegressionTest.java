package dev.langchain4j.model.openai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.internal.chat.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class OpenAiStreamingResponseBuilderRegressionTest {

    @Test
    void should_keep_all_tool_calls_from_same_delta() {
        // Given: a single delta containing 2 distinct tool calls (same streaming chunk)
        OpenAiStreamingResponseBuilder builder = new OpenAiStreamingResponseBuilder();

        ToolCall tc1 = ToolCall.builder()
                .id("call_1")
                .index(0)
                .type(ToolType.FUNCTION)
                .function(FunctionCall.builder()
                        .name("getWeather")
                        .arguments("{\"city\": \"Berlin\"}")
                        .build())
                .build();

        ToolCall tc2 = ToolCall.builder()
                .id("call_2")
                .index(1)
                .type(ToolType.FUNCTION)
                .function(FunctionCall.builder()
                        .name("getTemperature")
                        .arguments("{\"city\": \"Paris\"}")
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

        // When: appending the delta (was previously dropping additional tool calls)
        builder.append(partial);
        ChatResponse response = builder.build();

        // Then: both tool execution requests must be preserved
        List<ToolExecutionRequest> toolExecutionRequests = response.aiMessage().toolExecutionRequests();
        assertThat(toolExecutionRequests).hasSize(2);

        assertThat(toolExecutionRequests.get(0).id()).isEqualTo("call_1");
        assertThat(toolExecutionRequests.get(0).name()).isEqualTo("getWeather");
        assertThat(toolExecutionRequests.get(0).arguments()).isEqualTo("{\"city\": \"Berlin\"}");

        assertThat(toolExecutionRequests.get(1).id()).isEqualTo("call_2");
        assertThat(toolExecutionRequests.get(1).name()).isEqualTo("getTemperature");
        assertThat(toolExecutionRequests.get(1).arguments()).isEqualTo("{\"city\": \"Paris\"}");
    }
}
