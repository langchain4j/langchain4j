package dev.langchain4j.http.client;

import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Internal;
import dev.langchain4j.http.client.sse.HttpErrorResponseReceived;
import dev.langchain4j.http.client.sse.HttpResponseReceived;
import dev.langchain4j.http.client.sse.HttpStreamingEvent;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.http.client.sse.ServerSentEventParser;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

@Internal
public class MockHttpClient implements HttpClient, AutoCloseable {

    private final List<HttpRequest> requests = Collections.synchronizedList(new ArrayList<>());
    private final SuccessfulHttpResponse response;
    private final List<ServerSentEvent> events;
    private RuntimeException failure;
    private boolean scriptedStreaming;
    private Supplier<?> contextProbe = () -> null;
    private final BlockingQueue<StreamExchange> exchanges = new LinkedBlockingQueue<>();
    private final AtomicInteger closeCount = new AtomicInteger();
    private volatile Runnable onClose = () -> {};

    public MockHttpClient() {
        this.response = null;
        this.events = List.of();
    }

    public MockHttpClient(SuccessfulHttpResponse response) {
        this.response = ensureNotNull(response, "response");
        this.events = List.of();
    }

    public MockHttpClient(List<ServerSentEvent> events) {
        this.response = null;
        this.events = ensureNotEmpty(events, "events");
    }

    public MockHttpClient(SuccessfulHttpResponse response, List<ServerSentEvent> events) {
        this.response = ensureNotNull(response, "response");
        this.events = ensureNotEmpty(events, "events");
    }

    public List<HttpRequest> requests() {
        return requests;
    }

    public HttpRequest request() {
        if (requests.size() != 1) {
            throw new IllegalStateException("Expected 1 request, but got: " + requests.size());
        }
        return requests.get(0);
    }

    @Override
    public SuccessfulHttpResponse execute(HttpRequest request) {
        requests.add(request);
        if (scriptedStreaming) {
            throw new IllegalStateException("This client only supports stream(request, parser)");
        }
        if (failure != null) {
            throw failure;
        }
        return response;
    }

    @Override
    public CompletableFuture<SuccessfulHttpResponse> executeAsync(HttpRequest request) {
        requests.add(request);
        if (failure != null) {
            return CompletableFuture.failedFuture(failure);
        }
        return CompletableFuture.completedFuture(response);
    }

    @Override
    public void execute(HttpRequest request, ServerSentEventParser parser, ServerSentEventListener listener) {
        requests.add(request);
        if (scriptedStreaming) {
            throw new IllegalStateException("This client only supports stream(request, parser)");
        }

        listener.onOpen(response);
        events.forEach(listener::onEvent);
        listener.onClose();
    }

    /**
     * In scripted streaming mode, every {@link #stream(HttpRequest, ServerSentEventParser)} call creates a
     * {@link StreamExchange} that the test drives step by step; otherwise the default (unsupported) streaming
     * behavior of {@link HttpClient} applies.
     */
    @Override
    public Flow.Publisher<HttpStreamingEvent> stream(HttpRequest request, ServerSentEventParser parser) {
        if (!scriptedStreaming) {
            return HttpClient.super.stream(request, parser);
        }
        requests.add(request);
        Object streamContext = contextProbe.get();
        return subscriber -> {
            StreamExchange exchange =
                    new StreamExchange(request, parser.incremental(), subscriber, streamContext, contextProbe.get());
            subscriber.onSubscribe(exchange);
            exchanges.add(exchange);
        };
    }

    /**
     * Returns the next exchange started with {@link #stream(HttpRequest, ServerSentEventParser)},
     * waiting up to 5 seconds, or throws {@link AssertionError} if there is none.
     */
    public StreamExchange nextExchange() throws InterruptedException {
        StreamExchange exchange = exchanges.poll(5, TimeUnit.SECONDS);
        if (exchange == null) {
            throw new AssertionError("Expected a request sent through stream(request, parser)");
        }
        return exchange;
    }

    /**
     * Returns whether no exchange is waiting to be taken with {@link #nextExchange()}.
     */
    public boolean hasNoPendingExchanges() {
        return exchanges.isEmpty();
    }

