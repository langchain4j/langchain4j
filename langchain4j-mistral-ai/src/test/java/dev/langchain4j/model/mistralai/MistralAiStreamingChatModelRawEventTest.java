package dev.langchain4j.model.mistralai;

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

class MistralAiStreamingChatModelRawEventTest {

    private static final String MODEL = "mistral-large-latest";

    @Test
    void should_forward_only_raw_events_not_exposed_via_typed_callbacks() throws Exception {
        // Given: a text delta (delivered via onPartialResponse), a finish event carrying only finish_reason/usage
        // (not exposed via any typed callback), and the terminal [DONE] sentinel.
        ServerSentEvent textEvent = new ServerSentEvent(
                null,
                "{\"id\":\"abc\",\"model\":\"%s\",\"choices\":[{\"index\":0,\"delta\":{\"content\":[{\"type\":\"text\",\"text\":\"Hi\"}]}}]}"
                        .formatted(MODEL));
        ServerSentEvent finishEvent = new ServerSentEvent(
                null,
                "{\"id\":\"abc\",\"model\":\"%s\",\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":1,\"total_tokens\":2}}"
                        .formatted(MODEL));
        ServerSentEvent doneEvent = new ServerSentEvent(null, "[DONE]");
        List<ServerSentEvent> events = List.of(textEvent, finishEvent, doneEvent);

        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(events);
        StreamingChatModel model = MistralAiStreamingChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .apiKey("test-api-key")
                .modelName(MODEL)
                .build();

        CompletableFuture<ChatResponse> future = new CompletableFuture<>();
        StringBuilder partialResponses = new StringBuilder();
        List<Object> rawEvents = new ArrayList<>();

        // When
        model.chat("hello", new StreamingChatResponseHandler() {
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
                future.complete(completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                future.completeExceptionally(error);
            }
        });

        future.get(5, TimeUnit.SECONDS);

        // Then: the text delta was exposed via onPartialResponse and is NOT repeated as a raw event;
        // only the finish event (no typed callback) is surfaced via onUnmappedRawEvent.
        assertThat(partialResponses.toString()).isEqualTo("Hi");
        assertThat(rawEvents).containsExactly(finishEvent);
    }
}
