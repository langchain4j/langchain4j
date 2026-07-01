package dev.langchain4j.http.client.log;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.MockHttpClient;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.HttpResponseReceived;
import dev.langchain4j.http.client.sse.HttpStreamingEvent;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.http.client.sse.ServerSentEventParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

import static dev.langchain4j.http.client.HttpMethod.GET;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;

class LoggingHttpClientTest {

    private static final HttpRequest REQUEST =
            HttpRequest.builder().method(GET).url("http://localhost/x").build();

    private static final SuccessfulHttpResponse RESPONSE = SuccessfulHttpResponse.builder()
            .statusCode(200)
            .headers(Map.of())
            .body("hi")
            .build();

    private static final ServerSentEvent EVENT_1 = new ServerSentEvent("e1", "d1");
    private static final ServerSentEvent EVENT_2 = new ServerSentEvent("e2", "d2");
    private static final List<ServerSentEvent> EVENTS = List.of(EVENT_1, EVENT_2);

    // ---------- sync execute ----------

    @ParameterizedTest(name = "logRequests={0}, logResponses={1}")
    @CsvSource({"false,false", "true,false", "false,true", "true,true"})
    void execute_returns_response_and_logs_request_and_response_per_flags(boolean logRequests, boolean logResponses) {
        MockHttpClient delegate = MockHttpClient.thatAlwaysResponds(RESPONSE);
        Logger log = mock(Logger.class);
        LoggingHttpClient client = new LoggingHttpClient(delegate, logRequests, logResponses, log);

        SuccessfulHttpResponse result = client.execute(REQUEST);

        assertThat(result).isSameAs(RESPONSE);
        assertThat(delegate.request()).isSameAs(REQUEST);
        assertThat(requestLogCount(log)).isEqualTo(logRequests ? 1 : 0);
        assertThat(responseLogCount(log)).isEqualTo(logResponses ? 1 : 0);
        assertThat(eventLogCount(log)).isZero();
    }

    // ---------- async execute ----------

    @ParameterizedTest(name = "logRequests={0}, logResponses={1}")
    @CsvSource({"false,false", "true,false", "false,true", "true,true"})
    void executeAsync_completes_with_response_and_logs_per_flags(boolean logRequests, boolean logResponses)
            throws Exception {
        MockHttpClient delegate = MockHttpClient.thatAlwaysResponds(RESPONSE);
        Logger log = mock(Logger.class);
        LoggingHttpClient client = new LoggingHttpClient(delegate, logRequests, logResponses, log);

        SuccessfulHttpResponse result = client.executeAsync(REQUEST).get(5, SECONDS);

        assertThat(result).isSameAs(RESPONSE);
        assertThat(delegate.request()).isSameAs(REQUEST);
        assertThat(requestLogCount(log)).isEqualTo(logRequests ? 1 : 0);
        assertThat(responseLogCount(log)).isEqualTo(logResponses ? 1 : 0);
    }

    @Test
    void executeAsync_delegates_to_wrapped_client_and_passes_response_through() throws Exception {
        MockHttpClient delegate = MockHttpClient.thatAlwaysResponds(RESPONSE);
        LoggingHttpClient client = new LoggingHttpClient(delegate, true, true);

        SuccessfulHttpResponse result = client.executeAsync(REQUEST).get(5, SECONDS);

        assertThat(result).isSameAs(RESPONSE);
        assertThat(delegate.request()).isSameAs(REQUEST);
    }

    @Test
    void executeAsync_propagates_failure_from_wrapped_client() {
        RuntimeException failure = new RuntimeException("boom");
        HttpClient delegate = new HttpClient() {
            @Override
            public SuccessfulHttpResponse execute(HttpRequest request) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void execute(HttpRequest request, ServerSentEventParser parser, ServerSentEventListener listener) {
                throw new UnsupportedOperationException();
            }

            @Override
            public CompletableFuture<SuccessfulHttpResponse> executeAsync(HttpRequest request) {
                return CompletableFuture.failedFuture(failure);
            }
        };
        LoggingHttpClient client = new LoggingHttpClient(delegate, true, true);

        CompletableFuture<SuccessfulHttpResponse> future = client.executeAsync(REQUEST);

        assertThatThrownBy(() -> future.get(5, SECONDS)).hasCause(failure);
    }

