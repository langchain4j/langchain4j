package dev.langchain4j.http.client.jdk;

import static dev.langchain4j.internal.Exceptions.unwrapCompletionException;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.TimeoutException;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.HttpErrorResponseReceived;
import dev.langchain4j.http.client.sse.HttpResponseReceived;
import dev.langchain4j.http.client.sse.HttpStreamingEvent;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.http.client.sse.ServerSentEventParser;
import java.io.ByteArrayOutputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow.Publisher;
import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.Flow.Subscription;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Cold streaming publisher that carries downstream demand through to the JDK response body.
 * Only one body batch is requested at a time; events parsed from it are consumed before another
 * batch is requested. One batch of lookahead allows completion to be discovered after the last
 * requested event, without accumulating unread batches. No thread waits for downstream demand.
 */
final class BackpressuredHttpStreamingEventPublisher implements Publisher<HttpStreamingEvent> {

    private final HttpClient client;
    private final HttpRequest request;
    private final ServerSentEventParser parser;

    BackpressuredHttpStreamingEventPublisher(HttpClient client, HttpRequest request, ServerSentEventParser parser) {
        this.client = ensureNotNull(client, "client");
        this.request = ensureNotNull(request, "request");
        this.parser = ensureNotNull(parser, "parser");
    }

    @Override
    public void subscribe(Subscriber<? super HttpStreamingEvent> subscriber) {
        Objects.requireNonNull(subscriber, "subscriber");
        StreamingSubscription subscription = new StreamingSubscription(subscriber);
        subscriber.onSubscribe(subscription);
        subscription.start();
    }

    private final class StreamingSubscription implements Subscription, Subscriber<List<ByteBuffer>> {

        private Subscriber<? super HttpStreamingEvent> downstream;
        private final AtomicInteger draining = new AtomicInteger();
        private CompletableFuture<HttpResponse<Publisher<List<ByteBuffer>>>> future;
        private Subscription bodySubscription;
        private ServerSentEventParser.Incremental incremental;
        private HttpStreamingEvent responseEvent;
        private Iterator<ByteBuffer> buffers = Collections.emptyIterator();
        private Iterator<ServerSentEvent> events = Collections.emptyIterator();
        private ByteArrayOutputStream errorBody;
        private int statusCode;
        private long demand;
        private boolean bodyRequested;
        private boolean bodyComplete;
        private boolean flushed;
        private boolean terminated;
        private Throwable failure;

        private StreamingSubscription(Subscriber<? super HttpStreamingEvent> downstream) {
            this.downstream = downstream;
        }

        private void start() {
            synchronized (this) {
                if (terminated) {
                    return;
                }
            }
            try {
                CompletableFuture<HttpResponse<Publisher<List<ByteBuffer>>>> sendFuture =
                        client.sendAsync(request, HttpResponse.BodyHandlers.ofPublisher());
                boolean cancel;
                synchronized (this) {
                    future = sendFuture;
                    cancel = terminated;
                }
                // Attach even after cancellation: a response that already won the race must have
                // its body subscribed and cancelled as well, particularly for HTTP/2 streams.
                sendFuture.whenComplete((response, throwable) -> {
                    if (throwable != null) {
                        fail(throwable);
                    } else {
                        responseReceived(response);
                    }
                });
                if (cancel) {
                    sendFuture.cancel(true);
                }
            } catch (Throwable throwable) {
                fail(throwable);
            }
        }

        private void responseReceived(HttpResponse<Publisher<List<ByteBuffer>>> response) {
            try {
                synchronized (this) {
                    if (!terminated) {
                        statusCode = response.statusCode();
                        if (statusCode >= 200 && statusCode < 300) {
                            responseEvent = new HttpResponseReceived(SuccessfulHttpResponse.builder()
                                    .statusCode(statusCode)
                                    .headers(response.headers().map())
                                    .build());
                            incremental = parser.incremental();
                        } else {
                            errorBody = new ByteArrayOutputStream();
                            if (demand > 0) {
                                responseEvent = new HttpErrorResponseReceived(
                                        statusCode, response.headers().map());
                            }
                        }
                    }
                }
            } catch (Throwable throwable) {
                fail(throwable);
            }
            try {
                response.body().subscribe(this);
            } catch (Throwable throwable) {
                fail(throwable);
            }
            drain();
        }

        @Override
        public void request(long n) {
            if (n <= 0) {
                fail(new IllegalArgumentException("Requested amount must be greater than zero"));
                return;
            }
            synchronized (this) {
                if (terminated) {
                    return;
                }
                long sum = demand + n;
                demand = sum < 0 ? Long.MAX_VALUE : sum;
            }
            drain();
        }

