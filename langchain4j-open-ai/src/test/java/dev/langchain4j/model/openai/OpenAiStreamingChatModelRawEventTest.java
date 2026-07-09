package dev.langchain4j.model.openai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.http.client.MockHttpClient;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class OpenAiStreamingChatModelRawEventTest {

    @Test
    void should_forward_only_raw_events_not_exposed_via_typed_callbacks() throws Exception {
        // Given: a content chunk (delivered via onPartialResponse) and a role-only chunk that carries
        // no content/tool delta (not exposed via any typed callback).
        ServerSentEvent contentEvent =
                new ServerSentEvent(null, "{\"choices\":[{\"index\":0,\"delta\":{\"content\":\"Hello\"}}]}");
        ServerSentEvent roleEvent =
                new ServerSentEvent(null, "{\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\"}}]}");
        // The "[DONE]" sentinel is consumed by the SSE parser and must not be forwarded as a raw event.
        ServerSentEvent doneEvent = new ServerSentEvent(null, "[DONE]");
        List<ServerSentEvent> events = List.of(roleEvent, contentEvent, doneEvent);
        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(events);

        StreamingChatModel model = OpenAiStreamingChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .apiKey("test-key")
                .modelName("gpt-4o-mini")
                .build();

        CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();
        StringBuilder partialResponses = new StringBuilder();
        List<Object> rawEvents = new ArrayList<>();

        // When
        model.chat("Hi", new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                partialResponses.append(partialResponse);
            }

            @Override
            public void onUnmappedRawEvent(Object rawEvent) {
                rawEvents.add(rawEvent);
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                futureResponse.complete(completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                futureResponse.completeExceptionally(error);
            }
        });

        futureResponse.get(5, TimeUnit.SECONDS);

        // Then: the content chunk was exposed via onPartialResponse and is NOT repeated as a raw event;
        // only the role-only chunk is surfaced via onUnmappedRawEvent.
        assertThat(partialResponses.toString()).isEqualTo("Hello");
        assertThat(rawEvents).containsExactly(roleEvent);
    }

    @Test
    void should_not_throw_npe_when_tool_call_delta_has_no_function_object() throws Exception {
        // Given: an OpenAI-compatible gateway (e.g. LiteLLM, vLLM) emits a header chunk with an id
        // but no "function" object at all, followed by a chunk carrying the function details.
        ServerSentEvent headerChunk = new ServerSentEvent(
                null,
                "{\"choices\":[{\"index\":0,\"delta\":{\"tool_calls\":"
                        + "[{\"index\":0,\"id\":\"call_x\",\"type\":\"function\"}]}}]}");
        ServerSentEvent functionChunk = new ServerSentEvent(
                null,
                "{\"choices\":[{\"index\":0,\"delta\":{\"tool_calls\":"
                        + "[{\"index\":0,\"function\":{\"name\":\"getWeather\",\"arguments\":\"{}\"}}]}}]}");
        ServerSentEvent doneEvent = new ServerSentEvent(null, "[DONE]");
        List<ServerSentEvent> events = List.of(headerChunk, functionChunk, doneEvent);
        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(events);

        StreamingChatModel model = OpenAiStreamingChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .apiKey("test-key")
                .modelName("gpt-4o-mini")
                .build();

        CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();

        // When: streaming (should not throw NPE)
        model.chat("What is the weather?", new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {}

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                futureResponse.complete(completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                futureResponse.completeExceptionally(error);
            }
        });

        // Then
        ChatResponse response = futureResponse.get(5, TimeUnit.SECONDS);
        assertThat(response.aiMessage().toolExecutionRequests()).hasSize(1);
        assertThat(response.aiMessage().toolExecutionRequests().get(0).id()).isEqualTo("call_x");
        assertThat(response.aiMessage().toolExecutionRequests().get(0).name()).isEqualTo("getWeather");
    }
}
