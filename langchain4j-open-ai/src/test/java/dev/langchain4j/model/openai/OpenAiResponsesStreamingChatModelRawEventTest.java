package dev.langchain4j.model.openai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.http.client.MockHttpClient;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class OpenAiResponsesStreamingChatModelRawEventTest {

    @Test
    void should_forward_only_raw_events_not_exposed_via_typed_callbacks() throws Exception {
        // Given: a text delta (delivered via onPartialResponse), a server-tool lifecycle event that
        // langchain4j does not map (not exposed via any typed callback), and the terminal completed event.
        ServerSentEvent textEvent =
                new ServerSentEvent(null, "{\"type\":\"response.output_text.delta\",\"delta\":\"Hello\"}");
        ServerSentEvent webSearchEvent = new ServerSentEvent(
                null, "{\"type\":\"response.web_search_call.in_progress\",\"item_id\":\"ws_1\",\"output_index\":0}");
        ServerSentEvent completedEvent = new ServerSentEvent(
                null,
                "{\"type\":\"response.completed\",\"response\":{\"id\":\"resp_1\",\"model\":\"gpt-5.4-mini\",\"status\":\"completed\",\"output\":[]}}");
        List<ServerSentEvent> events = List.of(textEvent, webSearchEvent, completedEvent);
        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(events);

        OpenAiResponsesStreamingChatModel model = OpenAiResponsesStreamingChatModel.builder()
                .apiKey("test-key")
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .modelName("gpt-5.4-mini")
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

        // Then: the text delta was exposed via onPartialResponse and is NOT repeated as a raw event;
        // only the unmapped server-tool event is surfaced via onUnmappedRawEvent.
        assertThat(partialResponses.toString()).isEqualTo("Hello");
        assertThat(rawEvents).containsExactly(webSearchEvent);
    }
}
