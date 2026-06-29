package dev.langchain4j.model.ollama;

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

class OllamaStreamingChatModelRawEventTest {

    @Test
    void should_forward_only_raw_events_not_exposed_via_typed_callbacks() throws Exception {
        // Given: a content chunk (delivered via onPartialResponse), a chunk with no content/tool calls
        // (not exposed via any typed callback), and the terminal done chunk.
        ServerSentEvent contentEvent = new ServerSentEvent(
                null, "{\"model\":\"llama3\",\"message\":{\"role\":\"assistant\",\"content\":\"Hi\"},\"done\":false}");
        ServerSentEvent emptyEvent = new ServerSentEvent(
                null, "{\"model\":\"llama3\",\"message\":{\"role\":\"assistant\",\"content\":\"\"},\"done\":false}");
        ServerSentEvent doneEvent = new ServerSentEvent(
                null,
                "{\"model\":\"llama3\",\"message\":{\"role\":\"assistant\",\"content\":\"\"},\"done\":true,\"done_reason\":\"stop\",\"prompt_eval_count\":1,\"eval_count\":1}");
        List<ServerSentEvent> events = List.of(contentEvent, emptyEvent, doneEvent);

        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(events);
        OllamaStreamingChatModel model = OllamaStreamingChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .baseUrl("http://localhost:11434")
                .modelName("llama3")
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

        // Then: the content chunk was exposed via onPartialResponse and is NOT repeated as a raw event;
        // only the empty chunk (no typed callback) is surfaced via onUnmappedRawEvent.
        assertThat(partialResponses.toString()).isEqualTo("Hi");
        assertThat(rawEvents).containsExactly(emptyEvent);
    }
}