    // ---------- streaming (handler-based execute) ----------

    @ParameterizedTest(name = "logRequests={0}, logResponses={1}")
    @CsvSource({"false,false", "true,false", "false,true", "true,true"})
    void execute_streaming_delivers_events_to_listener_and_logs_per_flags(boolean logRequests, boolean logResponses) {
        RecordingHttpClient delegate = new RecordingHttpClient(RESPONSE, EVENTS);
        Logger log = mock(Logger.class);
        LoggingHttpClient client = new LoggingHttpClient(delegate, logRequests, logResponses, log);

        RecordingListener listener = new RecordingListener();
        client.execute(REQUEST, new DummyParser(), listener);

        // events are delivered to the caller's listener regardless of logging config
        assertThat(listener.opened).isSameAs(RESPONSE);
        assertThat(listener.events).containsExactly(EVENT_1, EVENT_2);
        assertThat(listener.closed).isTrue();

        assertThat(requestLogCount(log)).isEqualTo(logRequests ? 1 : 0);
        assertThat(responseLogCount(log)).isEqualTo(logResponses ? 1 : 0); // onOpen
        assertThat(eventLogCount(log)).isEqualTo(logResponses ? EVENTS.size() : 0);
    }

    @ParameterizedTest(name = "logResponses={0}")
    @CsvSource({"false", "true"})
    void execute_streaming_wraps_listener_only_when_logging_responses(boolean logResponses) {
        RecordingHttpClient delegate = new RecordingHttpClient(RESPONSE, EVENTS);
        LoggingHttpClient client = new LoggingHttpClient(delegate, true, logResponses, mock(Logger.class));

        RecordingListener listener = new RecordingListener();
        client.execute(REQUEST, new DummyParser(), listener);

        if (logResponses) {
            // wrapped, so the delegate receives a different (logging) listener
            assertThat(delegate.receivedListener).isNotSameAs(listener);
        } else {
            // nothing to log on the response path -> caller's listener passed straight through
            assertThat(delegate.receivedListener).isSameAs(listener);
        }
    }

    @Test
    void execute_streaming_propagates_error_to_listener() {
        RuntimeException failure = new RuntimeException("boom");
        RecordingHttpClient delegate = new RecordingHttpClient(failure);
        LoggingHttpClient client = new LoggingHttpClient(delegate, true, true, mock(Logger.class));

        RecordingListener listener = new RecordingListener();
        client.execute(REQUEST, new DummyParser(), listener);

        assertThat(listener.error).isSameAs(failure);
        assertThat(listener.opened).isNull();
    }

    // ---------- streaming (reactive publisher) ----------

    @ParameterizedTest(name = "logRequests={0}, logResponses={1}")
    @CsvSource({"false,false", "true,false", "false,true", "true,true"})
    void stream_delivers_events_to_subscriber_and_logs_per_flags(boolean logRequests, boolean logResponses)
            throws Exception {
        RecordingHttpClient delegate = new RecordingHttpClient(RESPONSE, EVENTS);
        Logger log = mock(Logger.class);
        LoggingHttpClient client = new LoggingHttpClient(delegate, logRequests, logResponses, log);

        List<HttpStreamingEvent> received = collect(client.stream(REQUEST));

        // response head + each SSE, in order, regardless of logging config
        assertThat(received).hasSize(1 + EVENTS.size());
        assertThat(received.get(0)).isInstanceOf(HttpResponseReceived.class);
        assertThat(received.subList(1, received.size())).containsExactly(EVENT_1, EVENT_2);

        assertThat(requestLogCount(log)).isEqualTo(logRequests ? 1 : 0);
        assertThat(responseLogCount(log)).isEqualTo(logResponses ? 1 : 0); // HttpResponseReceived
        assertThat(eventLogCount(log)).isEqualTo(logResponses ? EVENTS.size() : 0);
    }

