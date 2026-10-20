package dev.langchain4j.model.mistralai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.http.client.MockHttpClient;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.CompleteToolCall;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class MistralAiStreamingChatModelToolCallsTest {

    private static final String MODEL = "mistral-large-latest";

    private static ServerSentEvent toolCallEvent(String id, String name, String arguments) {
        return new ServerSentEvent(
                null,
                ("{\"id\":\"abc\",\"model\":\"%s\",\"choices\":[{\"index\":0,\"delta\":{\"tool_calls\":"
                                + "[{\"id\":\"%s\",\"type\":\"function\",\"function\":{\"name\":\"%s\",\"arguments\":\"%s\"}}]}}]}")
                        .formatted(MODEL, id, name, arguments.replace("\"", "\\\"")));
    }

    @Test
    void should_keep_tool_calls_that_arrive_in_separate_chunks() throws Exception {
        // Given: two tool calls, each in its own chunk, then the finish event and [DONE]
        List<ServerSentEvent> events = List.of(
                toolCallEvent("call_paris", "get_weather", "{\"city\":\"Paris\"}"),
                toolCallEvent("call_london", "get_weather", "{\"city\":\"London\"}"),
                new ServerSentEvent(
                        null,
                        "{\"id\":\"abc\",\"model\":\"%s\",\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"tool_calls\"}]}"
                                .formatted(MODEL)),
                new ServerSentEvent(null, "[DONE]"));

        StreamingChatModel model = MistralAiStreamingChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(MockHttpClient.thatAlwaysResponds(events)))
                .apiKey("test-api-key")
                .modelName(MODEL)
                .build();

        CompletableFuture<ChatResponse> future = new CompletableFuture<>();
        List<CompleteToolCall> completeToolCalls = new ArrayList<>();

        // When
        model.chat("weather in Paris and London?", new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {}

            @Override
            public void onCompleteToolCall(CompleteToolCall completeToolCall) {
                completeToolCalls.add(completeToolCall);
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                future.complete(completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                future.completeExceptionally(error);
            }
        });

        ChatResponse response = future.get(5, TimeUnit.SECONDS);

        // Then: both calls are reported with their own index and both reach the final message
        assertThat(completeToolCalls)
                .extracting(CompleteToolCall::index)
                .containsExactly(0, 1);
        assertThat(completeToolCalls)
                .extracting(call -> call.toolExecutionRequest().id())
                .containsExactly("call_paris", "call_london");
        assertThat(response.aiMessage().toolExecutionRequests())
                .extracting(ToolExecutionRequest::id, ToolExecutionRequest::arguments)
                .containsExactly(
                        tuple("call_paris", "{\"city\":\"Paris\"}"),
                        tuple("call_london", "{\"city\":\"London\"}"));
    }
}
