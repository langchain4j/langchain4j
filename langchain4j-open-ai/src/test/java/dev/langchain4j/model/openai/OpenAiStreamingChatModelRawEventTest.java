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
 * Verifies the chat-completions publisher path surfaces a completion chunk it cannot map to a typed
 * event as a {@link RawStreamingEvent}, while suppressing the normal chat-completion plumbing chunks
 * (the role-only opening delta and the final finish/usage chunk) so they don't become raw noise.
 */
class OpenAiStreamingChatModelRawEventTest {

    private static final String PATH = "/v1/chat/completions";

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
    void surfaces_every_unmapped_chunk_as_raw() throws Exception {
        // Only the content delta maps to a typed event. The role-only, empty-delta, usage-only and finish
        // chunks produce no typed event, so each is surfaced raw — nothing is dropped except [DONE].
        String sse =
                """
                data: {"choices":[{"index":0,"delta":{"role":"assistant"}}]}

                data: {"choices":[{"index":0,"delta":{"content":"Hi"}}]}

                data: {"choices":[{"index":0,"delta":{}}]}

                data: {"choices":[],"usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}

                data: {"choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}

                data: [DONE]

                """;
        wireMockServer.stubFor(post(urlEqualTo(PATH)).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "text/event-stream")
                .withBody(sse)));

        OpenAiStreamingChatModel model = OpenAiStreamingChatModel.builder()
                .baseUrl("http://localhost:" + wireMockServer.port() + "/v1")
                .apiKey("test-key")
                .modelName("gpt-4o-mini")
                .build();

        List<StreamingEvent> events = collect(model.chat(ChatRequest.builder()
                .messages(UserMessage.from("hi"))
                .build()));

        // Four raw events: role-only, empty-delta, usage-only and finish chunks (the content delta is typed).
        List<RawStreamingEvent> rawEvents =
                events.stream().filter(e -> e instanceof RawStreamingEvent).map(e -> (RawStreamingEvent) e).toList();
        assertThat(rawEvents).hasSize(4);
        assertThat(rawEvents).anyMatch(r -> r.rawData().contains("\"role\":\"assistant\""));
        assertThat(rawEvents).anyMatch(r -> r.rawData().contains("\"usage\""));
        assertThat(rawEvents).anyMatch(r -> r.rawData().contains("finish_reason"));
        // the standalone empty-delta chunk (without a finish_reason)
        assertThat(rawEvents).anyMatch(r -> r.rawData().contains("\"delta\":{}") && !r.rawData().contains("finish_reason"));

        assertThat(events).anyMatch(e -> e instanceof PartialResponse p && "Hi".equals(p.text()));
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
