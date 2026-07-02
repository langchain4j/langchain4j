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
}