    /**
     * Records the value of the given probe (e.g. a {@link ThreadLocal}) on the thread that calls
     * {@code stream(...)}, subscribes, and cancels each exchange.
     */
    public MockHttpClient withContextProbe(Supplier<?> contextProbe) {
        this.contextProbe = ensureNotNull(contextProbe, "contextProbe");
        return this;
    }

    /**
     * Runs the given action every time this client is closed, before the close is counted.
     */
    public void onClose(Runnable onClose) {
        this.onClose = ensureNotNull(onClose, "onClose");
    }

    public int closeCount() {
        return closeCount.get();
    }

    @Override
    public void close() {
        onClose.run();
        closeCount.incrementAndGet();
    }

    public static MockHttpClient thatAlwaysResponds(SuccessfulHttpResponse response) {
        return new MockHttpClient(response);
    }

    public static MockHttpClient thatAlwaysResponds(List<ServerSentEvent> events) {
        return new MockHttpClient(events);
    }

    public static MockHttpClient thatAlwaysResponds(SuccessfulHttpResponse response, List<ServerSentEvent> events) {
        return new MockHttpClient(response, events);
    }

    /**
     * Creates a client that records every request and fails it with the given exception,
     * for example {@code new HttpException(503, "...")}.
     */
    public static MockHttpClient thatAlwaysFails(RuntimeException failure) {
        MockHttpClient client = new MockHttpClient();
        client.failure = ensureNotNull(failure, "failure");
        return client;
    }

    /**
     * Creates a client whose {@code stream(...)} exchanges are driven by the test, see {@link #nextExchange()}.
     * Non-streaming requests fail.
     */
    public static MockHttpClient withScriptedStreaming() {
        MockHttpClient client = new MockHttpClient();
        client.scriptedStreaming = true;
        return client;
    }

    /**
     * A streaming exchange driven by the test: response head, body bytes and the terminal signal.
     * Signals are delivered on the calling thread, regardless of demand or cancellation, to model
     * publisher callbacks that are already in flight.
     */
    public final class StreamExchange implements Flow.Subscription {

        private final HttpRequest request;
        private final ServerSentEventParser.Incremental parser;
        private final Flow.Subscriber<? super HttpStreamingEvent> subscriber;
        private final Object streamContext;
        private final Object subscribeContext;
        private volatile boolean cancelled;
        private volatile Object cancelContext;

        private StreamExchange(
                HttpRequest request,
                ServerSentEventParser.Incremental parser,
                Flow.Subscriber<? super HttpStreamingEvent> subscriber,
                Object streamContext,
                Object subscribeContext) {
            this.request = request;
            this.parser = parser;
            this.subscriber = subscriber;
            this.streamContext = streamContext;
            this.subscribeContext = subscribeContext;
        }

        public HttpRequest request() {
            return request;
        }

        public void open(int status, Map<String, List<String>> headers) {
            subscriber.onNext(new HttpResponseReceived(SuccessfulHttpResponse.builder()
                    .statusCode(status)
                    .headers(headers)
                    .build()));
        }

        /**
         * Delivers the head of an error response, before its body, like {@link HttpErrorResponseReceived} describes.
         */
        public void errorHead(int status) {
            subscriber.onNext(new HttpErrorResponseReceived(status, Map.of()));
        }

        /**
         * Feeds the body one byte at a time, splitting CRLF and multi-byte UTF-8 characters.
         */
        public void body(String body) {
            for (byte value : body.getBytes(StandardCharsets.UTF_8)) {
                parser.feed(ByteBuffer.wrap(new byte[] {value})).forEach(subscriber::onNext);
            }
        }

        public void complete() {
            parser.flush().forEach(subscriber::onNext);
            subscriber.onComplete();
        }

        public void fail(Throwable error) {
            subscriber.onError(error);
        }

        /**
         * Opens the response (with a JSON content type when there is a body), sends the body and completes.
         */
        public void respond(int status, String body) {
            open(status, body == null ? Map.of() : Map.of("Content-Type", List.of("application/json")));
            if (body != null) {
                body(body);
            }
            complete();
        }

        public boolean isCancelled() {
            return cancelled;
        }

        public Object streamContext() {
            return streamContext;
        }

        public Object subscribeContext() {
            return subscribeContext;
        }

        public Object cancelContext() {
            return cancelContext;
        }

        @Override
        public void request(long n) {}

        @Override
        public void cancel() {
            cancelContext = contextProbe.get();
            cancelled = true;
        }
    }
}
