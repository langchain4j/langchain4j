package dev.langchain4j.http.client.jdk;

import static dev.langchain4j.http.client.HttpMethod.GET;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.TimeoutException;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.sse.DefaultServerSentEventParser;
import dev.langchain4j.http.client.sse.HttpErrorResponseReceived;
import dev.langchain4j.http.client.sse.HttpResponseReceived;
import dev.langchain4j.http.client.sse.HttpStreamingEvent;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.http.client.sse.ServerSentEventParser;
import java.io.IOException;
import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class JdkHttpClientBackpressureTest {

    @Test
    void should_read_ahead_at_most_one_body_batch_when_event_demand_is_exhausted() {
        Exchange exchange = new Exchange();
        RecordingSubscriber subscriber = exchange.subscribe();

        exchange.respond(200);

        assertThat(subscriber.events).isEmpty();
        assertThat(exchange.body.requests).isEmpty();

        subscriber.subscription.request(1);

        assertThat(subscriber.events).singleElement().isInstanceOf(HttpResponseReceived.class);
        HttpResponseReceived headers = (HttpResponseReceived) subscriber.events.get(0);
        assertThat(headers.response().headers()).containsEntry("content-type", List.of("text/event-stream"));
        assertThat(exchange.body.requests).containsExactly(1L);

        subscriber.subscription.request(1);

        assertThat(exchange.body.requests).containsExactly(1L);
        exchange.body.emit("data: first\n\ndata: second\n\n");
        assertThat(subscriber.data()).containsExactly("first");
        assertThat(exchange.body.requests).containsExactly(1L);

        subscriber.subscription.request(1);

        assertThat(subscriber.data()).containsExactly("first", "second");
        assertThat(exchange.body.requests).containsExactly(1L, 1L);

        subscriber.subscription.request(1);

        assertThat(exchange.body.requests).containsExactly(1L, 1L);
    }

    @Test
    void should_deliver_more_than_the_default_buffer_size_from_one_body_batch_without_overflow() {
        Exchange exchange = new Exchange();
        RecordingSubscriber subscriber = exchange.subscribe();
        subscriber.subscription.request(2);
        exchange.respond(200);

        int eventCount = JdkHttpClient.DEFAULT_STREAMING_BUFFER_SIZE + 1;
        StringBuilder payload = new StringBuilder();
        for (int i = 0; i < eventCount; i++) {
            payload.append("data: ").append(i).append("\n\n");
        }
        exchange.body.emit(payload.toString());
        exchange.body.complete();

        assertThat(subscriber.data()).containsExactly("0");
        assertThat(subscriber.failure).isNull();
        assertThat(subscriber.completed).isFalse();
        assertThat(exchange.body.requests).containsExactly(1L);

        subscriber.subscription.request(eventCount - 1L);

        assertThat(subscriber.data()).hasSize(eventCount);
        for (int i = 0; i < eventCount; i++) {
            assertThat(subscriber.events.get(i + 1)).isEqualTo(new ServerSentEvent(null, Integer.toString(i)));
        }
        assertThat(subscriber.failure).isNull();
        assertThat(subscriber.completed).isTrue();
        assertThat(exchange.body.requests).containsExactly(1L);
    }

    @Test
    void should_continue_reading_incomplete_events_without_losing_split_utf8_bytes() {
        Exchange exchange = new Exchange();
        RecordingSubscriber subscriber = exchange.subscribe();
        subscriber.subscription.request(2);
        exchange.respond(200);

        byte[] payload = "data: café\n\n".getBytes(UTF_8);
        int split = "data: caf".getBytes(UTF_8).length + 1;
        exchange.body.emit(List.of(ByteBuffer.wrap(payload, 0, split)));

        assertThat(subscriber.data()).isEmpty();
        assertThat(exchange.body.requests).containsExactly(1L, 1L);

        exchange.body.emit(List.of(ByteBuffer.wrap(payload, split, payload.length - split)));

        assertThat(subscriber.data()).containsExactly("café");
        assertThat(exchange.body.requests).containsExactly(1L, 1L, 1L);
        exchange.body.complete();
        assertThat(subscriber.completed).isTrue();
    }

    @Test
    void should_deliver_flush_events_before_completion_and_only_with_demand() {
        Exchange exchange = new Exchange();
        RecordingSubscriber subscriber = exchange.subscribe();
        subscriber.subscription.request(2);
        exchange.respond(200);
        exchange.body.emit("data: first\n\ndata: trailing");
        exchange.body.complete();

        assertThat(subscriber.data()).containsExactly("first");
        assertThat(subscriber.completed).isFalse();

        subscriber.subscription.request(1);

        assertThat(subscriber.data()).containsExactly("first", "trailing");
        assertThat(subscriber.completed).isTrue();
        assertThat(subscriber.signals).containsExactly("next", "next", "next", "complete");
    }

    @Test
    void should_discover_completion_after_delivering_exactly_the_requested_number_of_events() {
        Exchange exchange = new Exchange();
        exchange.body.onRequest = () -> {
            if (exchange.body.requests.size() == 1) {
                exchange.body.emit("data: first\n\n");
            } else {
                exchange.body.complete();
            }
        };
        RecordingSubscriber subscriber = exchange.subscribe();
        subscriber.subscription.request(2);

        exchange.respond(200);

        assertThat(subscriber.data()).containsExactly("first");
        assertThat(subscriber.completed).isTrue();
        assertThat(subscriber.signals).containsExactly("next", "next", "complete");
    }

    @Test
    void should_cancel_the_pending_http_request_before_headers_arrive() {
        Exchange exchange = new Exchange();
        RecordingSubscriber subscriber = exchange.subscribe();

        subscriber.subscription.cancel();

        assertThat(exchange.response.isCancelled()).isTrue();
        assertThat(subscriber.signals).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void should_not_start_http_request_when_cancelled_in_on_subscribe(boolean backpressured) {
        Exchange exchange = new Exchange(backpressured);
        HttpRequest request =
                HttpRequest.builder().method(GET).url("http://localhost/stream").build();

        exchange.client.stream(request, new DefaultServerSentEventParser()).subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.cancel();
            }

            @Override
            public void onNext(HttpStreamingEvent item) {
                throw new AssertionError("No event expected");
            }

            @Override
            public void onError(Throwable throwable) {
                throw new AssertionError("No terminal signal expected", throwable);
            }

            @Override
            public void onComplete() {
                throw new AssertionError("No terminal signal expected");
            }
        });

        verify(exchange.delegate, never()).sendAsync(any(), any());
    }

    @Test
    void should_cancel_a_body_subscription_that_arrives_after_cancellation() {
        Exchange exchange = new Exchange();
        exchange.body.delaySubscription = true;
        RecordingSubscriber subscriber = exchange.subscribe();
        subscriber.subscription.request(2);
        exchange.respond(200);

        subscriber.subscription.cancel();
        exchange.body.deliverSubscription();

        assertThat(exchange.body.cancelled).isTrue();
        assertThat(exchange.body.requests).isEmpty();
        assertThat(subscriber.signals).containsExactly("next");
    }

    @Test
    void should_discard_pending_events_and_cancel_the_body_when_cancelled() {
        Exchange exchange = new Exchange();
        RecordingSubscriber subscriber = exchange.subscribe();
        subscriber.subscription.request(2);
        exchange.respond(200);
        exchange.body.emit("data: first\n\ndata: second\n\n");

        subscriber.subscription.cancel();
        subscriber.subscription.request(Long.MAX_VALUE);
        exchange.body.complete();

        assertThat(exchange.body.cancelled).isTrue();
        assertThat(subscriber.data()).containsExactly("first");
        assertThat(subscriber.signals).containsExactly("next", "next");
    }

    @Test
    void should_fail_invalid_demand_and_cancel_the_pending_request() {
        Exchange exchange = new Exchange();
        RecordingSubscriber subscriber = exchange.subscribe();

        subscriber.subscription.request(0);
        subscriber.subscription.request(-1);

        assertThat(subscriber.failure).isInstanceOf(IllegalArgumentException.class);
        assertThat(subscriber.signals).containsExactly("error");
        assertThat(exchange.response.isCancelled()).isTrue();
    }

    @Test
    void should_report_body_errors_without_waiting_for_more_demand() {
        Exchange exchange = new Exchange();
        RecordingSubscriber subscriber = exchange.subscribe();
        subscriber.subscription.request(2);
        exchange.respond(200);
        exchange.body.emit("data: first\n\ndata: second\n\n");
        IOException failure = new IOException("connection reset");

        exchange.body.subscriber.onError(failure);

        assertThat(subscriber.failure).isSameAs(failure);
        assertThat(subscriber.data()).containsExactly("first");
        assertThat(subscriber.signals).containsExactly("next", "next", "error");
    }

    @Test
    void should_cancel_the_body_and_report_parser_errors() {
        Exchange exchange = new Exchange();
        ServerSentEventParser parser = mock(ServerSentEventParser.class);
        ServerSentEventParser.Incremental incremental = mock(ServerSentEventParser.Incremental.class);
        IllegalArgumentException failure = new IllegalArgumentException("invalid event");
        when(parser.incremental()).thenReturn(incremental);
        when(incremental.feed(any())).thenThrow(failure);
        RecordingSubscriber subscriber = exchange.subscribe(parser);
        subscriber.subscription.request(2);
        exchange.respond(200);

        exchange.body.emit("invalid");

        assertThat(subscriber.failure).isSameAs(failure);
        assertThat(exchange.body.cancelled).isTrue();
        assertThat(subscriber.signals).containsExactly("next", "error");
    }

    @Test
    void should_report_parser_flush_errors_instead_of_completion() {
        Exchange exchange = new Exchange();
        ServerSentEventParser parser = mock(ServerSentEventParser.class);
        ServerSentEventParser.Incremental incremental = mock(ServerSentEventParser.Incremental.class);
        IllegalArgumentException failure = new IllegalArgumentException("incomplete event");
        when(parser.incremental()).thenReturn(incremental);
        when(incremental.flush()).thenThrow(failure);
        RecordingSubscriber subscriber = exchange.subscribe(parser);
        subscriber.subscription.request(1);
        exchange.respond(200);

        exchange.body.complete();

        assertThat(subscriber.failure).isSameAs(failure);
        assertThat(subscriber.completed).isFalse();
        assertThat(subscriber.signals).containsExactly("next", "error");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void should_report_http_errors_with_the_response_body_even_without_event_demand(boolean backpressured) {
        Exchange exchange = new Exchange(backpressured);
        RecordingSubscriber subscriber = exchange.subscribe();
        exchange.respond(401);
        exchange.body.emit("credentials expired");
        exchange.body.complete();

        assertThat(subscriber.failure).isInstanceOf(HttpException.class).hasMessage("credentials expired");
        assertThat(((HttpException) subscriber.failure).statusCode()).isEqualTo(401);
        assertThat(subscriber.signals).containsExactly("error");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void should_report_http_errors_with_the_full_response_body_when_events_are_requested(boolean backpressured) {
        Exchange exchange = new Exchange(backpressured);
        RecordingSubscriber subscriber = exchange.subscribe();
        subscriber.subscription.request(1);

        exchange.respond(401);
        assertThat(subscriber.events).singleElement().isInstanceOf(HttpErrorResponseReceived.class);
        exchange.body.emit("credentials expired");
        exchange.body.complete();

        assertThat(subscriber.failure).isInstanceOf(HttpException.class).hasMessage("credentials expired");
        assertThat(((HttpException) subscriber.failure).statusCode()).isEqualTo(401);
        assertThat(subscriber.signals).containsExactly("next", "error");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void should_emit_the_error_response_head_before_reading_the_body(boolean backpressured) {
        Exchange exchange = new Exchange(backpressured);
        RecordingSubscriber subscriber = exchange.subscribe();
        subscriber.cancelOnErrorResponseHead = true;
        subscriber.subscription.request(1);

        exchange.respond(404);

        HttpErrorResponseReceived head = (HttpErrorResponseReceived) subscriber.events.get(0);
        assertThat(head.statusCode()).isEqualTo(404);
        assertThat(head.headers()).containsEntry("Content-Type", List.of("text/event-stream"));
        assertThat(exchange.body.requests).isEmpty();
        assertThat(exchange.body.cancelled).isTrue();
        assertThat(subscriber.signals).containsExactly("next");
    }

    @Test
    void should_reject_streaming_buffer_size_with_backpressure() {
        JdkHttpClientBuilder builder =
                JdkHttpClient.builder().streamingBackpressure(true).streamingBufferSize(16);

        assertThatThrownBy(builder::build)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "streamingBufferSize cannot be combined with streamingBackpressure(true), which does not buffer events");
    }

    @Test
    void should_translate_request_timeout_errors() {
        Exchange exchange = new Exchange();
        RecordingSubscriber subscriber = exchange.subscribe();
        HttpTimeoutException failure = new HttpTimeoutException("timed out");

        exchange.response.completeExceptionally(failure);

        assertThat(subscriber.failure).isInstanceOf(TimeoutException.class).hasCause(failure);
        assertThat(subscriber.signals).containsExactly("error");
    }

    private static final class Exchange {
        private final CompletableFuture<HttpResponse<Flow.Publisher<List<ByteBuffer>>>> response =
                new CompletableFuture<>();
        private final BodyPublisher body = new BodyPublisher();
        private final java.net.http.HttpClient delegate;
        private final JdkHttpClient client;

        private Exchange() {
            this(true);
        }

        private Exchange(boolean backpressured) {
            delegate = mock(java.net.http.HttpClient.class);
            java.net.http.HttpClient.Builder builder = mock(java.net.http.HttpClient.Builder.class);
            when(builder.build()).thenReturn(delegate);
            when(delegate.<Flow.Publisher<List<ByteBuffer>>>sendAsync(any(), any()))
                    .thenReturn(response);
            client = JdkHttpClient.builder()
                    .httpClientBuilder(builder)
                    .streamingBackpressure(backpressured)
                    .build();
        }

        private RecordingSubscriber subscribe() {
            return subscribe(new DefaultServerSentEventParser());
        }

        private RecordingSubscriber subscribe(ServerSentEventParser parser) {
            RecordingSubscriber subscriber = new RecordingSubscriber();
            HttpRequest request = HttpRequest.builder()
                    .method(GET)
                    .url("http://localhost/stream")
                    .build();
            client.stream(request, parser).subscribe(subscriber);
            assertThat(subscriber.subscription).isNotNull();
            return subscriber;
        }

        @SuppressWarnings("unchecked")
        private void respond(int statusCode) {
            HttpResponse<Flow.Publisher<List<ByteBuffer>>> headers = mock(HttpResponse.class);
            when(headers.statusCode()).thenReturn(statusCode);
            when(headers.headers())
                    .thenReturn(HttpHeaders.of(
                            Map.of("content-type", List.of("text/event-stream")), (name, value) -> true));
            when(headers.body()).thenReturn(body);
            response.complete(headers);
        }
    }

    private static final class BodyPublisher implements Flow.Publisher<List<ByteBuffer>> {
        private final List<Long> requests = new ArrayList<>();
        private Flow.Subscriber<? super List<ByteBuffer>> subscriber;
        private boolean delaySubscription;
        private boolean cancelled;
        private long demand;
        private Runnable onRequest = () -> {};

        @Override
        public void subscribe(Flow.Subscriber<? super List<ByteBuffer>> subscriber) {
            this.subscriber = subscriber;
            if (!delaySubscription) {
                deliverSubscription();
            }
        }

        private void deliverSubscription() {
            subscriber.onSubscribe(new Flow.Subscription() {
                @Override
                public void request(long n) {
                    assertThat(n).isPositive();
                    requests.add(n);
                    demand = n == Long.MAX_VALUE ? Long.MAX_VALUE : demand + n;
                    onRequest.run();
                }

                @Override
                public void cancel() {
                    cancelled = true;
                }
            });
        }

        private void emit(String data) {
            emit(List.of(ByteBuffer.wrap(data.getBytes(UTF_8))));
        }

        private void emit(List<ByteBuffer> buffers) {
            assertThat(demand).as("requested body batches").isPositive();
            assertThat(cancelled).isFalse();
            if (demand != Long.MAX_VALUE) {
                demand--;
            }
            subscriber.onNext(buffers);
        }

        private void complete() {
            subscriber.onComplete();
        }
    }

    private static final class RecordingSubscriber implements Flow.Subscriber<HttpStreamingEvent> {
        private final List<HttpStreamingEvent> events = new ArrayList<>();
        private final List<String> signals = new ArrayList<>();
        private Flow.Subscription subscription;
        private Throwable failure;
        private boolean completed;
        private boolean cancelOnErrorResponseHead;

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
        }

        @Override
        public void onNext(HttpStreamingEvent item) {
            events.add(item);
            signals.add("next");
            if (cancelOnErrorResponseHead && item instanceof HttpErrorResponseReceived) {
                subscription.cancel();
            }
        }

        @Override
        public void onError(Throwable throwable) {
            failure = throwable;
            signals.add("error");
        }

        @Override
        public void onComplete() {
            completed = true;
            signals.add("complete");
        }

        private List<String> data() {
            return events.stream()
                    .filter(ServerSentEvent.class::isInstance)
                    .map(ServerSentEvent.class::cast)
                    .map(ServerSentEvent::data)
                    .toList();
        }
    }
}
