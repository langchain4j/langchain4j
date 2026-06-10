package dev.langchain4j.model.openai;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.PartialResponse;
import dev.langchain4j.model.chat.response.RawStreamingEvent;
import dev.langchain4j.model.chat.response.StreamingEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Flow;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the Responses-API publisher path surfaces provider events that langchain4j does not map
 * to a typed {@link StreamingEvent} (e.g. web-search events) as {@link RawStreamingEvent}s, rather than
 * dropping them — while still mapping known events to typed events. This is the real test surface for
 * {@code RawStreamingEvent}: unlike chat-completions (where every frame is a completion chunk), the
 * Responses API emits discrete, named events, many of which the generic API does not model.
 */
class OpenAiResponsesStreamingChatModelRawEventTest {

    private static final String PATH = "/v1/responses";

    private WireMockServer wireMockServer;

    @BeforeEach
    void startServer() {
        wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();
    }

    @AfterEach
    void stopServer() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @Test
    void surfaces_unmapped_provider_event_as_raw_streaming_event() throws Exception {
        // A stream mixing a mapped event (output_text.delta), an unmapped provider event
        // (web_search_call.searching) and the terminal completed event.
        String sse =
                """
                data: {"type":"response.output_text.delta","delta":"Hello"}

                data: {"type":"response.web_search_call.searching","item_id":"ws_1","query":"langchain4j"}

                data: {"type":"response.completed","response":{"id":"resp_1","model":"gpt-4o-mini","status":"completed","output":[{"type":"message","content":[{"type":"output_text","text":"Hello"}]}]}}

                data: [DONE]

                """;
        wireMockServer.stubFor(post(urlEqualTo(PATH)).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "text/event-stream")
                .withBody(sse)));

        OpenAiResponsesStreamingChatModel model = OpenAiResponsesStreamingChatModel.builder()
                .baseUrl("http://localhost:" + wireMockServer.port() + "/v1")
                .apiKey("test-key")
                .modelName("gpt-4o-mini")
                .build();

        List<StreamingEvent> events = collect(model.chat(ChatRequest.builder()
                .messages(UserMessage.from("hi"))
                .build()));

        // The unmapped web-search event is surfaced raw, with its type and payload preserved.
        List<RawStreamingEvent> rawEvents =
                events.stream().filter(e -> e instanceof RawStreamingEvent).map(e -> (RawStreamingEvent) e).toList();
        assertThat(rawEvents).hasSize(1);
        assertThat(rawEvents.get(0).providerEventType()).isEqualTo("response.web_search_call.searching");
        assertThat(rawEvents.get(0).rawData()).contains("\"ws_1\"").contains("langchain4j");

        // The mapped text delta still arrives as a typed event...
        assertThat(events).anyMatch(e -> e instanceof PartialResponse p && "Hello".equals(p.text()));
        // ...and the terminal ChatResponse is still the last event.
        assertThat(events.get(events.size() - 1)).isInstanceOf(ChatResponse.class);
    }

    private static List<StreamingEvent> collect(Flow.Publisher<StreamingEvent> publisher) throws Exception {
        List<StreamingEvent> events = new CopyOnWriteArrayList<>();
        CompletableFuture<Void> done = new CompletableFuture<>();
        publisher.subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(StreamingEvent event) {
                events.add(event);
            }

            @Override
            public void onError(Throwable throwable) {
                done.completeExceptionally(throwable);
            }

            @Override
            public void onComplete() {
                done.complete(null);
            }
        });
        done.get(10, SECONDS);
        return events;
    }
}
