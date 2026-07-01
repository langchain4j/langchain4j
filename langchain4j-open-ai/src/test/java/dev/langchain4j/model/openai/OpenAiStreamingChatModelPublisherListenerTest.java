package dev.langchain4j.model.openai;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.StreamingEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Verifies the {@link ChatModelListener} contract of the publisher path
 * ({@link OpenAiStreamingChatModel#chat(ChatRequest)}), deterministically via WireMock. The publisher's
 * javadoc promises:
 * <ul>
 *     <li>success — {@code onRequest} then {@code onResponse}, never {@code onError};</li>
 *     <li>provider/stream failure — {@code onRequest} then {@code onError}, never {@code onResponse};</li>
 *     <li>downstream cancels mid-stream — {@code onRequest} only;</li>
 *     <li>subscriber throws from {@code onNext} (Reactive Streams Rule 2.13 violation) — {@code onRequest}
 *         only; the stream is cancelled and no {@code onResponse}/{@code onError} fires (unlike the
 *         handler path, which routes handler exceptions to {@code onError} and keeps streaming).</li>
 * </ul>
 */
class OpenAiStreamingChatModelPublisherListenerTest {

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
    void listener_is_invoked_with_request_then_response_on_success() throws Exception {
        stubStreaming(3, 0);
        ChatModelListener listener = mock(ChatModelListener.class);

        CompletableFuture<Void> done = subscribeAndAwaitTermination(newModel(listener));
        done.get(10, SECONDS);

        InOrder inOrder = inOrder(listener);
        inOrder.verify(listener).onRequest(any());
        inOrder.verify(listener).onResponse(any());
        verify(listener, never()).onError(any());
        verifyNoMoreInteractions(listener);
    }

    @Test
    void listener_is_invoked_with_request_then_error_on_failure() throws Exception {
        wireMockServer.stubFor(post(urlEqualTo(PATH)).willReturn(aResponse()
                .withStatus(500)
                .withBody("{\"error\":{\"message\":\"boom\"}}")));
        ChatModelListener listener = mock(ChatModelListener.class);

        CompletableFuture<Void> done = subscribeAndAwaitTermination(newModel(listener));
        done.get(10, SECONDS);

        InOrder inOrder = inOrder(listener);
        inOrder.verify(listener).onRequest(any());
        inOrder.verify(listener).onError(any());
        verify(listener, never()).onResponse(any());
        verifyNoMoreInteractions(listener);
    }

    @Test
    void listener_gets_only_onRequest_when_subscription_is_cancelled_mid_stream() throws Exception {
        stubStreaming(50, 5_000); // dribbled, so we can cancel while still streaming
        ChatModelListener listener = mock(ChatModelListener.class);

        AtomicInteger events = new AtomicInteger();
        AtomicBoolean cancelled = new AtomicBoolean();
        AtomicReference<Flow.Subscription> subscriptionReference = new AtomicReference<>();
        CompletableFuture<Void> cancelledFuture = new CompletableFuture<>();

        newModel(listener).chat(request()).subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscriptionReference.set(subscription);
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(StreamingEvent event) {
                if (cancelled.get()) {
                    return;
                }
                if (events.incrementAndGet() == 3) {
                    cancelled.set(true);
                    subscriptionReference.get().cancel();
                    cancelledFuture.complete(null);
                }
            }

            @Override
            public void onError(Throwable throwable) {}

            @Override
            public void onComplete() {}
        });

        cancelledFuture.get(10, SECONDS);
        Thread.sleep(3_000); // ensure no late onResponse/onError reaches the listener

        verify(listener).onRequest(any());
        verify(listener, never()).onResponse(any());
        verify(listener, never()).onError(any());
        verifyNoMoreInteractions(listener);
    }

    @Test
    void listener_gets_only_onRequest_when_subscriber_throws_from_onNext() throws Exception {
        stubStreaming(50, 5_000); // dribbled, so the throw happens while still streaming
        ChatModelListener listener = mock(ChatModelListener.class);

        CountDownLatch firstEvent = new CountDownLatch(1);

        newModel(listener).chat(request()).subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(StreamingEvent event) {
                firstEvent.countDown();
                throw new RuntimeException("boom thrown from subscriber's onNext");
            }

            @Override
            public void onError(Throwable throwable) {}

            @Override
            public void onComplete() {}
        });

        firstEvent.await(10, SECONDS);
        Thread.sleep(3_000); // the stream must be cancelled and no listener callback must fire

        verify(listener).onRequest(any());
        verify(listener, never()).onResponse(any());
        verify(listener, never()).onError(any());
        verifyNoMoreInteractions(listener);
    }

    private OpenAiStreamingChatModel newModel(ChatModelListener listener) {
        return OpenAiStreamingChatModel.builder()
                .baseUrl("http://localhost:" + wireMockServer.port() + "/v1")
                .apiKey("test-key")
                .modelName("gpt-4o-mini")
                .listeners(List.of(listener))
                .build();
    }

    private static ChatRequest request() {
        return ChatRequest.builder()
                .messages(UserMessage.from("hi"))
                .build();
    }

    private static CompletableFuture<Void> subscribeAndAwaitTermination(OpenAiStreamingChatModel model) {
        CompletableFuture<Void> done = new CompletableFuture<>();
        model.chat(request()).subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(StreamingEvent event) {}

            @Override
            public void onError(Throwable throwable) {
                done.complete(null);
            }

            @Override
            public void onComplete() {
                done.complete(null);
            }
        });
        return done;
    }

    private void stubStreaming(int contentChunks, int dribbleMillis) {
        ResponseDefinitionBuilder response = aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "text/event-stream")
                .withBody(openAiStreamBody(contentChunks));
        if (dribbleMillis > 0) {
            response = response.withChunkedDribbleDelay(contentChunks + 1, dribbleMillis);
        }
        wireMockServer.stubFor(post(urlEqualTo(PATH)).willReturn(response));
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
