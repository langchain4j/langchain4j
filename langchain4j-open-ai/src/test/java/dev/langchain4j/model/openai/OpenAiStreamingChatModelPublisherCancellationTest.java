package dev.langchain4j.model.openai;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.StreamingEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that cancelling the {@link Flow.Subscription} of {@link OpenAiStreamingChatModel#chat(ChatRequest)}
 * <em>mid-stream</em> actually stops the pipeline: once the source is actively streaming, {@code cancel()}
 * must make it go quiet and must never deliver a terminal {@code onComplete}/{@code onError}.
 * <p>
 * This complements {@link OpenAiStreamingChatModelPublisherTckTest}: the Reactive Streams TCK's cancellation
 * checks (spec306/307) cancel <em>before</em> any element is requested, so they don't exercise
 * cancellation while the producer is actively reading the SSE body — the realistic, bug-prone case for
 * an SSE reader. WireMock's chunked-dribble delay spreads the response over time so we can cancel in the
 * middle, deterministically and without a live endpoint.
 * <p>
 * The Reactive Streams contract only guarantees the stream <em>eventually</em> goes quiet
 * ({@code cancel()} need not take effect immediately, so a few in-flight {@code onNext} signals may still
 * arrive — Rule 2.8). So we don't assert zero events after cancel; we let them drain, then assert the
 * stream truly stopped and never ran to completion.
 */
class OpenAiStreamingChatModelPublisherCancellationTest {

    private static final int TOTAL_CONTENT_CHUNKS = 50;
    private static final int EVENTS_BEFORE_CANCEL = 3;

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
    void cancelling_subscription_mid_stream_stops_the_pipeline() throws Exception {
        // given: an SSE response dribbled out over several seconds, so events arrive over time and we can
        // cancel while the source is still actively streaming.
        wireMockServer.stubFor(post(urlEqualTo("/v1/chat/completions")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "text/event-stream")
                .withChunkedDribbleDelay(TOTAL_CONTENT_CHUNKS + 1, 5_000)
                .withBody(openAiStreamBody(TOTAL_CONTENT_CHUNKS))));

        OpenAiStreamingChatModel model = OpenAiStreamingChatModel.builder()
                .baseUrl("http://localhost:" + wireMockServer.port() + "/v1")
                .apiKey("test-key")
                .modelName("gpt-4o-mini")
                .build();

        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from("hi"))
                .build();

        AtomicInteger eventsBeforeCancel = new AtomicInteger();
        AtomicInteger eventsAfterCancel = new AtomicInteger();
        AtomicBoolean cancelled = new AtomicBoolean();
        AtomicReference<Flow.Subscription> subscriptionReference = new AtomicReference<>();
        CompletableFuture<Void> cancelledFuture = new CompletableFuture<>();
        CompletableFuture<Void> terminalSignal = new CompletableFuture<>();

        // when
        model.chat(request).subscribe(new Flow.Subscriber<>() {

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscriptionReference.set(subscription);
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(StreamingEvent event) {
                if (cancelled.get()) {
                    eventsAfterCancel.incrementAndGet(); // in-flight; the spec allows these (Rule 2.8)
                    return;
                }
                if (eventsBeforeCancel.incrementAndGet() == EVENTS_BEFORE_CANCEL) {
                    cancelled.set(true);
                    subscriptionReference.get().cancel();
                    cancelledFuture.complete(null);
                }
            }

            @Override
            public void onError(Throwable throwable) {
                terminalSignal.completeExceptionally(throwable);
            }

            @Override
            public void onComplete() {
                terminalSignal.complete(null);
            }
        });

        // then: we received enough to cancel mid-stream...
        cancelledFuture.get(10, SECONDS);
        assertThat(eventsBeforeCancel).hasValue(EVENTS_BEFORE_CANCEL);

        // ...the stream then goes quiet (a few in-flight events are tolerated)...
        Thread.sleep(500); // let in-flight events drain after cancel()
        int deliveredWhileDraining = eventsAfterCancel.get();
        Thread.sleep(5_000); // the undribbled remainder would arrive here if cancel() were ignored
        assertThat(eventsAfterCancel)
                .as("pipeline kept emitting after cancel() — cancellation was not honored")
                .hasValue(deliveredWhileDraining);

        // ...without ever completing naturally, and far short of the full response.
        assertThat(terminalSignal)
                .as("pipeline ran to completion after cancel() instead of stopping")
                .isNotDone();
        assertThat(eventsBeforeCancel.get() + eventsAfterCancel.get())
                .as("cancellation should cut the stream well short of the full response")
                .isLessThan(TOTAL_CONTENT_CHUNKS);
    }

    private static String openAiStreamBody(int contentChunks) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < contentChunks; i++) {
            sb.append("data: ").append(contentChunk("chunk-" + i)).append("\n\n");
        }
        sb.append("data: [DONE]\n\n");
        return sb.toString();
    }

    private static String contentChunk(String content) {
        return "{\"id\":\"x\",\"object\":\"chat.completion.chunk\",\"created\":1,\"model\":\"gpt-4o-mini\","
                + "\"choices\":[{\"index\":0,\"delta\":{\"content\":\"" + content + "\"},\"finish_reason\":null}]}";
    }
}
