package dev.langchain4j.model.anthropic;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.StreamingEvent;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies that cancelling the {@link Flow.Subscription} of {@link AnthropicStreamingChatModel#chat(ChatRequest)}
 * <em>mid-stream</em> actually aborts the underlying SSE stream — not merely that the publisher stops forwarding
 * events. Anthropic streams via {@code httpClient.execute(request, listener)}, whose only abort mechanism is the
 * {@code StreamingHandle} the SSE parser threads through its callback contexts; the publisher path captures that
 * handle and cancels it when the downstream subscription is cancelled, which closes the response {@code
 * InputStream} and tears down the HTTP connection.
 * <p>
 * A local {@link HttpServer} dribbles the SSE response one event at a time so we can cancel while it is still
 * streaming, and — crucially — it observes the client disconnect: once the connection is torn down, its next
 * write fails. That server-side observation is what distinguishes a real abort from best-effort event-dropping
 * (where the client would keep draining the socket to completion). If cancellation were not honored, the server
 * would write the whole body without ever seeing a disconnect and this test would fail.
 */
class AnthropicStreamingChatModelPublisherCancellationTest {

    private static final int TOTAL_CONTENT_CHUNKS = 50;
    private static final int EVENTS_BEFORE_CANCEL = 3;
    private static final long SERVER_CHUNK_DELAY_MS = 50;

    private HttpServer server;
    private String baseUrl;

    private final AtomicInteger chunksWritten = new AtomicInteger();
    private final AtomicBoolean serverObservedDisconnect = new AtomicBoolean();
    private final AtomicBoolean serverWroteWholeBody = new AtomicBoolean();

    @BeforeEach
    void startServer() throws Exception {
        InetAddress loopback = InetAddress.getLoopbackAddress();
        server = HttpServer.create(new InetSocketAddress(loopback, 0), 0);
        server.createContext("/v1/messages", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0); // 0 => chunked transfer, unknown length
            try (OutputStream os = exchange.getResponseBody()) {
                writeEvent(os, "message_start", "{\"type\":\"message_start\",\"message\":{\"id\":\"msg_1\","
                        + "\"type\":\"message\",\"role\":\"assistant\",\"content\":[],"
                        + "\"model\":\"claude-haiku-4-5-20251001\",\"stop_reason\":null,"
                        + "\"usage\":{\"input_tokens\":5,\"output_tokens\":0}}}");
                writeEvent(os, "content_block_start", "{\"type\":\"content_block_start\",\"index\":0,"
                        + "\"content_block\":{\"type\":\"text\",\"text\":\"\"}}");
                for (int i = 0; i < TOTAL_CONTENT_CHUNKS; i++) {
                    writeEvent(os, "content_block_delta", "{\"type\":\"content_block_delta\",\"index\":0,"
                            + "\"delta\":{\"type\":\"text_delta\",\"text\":\"chunk-" + i + " \"}}");
                    chunksWritten.incrementAndGet();
                    Thread.sleep(SERVER_CHUNK_DELAY_MS);
                }
                writeEvent(os, "content_block_stop", "{\"type\":\"content_block_stop\",\"index\":0}");
                writeEvent(os, "message_delta", "{\"type\":\"message_delta\","
                        + "\"delta\":{\"stop_reason\":\"end_turn\"},\"usage\":{\"output_tokens\":"
                        + TOTAL_CONTENT_CHUNKS + "}}");
                writeEvent(os, "message_stop", "{\"type\":\"message_stop\"}");
                serverWroteWholeBody.set(true);
            } catch (IOException e) {
                // The client tore down the connection mid-stream: exactly what cancellation should cause.
                serverObservedDisconnect.set(true);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        server.start();
        baseUrl = "http://" + loopback.getHostAddress() + ":" + server.getAddress().getPort() + "/v1";
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void cancelling_subscription_mid_stream_aborts_the_upstream_stream() throws Exception {
        AnthropicStreamingChatModel model = AnthropicStreamingChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey("test-key")
                .modelName(AnthropicChatModelName.CLAUDE_HAIKU_4_5_20251001)
                .maxTokens(100)
                .build();

        ChatRequest request =
                ChatRequest.builder().messages(UserMessage.from("hi")).build();

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

        // ...the cancellation propagated all the way to the socket: the server's next write failed, proving the
        // HTTP connection was actually torn down rather than drained in the background.
        long deadline = System.nanoTime() + SECONDS.toNanos(10);
        while (!serverObservedDisconnect.get() && System.nanoTime() < deadline) {
            Thread.sleep(50);
        }
        assertThat(serverObservedDisconnect)
                .as("server never saw the client disconnect — the upstream stream was not aborted")
                .isTrue();

        // ...the server stopped well short of the full body...
        assertThat(serverWroteWholeBody)
                .as("server streamed the entire response — cancellation did not stop it")
                .isFalse();
        assertThat(chunksWritten.get()).isLessThan(TOTAL_CONTENT_CHUNKS);

        // ...and the publisher never completed naturally.
        assertThat(terminalSignal)
                .as("pipeline ran to completion after cancel() instead of stopping")
                .isNotDone();
    }

    private static void writeEvent(OutputStream os, String event, String data) throws IOException {
        os.write(("event: " + event + "\ndata: " + data + "\n\n").getBytes(UTF_8));
        os.flush();
    }
}