    @Test
    void stream_propagates_error_to_subscriber() {
        RuntimeException failure = new RuntimeException("boom");
        RecordingHttpClient delegate = new RecordingHttpClient(failure);
        LoggingHttpClient client = new LoggingHttpClient(delegate, true, true, mock(Logger.class));

        assertThatThrownBy(() -> collect(client.stream(REQUEST))).hasCause(failure);
    }

    // ---------- helpers ----------

    private static List<HttpStreamingEvent> collect(Flow.Publisher<HttpStreamingEvent> publisher) throws Exception {
        List<HttpStreamingEvent> received = new ArrayList<>();
        CompletableFuture<Void> done = new CompletableFuture<>();
        publisher.subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(HttpStreamingEvent item) {
                received.add(item);
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
        done.get(5, SECONDS);
        return received;
    }

    private static long requestLogCount(Logger log) {
        return infoCount(log, "HTTP request:");
    }

    private static long responseLogCount(Logger log) {
        return infoCount(log, "HTTP response:");
    }

    private static long infoCount(Logger log, String messagePrefix) {
        return mockingDetails(log).getInvocations().stream()
                .filter(invocation -> "info".equals(invocation.getMethod().getName()))
                .filter(invocation -> invocation.getArguments().length > 0
                        && String.valueOf(invocation.getArguments()[0]).startsWith(messagePrefix))
                .count();
    }

    /** Streamed events are logged via {@code log.debug("{}", event)}; nothing else in the client logs at debug. */
    private static long eventLogCount(Logger log) {
        return mockingDetails(log).getInvocations().stream()
                .filter(invocation -> "debug".equals(invocation.getMethod().getName()))
                .count();
    }

    private static final class RecordingListener implements ServerSentEventListener {
        private SuccessfulHttpResponse opened;
        private final List<ServerSentEvent> events = new ArrayList<>();
        private boolean closed;
        private Throwable error;

        @Override
        public void onOpen(SuccessfulHttpResponse response) {
            opened = response;
        }

        @Override
        public void onEvent(ServerSentEvent event) {
            events.add(event);
        }

        @Override
        public void onError(Throwable throwable) {
            error = throwable;
        }

        @Override
        public void onClose() {
            closed = true;
        }
    }

    /**
     * Delegate that records the listener it was handed (to assert the "don't wrap when not logging responses"
     * optimization) and drives it with a fixed response + events, or an error.
     */
    private static final class RecordingHttpClient implements HttpClient {

        private final SuccessfulHttpResponse response;
        private final List<ServerSentEvent> events;
        private final Throwable failure;
        private volatile ServerSentEventListener receivedListener;

        RecordingHttpClient(SuccessfulHttpResponse response, List<ServerSentEvent> events) {
            this(response, events, null);
        }

        RecordingHttpClient(Throwable failure) {
            this(null, List.of(), failure);
        }

        private RecordingHttpClient(SuccessfulHttpResponse response, List<ServerSentEvent> events, Throwable failure) {
            this.response = response;
            this.events = events;
            this.failure = failure;
        }

        @Override
        public SuccessfulHttpResponse execute(HttpRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<SuccessfulHttpResponse> executeAsync(HttpRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void execute(HttpRequest request, ServerSentEventParser parser, ServerSentEventListener listener) {
            this.receivedListener = listener;
            if (failure != null) {
                listener.onError(failure);
                return;
            }
            listener.onOpen(response);
            events.forEach(listener::onEvent);
            listener.onClose();
        }

        @Override
        public Flow.Publisher<HttpStreamingEvent> stream(HttpRequest request, ServerSentEventParser parser) {
            return subscriber -> {
                subscriber.onSubscribe(new Flow.Subscription() {
                    @Override
                    public void request(long n) {}

                    @Override
                    public void cancel() {}
                });
                if (failure != null) {
                    subscriber.onError(failure);
                    return;
                }
                subscriber.onNext(new HttpResponseReceived(response));
                events.forEach(subscriber::onNext);
                subscriber.onComplete();
            };
        }
    }

    private static final class DummyParser implements ServerSentEventParser {
        @Override
        public void parse(java.io.InputStream httpResponseBody, ServerSentEventListener listener) {}
    }
}