        @Override
        public void cancel() {
            synchronized (this) {
                terminated = true;
                downstream = null;
                clearPending();
            }
            cancelHttpRequest();
        }

        private void cancelHttpRequest() {
            CompletableFuture<?> sendFuture;
            Subscription body;
            synchronized (this) {
                sendFuture = future;
                body = bodySubscription;
            }
            if (sendFuture != null) {
                sendFuture.cancel(true);
            }
            if (body != null) {
                body.cancel();
            }
        }

        @Override
        public void onSubscribe(Subscription subscription) {
            boolean cancel;
            synchronized (this) {
                cancel = terminated || bodySubscription != null;
                if (!cancel) {
                    bodySubscription = subscription;
                }
            }
            if (cancel) {
                subscription.cancel();
            } else {
                drain();
            }
        }

        @Override
        public void onNext(List<ByteBuffer> batch) {
            synchronized (this) {
                if (terminated) {
                    return;
                }
                buffers = batch.iterator();
                bodyRequested = false;
            }
            drain();
        }

        @Override
        public void onError(Throwable throwable) {
            synchronized (this) {
                if (errorBody != null) {
                    throwable =
                            new HttpException(statusCode, "Cannot read error response body: " + throwable.getMessage());
                }
            }
            fail(throwable);
        }

        @Override
        public void onComplete() {
            synchronized (this) {
                bodyComplete = true;
            }
            drain();
        }

        private void fail(Throwable throwable) {
            synchronized (this) {
                if (terminated || failure != null) {
                    return;
                }
                Throwable cause = unwrapCompletionException(throwable);
                failure = cause instanceof HttpTimeoutException ? new TimeoutException(cause) : cause;
            }
            drain();
        }

        private void drain() {
            if (draining.getAndIncrement() != 0) {
                return;
            }
            int missed = 1;
            do {
                while (drainOne()) {
                    // All callbacks, parsing and body requests are serialized here, including
                    // synchronous request/onNext reentrancy from either side of the publisher.
                }
                missed = draining.addAndGet(-missed);
            } while (missed != 0);
        }

        private boolean drainOne() {
            Subscriber<? super HttpStreamingEvent> subscriber;
            HttpStreamingEvent event = null;
            ByteBuffer buffer = null;
            Subscription requestBody = null;
            Throwable error = null;
            boolean complete = false;
            boolean flush = false;
            synchronized (this) {
                if (terminated) {
                    return false;
                }
                subscriber = downstream;
                if (failure != null) {
                    error = failure;
                    terminated = true;
                    downstream = null;
                    clearPending();
                } else if (responseEvent != null) {
                    if (demand == 0) {
                        return false;
                    }
                    event = responseEvent;
                    responseEvent = null;
                    demand--;
                } else if (events.hasNext()) {
                    if (demand == 0) {
                        return false;
                    }
                    event = events.next();
                    demand--;
                } else if (buffers.hasNext()) {
                    buffer = buffers.next();
                } else if (bodyComplete) {
                    if (errorBody != null) {
                        error = new HttpException(statusCode, errorBody.toString(StandardCharsets.UTF_8));
                        terminated = true;
                        downstream = null;
                        clearPending();
                    } else if (!flushed) {
                        flushed = true;
                        flush = true;
                    } else {
                        terminated = true;
                        downstream = null;
                        clearPending();
                        complete = true;
                    }
                } else if (bodySubscription != null && !bodyRequested) {
                    bodyRequested = true;
                    requestBody = bodySubscription;
                } else {
                    return false;
                }
            }
            try {
                if (error != null) {
                    cancelHttpRequest();
                    subscriber.onError(error);
                } else if (complete) {
                    subscriber.onComplete();
                } else if (event != null) {
                    subscriber.onNext(event);
                } else if (buffer != null) {
                    if (errorBody != null) {
                        byte[] bytes = new byte[buffer.remaining()];
                        buffer.get(bytes);
                        errorBody.write(bytes, 0, bytes.length);
                    } else {
                        storeEvents(incremental.feed(buffer));
                    }
                } else if (flush) {
                    storeEvents(incremental.flush());
                } else {
                    requestBody.request(1);
                }
            } catch (Throwable throwable) {
                if (event != null || error != null || complete) {
                    // A subscriber throwing from a callback violates Flow's contract. Release
                    // the request without attempting another callback on that subscriber.
                    cancel();
                } else {
                    fail(throwable);
                }
            }
            return true;
        }

        private synchronized void storeEvents(List<ServerSentEvent> parsed) {
            if (!terminated) {
                events = parsed.iterator();
            }
        }

        private void clearPending() {
            responseEvent = null;
            buffers = Collections.emptyIterator();
            events = Collections.emptyIterator();
        }
    }
}
