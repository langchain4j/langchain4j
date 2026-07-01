package dev.langchain4j.http.client;

import static dev.langchain4j.http.client.HttpMethod.POST;
import static java.util.Collections.synchronizedList;
import static java.util.Collections.synchronizedSet;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.joining;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import dev.langchain4j.exception.HttpException;
import dev.langchain4j.http.client.sse.DefaultServerSentEventParser;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.http.client.sse.ServerSentEventContext;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.http.client.sse.ServerSentEventParser;
import dev.langchain4j.http.client.sse.HttpResponseReceived;
import dev.langchain4j.http.client.sse.HttpStreamingEvent;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InOrder;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public abstract class HttpClientIT {

    private static final String OPENAI_API_KEY = System.getenv("OPENAI_API_KEY");

    protected abstract List<HttpClient> clients();

    /** How to invoke a single (non-streaming) request: blocking {@link HttpClient#execute} or
     * non-blocking {@link HttpClient#executeAsync}. Lets the single-response tests run in both modes. */
    protected enum ExecutionMode {
        SYNC,
        ASYNC
    }

    /**
     * Executes the request in the given mode. For {@link ExecutionMode#ASYNC} the {@link CompletableFuture}
     * is awaited and any {@link ExecutionException} is unwrapped, so callers observe the same exception
     * (e.g. {@link HttpException}) that the synchronous path throws.
     */
    private static SuccessfulHttpResponse execute(HttpRequest request, HttpClient client, ExecutionMode mode) {
        if (mode == ExecutionMode.SYNC) {
            return client.execute(request);
        }
        try {
            return client.executeAsync(request).get(30, SECONDS);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new RuntimeException(cause != null ? cause : e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (TimeoutException e) {
            throw new RuntimeException(e);
        }
    }

    /** Which streaming API to drive: the callback {@link HttpClient#execute(HttpRequest, ServerSentEventParser,
     * ServerSentEventListener)} (listener) or the {@link HttpClient#stream(HttpRequest)}
     * (publisher). Lets the common streaming tests run against both. */
    protected enum StreamingMode {
        LISTENER,
        PUBLISHER
    }

    protected record StreamingResult(
            SuccessfulHttpResponse response,
            List<ServerSentEvent> events,
            Set<Thread> threads,
            ServerSentEventListener listenerSpy) {}

    /**
     * Drives a streaming request through the given API and collects the response, the events and the
     * threads they were delivered on, blocking until the stream terminates. If the stream fails, the error
     * is re-thrown (unwrapped) so both modes surface failures the same way.
     */
    private static StreamingResult stream(HttpClient client, HttpRequest request, StreamingMode mode) {
        AtomicReference<SuccessfulHttpResponse> response = new AtomicReference<>();
        List<ServerSentEvent> events = synchronizedList(new ArrayList<>());
        Set<Thread> threads = synchronizedSet(new HashSet<>());
        CompletableFuture<Void> done = new CompletableFuture<>();

        ServerSentEventListener listenerSpy = null;
        if (mode == StreamingMode.LISTENER) {
            ServerSentEventListener listener = new ServerSentEventListener() {
                @Override
                public void onOpen(SuccessfulHttpResponse r) {
                    threads.add(Thread.currentThread());
                    response.set(r);
                }

                @Override
                public void onEvent(ServerSentEvent e) {
                    threads.add(Thread.currentThread());
                    events.add(e);
                }

                @Override
                public void onEvent(ServerSentEvent e, ServerSentEventContext context) {
                    threads.add(Thread.currentThread());
                    events.add(e);
                }

                @Override
                public void onError(Throwable t) {
                    threads.add(Thread.currentThread());
                    done.completeExceptionally(t);
                }

                @Override
                public void onClose() {
                    threads.add(Thread.currentThread());
                    done.complete(null);
                }
            };
            listenerSpy = spy(listener);
            client.execute(request, new DefaultServerSentEventParser(), listenerSpy);
        } else {
            client.stream(request).subscribe(new Flow.Subscriber<>() {
                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    subscription.request(Long.MAX_VALUE);
                }

                @Override
                public void onNext(HttpStreamingEvent item) {
                    threads.add(Thread.currentThread());
                    if (item instanceof HttpResponseReceived r) {
                        response.set(r.response());
                    } else if (item instanceof ServerSentEvent e) {
                        events.add(e);
                    }
                }

                @Override
                public void onError(Throwable t) {
                    threads.add(Thread.currentThread());
                    done.completeExceptionally(t);
                }

                @Override
                public void onComplete() {
                    threads.add(Thread.currentThread());
                    done.complete(null);
                }
            });
        }

        try {
            done.get(30, SECONDS);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new RuntimeException(cause != null ? cause : e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (TimeoutException e) {
            throw new RuntimeException(e);
        }

        return new StreamingResult(response.get(), events, threads, listenerSpy);
    }

    @ParameterizedTest
    @EnumSource(ExecutionMode.class)
    void should_return_successful_http_response(ExecutionMode mode) {

        for (HttpClient client : clients()) {

            // given
            HttpRequest request = HttpRequest.builder()
                    .method(POST)
                    .url("https://api.openai.com/v1/chat/completions")
                    .addHeader("Authorization", "Bearer " + OPENAI_API_KEY)
                    .addHeader("Content-Type", "application/json")
                    .body(
                            """
                                    {
                                        "model": "gpt-4o-mini",
                                        "messages": [
                                            {
                                                "role" : "user",
                                                "content" : "What is the capital of Germany?"
                                            }
                                        ]
                                    }
                                    """)
                    .build();

            // when
            SuccessfulHttpResponse response = execute(request, client, mode);

            // then
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.headers()).isNotEmpty();
            assertThat(response.body()).contains("Berlin");
        }
    }

    @Test
    void should_deliver_response_off_the_calling_thread_executeAsync() throws Exception {

        for (HttpClient client : clients()) {

            // given
            HttpRequest request = HttpRequest.builder()
                    .method(POST)
                    .url("https://api.openai.com/v1/chat/completions")
                    .addHeader("Authorization", "Bearer " + OPENAI_API_KEY)
                    .addHeader("Content-Type", "application/json")
                    .body(
                            """
                                    {
                                        "model": "gpt-4o-mini",
                                        "messages": [
                                            {
                                                "role" : "user",
                                                "content" : "What is the capital of Germany?"
                                            }
                                        ]
                                    }
                                    """)
                    .build();

            Thread callerThread = Thread.currentThread();
            AtomicReference<Thread> completionThread = new AtomicReference<>();

            // when
            SuccessfulHttpResponse response = client.executeAsync(request)
                    .whenComplete((r, t) -> completionThread.set(Thread.currentThread()))
                    .get(30, SECONDS);

            // then: the response was delivered asynchronously, so the caller was never blocked.
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(completionThread.get())
                    .as("the response must be delivered off the calling thread")
                    .isNotNull()
                    .isNotEqualTo(callerThread);
        }
    }

    @ParameterizedTest
    @EnumSource(ExecutionMode.class)
    void should_throw_400(ExecutionMode mode) {

        for (HttpClient client : clients()) {

            // given
            String invalidBody =
                    """
                            {
                                "model": "gpt-4o-mini"
                            }
                            """; // missing field "messages"

            HttpRequest request = HttpRequest.builder()
                    .method(POST)
                    .url("https://api.openai.com/v1/chat/completions")
                    .addHeader("Authorization", "Bearer " + OPENAI_API_KEY)
                    .addHeader("Content-Type", "application/json")
                    .body(invalidBody)
                    .build();

            // when
            try {
                execute(request, client, mode);
                fail("Should have thrown an exception");
            } catch (Exception e) {
                // then
                assertThat(e).isExactlyInstanceOf(HttpException.class);
                HttpException httpException = (HttpException) e;
                assertThat(httpException.statusCode()).isEqualTo(400);
                assertThat(httpException.getMessage()).contains("Missing required parameter: 'messages'");
            }
        }
    }

    @ParameterizedTest
    @EnumSource(ExecutionMode.class)
    void should_throw_401(ExecutionMode mode) {

        for (HttpClient client : clients()) {

            // given
            String incorrectApiKey = "banana";

            HttpRequest request = HttpRequest.builder()
                    .method(POST)
                    .url("https://api.openai.com/v1/chat/completions")
                    .addHeader("Authorization", "Bearer " + incorrectApiKey)
                    .addHeader("Content-Type", "application/json")
                    .body(
                            """
                                    {
                                        "model": "gpt-4o-mini",
                                        "messages": [
                                            {
                                                "role" : "user",
                                                "content" : "What is the capital of Germany?"
                                            }
                                        ]
                                    }
                                    """)
                    .build();

            // when
            try {
                execute(request, client, mode);
                fail("Should have thrown an exception");
            } catch (Exception e) {
                // then
                assertThat(e).isExactlyInstanceOf(HttpException.class);
                HttpException httpException = (HttpException) e;
                assertThat(httpException.statusCode()).isEqualTo(401);
                assertThat(httpException.getMessage()).contains("Incorrect API key provided");
            }
        }
    }

    @ParameterizedTest
    @EnumSource(StreamingMode.class)
    void should_stream_successful_response(StreamingMode mode) {

        for (HttpClient client : clients()) {

            // given
            HttpRequest request = HttpRequest.builder()
                    .method(POST)
                    .url("https://api.openai.com/v1/chat/completions")
                    .addHeader("Authorization", "Bearer " + OPENAI_API_KEY)
                    .addHeader("Content-Type", "application/json")
                    .body(
                            """
                                    {
                                        "model": "gpt-4o-mini",
                                        "messages": [
                                            {
                                                "role" : "user",
                                                "content" : "What is the capital of Germany?"
                                            }
                                        ],
                                        "stream": true
                                    }
                                    """)
                    .build();

            // when
            StreamingResult result = stream(client, request, mode);

            // then
            assertThat(result.response()).isNotNull();
            assertThat(result.response().statusCode()).isEqualTo(200);
            assertThat(result.response().headers()).isNotEmpty();
            assertThat(result.response().body()).isNull();

            assertThat(result.events()).isNotEmpty();
            assertThat(result.events().stream().map(ServerSentEvent::data).collect(joining("")))
                    .contains("Berlin");

            // Events are delivered off the calling thread in both modes; the listener path additionally
            // guarantees single-threaded delivery, whereas the publisher may use several worker threads.
            assertThat(result.threads()).isNotEmpty().doesNotContain(Thread.currentThread());
            if (mode == StreamingMode.LISTENER) {
                // the listener path delivers everything on a single thread, with callbacks in order
                assertThat(result.threads()).hasSize(1);

                ServerSentEventListener listenerSpy = result.listenerSpy();
                InOrder inOrder = inOrder(listenerSpy);
                inOrder.verify(listenerSpy, times(1)).onOpen(any());
                inOrder.verify(listenerSpy, atLeastOnce()).onEvent(any(), any());
                inOrder.verify(listenerSpy, times(1)).onClose();
                inOrder.verifyNoMoreInteractions();
                verifyNoMoreInteractions(listenerSpy);
            }
        }
    }

    @ParameterizedTest
    @EnumSource(StreamingMode.class)
    void should_cancel_streaming(StreamingMode mode) throws Exception {

        int eventsBeforeCancellation = 5;

        for (HttpClient client : clients()) {

            // given
            HttpRequest request = HttpRequest.builder()
                    .method(POST)
                    .url("https://api.openai.com/v1/chat/completions")
                    .addHeader("Authorization", "Bearer " + OPENAI_API_KEY)
                    .addHeader("Content-Type", "application/json")
                    .body(
                            """
                                    {
                                        "model": "gpt-4o-mini",
                                        "messages": [
                                            {
                                                "role" : "user",
                                                "content" : "Tell me a story about kittens"
                                            }
                                        ],
                                        "stream": true
                                    }
                                    """)
                    .build();

            // when: cancel after N events (listener -> parsingHandle().cancel(); publisher -> Subscription.cancel())
            AtomicInteger eventCounter = new AtomicInteger();
            CompletableFuture<Void> stopped = new CompletableFuture<>();

            ServerSentEventListener listenerSpy = null;
            if (mode == StreamingMode.LISTENER) {
                ServerSentEventListener listener = new ServerSentEventListener() {
                    @Override
                    public void onOpen(SuccessfulHttpResponse response) {}

                    @Override
                    public void onEvent(ServerSentEvent event) {}

                    @Override
                    public void onEvent(ServerSentEvent event, ServerSentEventContext context) {
                        if (eventCounter.incrementAndGet() >= eventsBeforeCancellation) {
                            context.parsingHandle().cancel();
                        }
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        stopped.completeExceptionally(throwable);
                    }

                    @Override
                    public void onClose() {
                        stopped.complete(null);
                    }
                };
                listenerSpy = spy(listener);
                client.execute(request, new DefaultServerSentEventParser(), listenerSpy);
            } else {
                AtomicReference<Flow.Subscription> subscription = new AtomicReference<>();
                client.stream(request).subscribe(new Flow.Subscriber<>() {
                    @Override
                    public void onSubscribe(Flow.Subscription s) {
                        subscription.set(s);
                        s.request(Long.MAX_VALUE);
                    }

                    @Override
                    public void onNext(HttpStreamingEvent item) {
                        if (item instanceof ServerSentEvent
                                && eventCounter.incrementAndGet() >= eventsBeforeCancellation) {
                            subscription.get().cancel();
                            stopped.complete(null);
                        }
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        stopped.completeExceptionally(throwable);
                    }

                    @Override
                    public void onComplete() {
                        stopped.complete(null);
                    }
                });
            }

            stopped.get(30, SECONDS);

            // then: the stream was cancelled around the expected point (a few in-flight events are tolerated)
            assertThat(eventCounter.get()).isGreaterThanOrEqualTo(eventsBeforeCancellation);

            if (mode == StreamingMode.LISTENER) {
                // the listener path cancels deterministically: exactly N events, in order, then onClose
                InOrder inOrder = inOrder(listenerSpy);
                inOrder.verify(listenerSpy, times(1)).onOpen(any());
                inOrder.verify(listenerSpy, times(eventsBeforeCancellation)).onEvent(any(), any());
                inOrder.verify(listenerSpy, times(1)).onClose();
                inOrder.verifyNoMoreInteractions();
                verifyNoMoreInteractions(listenerSpy);
            }
        }
    }

    @ParameterizedTest
    @EnumSource(StreamingMode.class)
    void should_stream_response_with_double_newline(StreamingMode mode) {

        for (HttpClient client : clients()) {

            // given
            HttpRequest request = HttpRequest.builder()
                    .method(POST)
                    .url("https://api.openai.com/v1/chat/completions")
                    .addHeader("Authorization", "Bearer " + OPENAI_API_KEY)
                    .addHeader("Content-Type", "application/json")
                    .body(
                            """
                                    {
                                        "model": "gpt-4o-mini",
                                        "messages": [
                                            {
                                                "role" : "user",
                                                "content" : "What is the capital of Germany? What is a capital of France? Your answers must be separated by a double newline!"
                                            }
                                        ],
                                        "temperature": 0.0,
                                        "stream": true
                                    }
                                    """)
                    .build();

            // when
            StreamingResult result = stream(client, request, mode);

            // then
            assertThat(result.response()).isNotNull();
            assertThat(result.response().statusCode()).isEqualTo(200);
            assertThat(result.response().headers()).isNotEmpty();
            assertThat(result.response().body()).isNull();

            assertThat(result.events()).isNotEmpty();
            assertThat(result.events().stream().map(ServerSentEvent::data).collect(joining("")))
                    .contains("Berlin", "Paris", "\\n\\n");

            assertThat(result.threads()).isNotEmpty().doesNotContain(Thread.currentThread());
            if (mode == StreamingMode.LISTENER) {
                // the listener path delivers everything on a single thread, with callbacks in order
                assertThat(result.threads()).hasSize(1);

                ServerSentEventListener listenerSpy = result.listenerSpy();
                InOrder inOrder = inOrder(listenerSpy);
                inOrder.verify(listenerSpy, times(1)).onOpen(any());
                inOrder.verify(listenerSpy, atLeastOnce()).onEvent(any(), any());
                inOrder.verify(listenerSpy, times(1)).onClose();
                inOrder.verifyNoMoreInteractions();
                verifyNoMoreInteractions(listenerSpy);
            }
        }
    }

    @ParameterizedTest
    @EnumSource(StreamingMode.class)
    void should_deliver_error_when_streaming_400(StreamingMode mode) {

        for (HttpClient client : clients()) {

            // given
            String invalidBody =
                    """
                            {
                                "model": "gpt-4o-mini",
                                "stream": true
                            }
                            """; // missing field "messages"

            HttpRequest request = HttpRequest.builder()
                    .method(POST)
                    .url("https://api.openai.com/v1/chat/completions")
                    .addHeader("Authorization", "Bearer " + OPENAI_API_KEY)
                    .addHeader("Content-Type", "application/json")
                    .body(invalidBody)
                    .build();

            // when-then: the error is delivered through the stream (onError), surfaced here as HttpException.
            assertThatThrownBy(() -> stream(client, request, mode))
                    .isExactlyInstanceOf(HttpException.class)
                    .hasMessageContaining("Missing required parameter: 'messages'")
                    .extracting("statusCode")
                    .isEqualTo(400);
        }
    }

    @Test
    void should_not_fail_when_listener_onOpen_throws_exception() throws Exception {

        for (HttpClient client : clients()) {

            // given
            HttpRequest request = HttpRequest.builder()
                    .method(POST)
                    .url("https://api.openai.com/v1/chat/completions")
                    .addHeader("Authorization", "Bearer " + OPENAI_API_KEY)
                    .addHeader("Content-Type", "application/json")
                    .body(
                            """
                                    {
                                        "model": "gpt-4o-mini",
                                        "messages": [
                                            {
                                                "role" : "user",
                                                "content" : "What is the capital of Germany?"
                                            }
                                        ],
                                        "stream": true
                                    }
                                    """)
                    .build();

            // when
            AtomicReference<SuccessfulHttpResponse> response = new AtomicReference<>();
            List<ServerSentEvent> events = synchronizedList(new ArrayList<>());
            List<Throwable> errors = synchronizedList(new ArrayList<>());
            Set<Thread> threads = synchronizedSet(new HashSet<>());
            CompletableFuture<Void> future = new CompletableFuture<>();

            ServerSentEventListener listener = new ServerSentEventListener() {

                @Override
                public void onOpen(SuccessfulHttpResponse successfulHttpResponse) {
                    response.set(successfulHttpResponse);
                    threads.add(Thread.currentThread());

                    throw new RuntimeException("Unexpected exception in onOpen()");
                }

                @Override
                public void onEvent(ServerSentEvent event) {
                    events.add(event);
                    threads.add(Thread.currentThread());
                }

                @Override
                public void onEvent(ServerSentEvent event, ServerSentEventContext context) {
                    events.add(event);
                    threads.add(Thread.currentThread());
                }

                @Override
                public void onError(Throwable throwable) {
                    errors.add(throwable);
                    threads.add(Thread.currentThread());
                }

                @Override
                public void onClose() {
                    threads.add(Thread.currentThread());
                    future.complete(null);
                }
            };
            ServerSentEventListener spyListener = spy(listener);
            client.execute(request, new DefaultServerSentEventParser(), spyListener);
            future.get(30, SECONDS);
            Thread.sleep(5_000);

            // then
            assertThat(response.get()).isNotNull();
            assertThat(events).isNotEmpty();
            assertThat(errors).isEmpty();

            assertThat(threads).hasSize(1);
            assertThat(threads.iterator().next()).isNotEqualTo(Thread.currentThread());

            InOrder inOrder = inOrder(spyListener);
            inOrder.verify(spyListener, times(1)).onOpen(any());
            inOrder.verify(spyListener, atLeastOnce()).onEvent(any(), any());
            inOrder.verify(spyListener, times(1)).onClose();
            inOrder.verifyNoMoreInteractions();
            verifyNoMoreInteractions(spyListener);
        }
    }

    @Test
    void should_not_fail_when_listener_onEvent_throws_exception() throws Exception {

        for (HttpClient client : clients()) {

            // given
            HttpRequest request = HttpRequest.builder()
                    .method(POST)
                    .url("https://api.openai.com/v1/chat/completions")
                    .addHeader("Authorization", "Bearer " + OPENAI_API_KEY)
                    .addHeader("Content-Type", "application/json")
                    .body(
                            """
                                    {
                                        "model": "gpt-4o-mini",
                                        "messages": [
                                            {
                                                "role" : "user",
                                                "content" : "What is the capital of Germany?"
                                            }
                                        ],
                                        "stream": true
                                    }
                                    """)
                    .build();

            // when
            AtomicReference<SuccessfulHttpResponse> response = new AtomicReference<>();
            List<ServerSentEvent> events = synchronizedList(new ArrayList<>());
            List<Throwable> errors = synchronizedList(new ArrayList<>());
            Set<Thread> threads = synchronizedSet(new HashSet<>());
            CompletableFuture<Void> future = new CompletableFuture<>();

            ServerSentEventListener listener = new ServerSentEventListener() {

                @Override
                public void onOpen(SuccessfulHttpResponse successfulHttpResponse) {
                    response.set(successfulHttpResponse);
                    threads.add(Thread.currentThread());
                }

                @Override
                public void onEvent(ServerSentEvent event) {
                    events.add(event);
                    threads.add(Thread.currentThread());

                    throw new RuntimeException("Unexpected exception in onEvent()");
                }

                @Override
                public void onEvent(ServerSentEvent event, ServerSentEventContext context) {
                    events.add(event);
                    threads.add(Thread.currentThread());

                    throw new RuntimeException("Unexpected exception in onEvent()");
                }

                @Override
                public void onError(Throwable throwable) {
                    errors.add(throwable);
                    threads.add(Thread.currentThread());
                }

                @Override
                public void onClose() {
                    threads.add(Thread.currentThread());
                    future.complete(null);
                }
            };
            ServerSentEventListener spyListener = spy(listener);
            client.execute(request, new DefaultServerSentEventParser(), spyListener);
            future.get(30, SECONDS);
            Thread.sleep(5_000);

            // then
            assertThat(response.get()).isNotNull();
            assertThat(events).hasSizeGreaterThan(1);
            assertThat(errors).isEmpty();

            assertThat(threads).hasSize(1);
            assertThat(threads.iterator().next()).isNotEqualTo(Thread.currentThread());

            InOrder inOrder = inOrder(spyListener);
            inOrder.verify(spyListener, times(1)).onOpen(any());
            inOrder.verify(spyListener, times(events.size())).onEvent(any(), any());
            inOrder.verify(spyListener, times(1)).onClose();
            inOrder.verifyNoMoreInteractions();
            verifyNoMoreInteractions(spyListener);
        }
    }

    @Test
    void should_not_fail_when_listener_onError_throws_exception() throws Exception {

        for (HttpClient client : clients()) {

            // given
            String incorrectApiKey = "banana";

            HttpRequest request = HttpRequest.builder()
                    .method(POST)
                    .url("https://api.openai.com/v1/chat/completions")
                    .addHeader("Authorization", "Bearer " + incorrectApiKey)
                    .addHeader("Content-Type", "application/json")
                    .body(
                            """
                                    {
                                        "model": "gpt-4o-mini",
                                        "messages": [
                                            {
                                                "role" : "user",
                                                "content" : "What is the capital of Germany?"
                                            }
                                        ],
                                        "stream": true
                                    }
                                    """)
                    .build();

            // when
            AtomicReference<SuccessfulHttpResponse> response = new AtomicReference<>();
            List<ServerSentEvent> events = synchronizedList(new ArrayList<>());
            List<Throwable> errors = synchronizedList(new ArrayList<>());
            Set<Thread> threads = synchronizedSet(new HashSet<>());
            CompletableFuture<Void> future = new CompletableFuture<>();

            ServerSentEventListener listener = new ServerSentEventListener() {

                @Override
                public void onOpen(SuccessfulHttpResponse successfulHttpResponse) {
                    response.set(successfulHttpResponse);
                    threads.add(Thread.currentThread());
                }

                @Override
                public void onEvent(ServerSentEvent event) {
                    events.add(event);
                    threads.add(Thread.currentThread());
                }

                @Override
                public void onError(Throwable throwable) {
                    errors.add(throwable);
                    threads.add(Thread.currentThread());

                    future.complete(null);
                    throw new RuntimeException("Unexpected exception in onError()");
                }

                @Override
                public void onClose() {
                    threads.add(Thread.currentThread());
                }
            };
            ServerSentEventListener spyListener = spy(listener);
            client.execute(request, new DefaultServerSentEventParser(), spyListener);
            future.get(30, SECONDS);
            Thread.sleep(5_000);

            // then
            assertThat(response.get()).isNull();
            assertThat(events).isEmpty();
            assertThat(errors).hasSize(1);
            assertThat(errors.get(0))
                    .isExactlyInstanceOf(HttpException.class)
                    .hasMessageContaining("Incorrect API key provided");

            assertThat(threads).hasSize(1);
            assertThat(threads.iterator().next()).isNotEqualTo(Thread.currentThread());

            verify(spyListener).onError(any());
            verifyNoMoreInteractions(spyListener);
        }
    }

    @ParameterizedTest
    @EnumSource(StreamingMode.class)
    void should_deliver_error_when_streaming_connect_fails(StreamingMode mode) {

        for (HttpClient client : clients()) {

            // given
            HttpRequest request = HttpRequest.builder()
                    .method(POST)
                    .url(incorrectUrl())
                    .addHeader("Authorization", "Bearer " + OPENAI_API_KEY)
                    .addHeader("Content-Type", "application/json")
                    .body(
                            """
                                    {
                                        "model": "gpt-4o-mini",
                                        "messages": [
                                            {
                                                "role" : "user",
                                                "content" : "What is the capital of Germany?"
                                            }
                                        ],
                                        "stream": true
                                    }
                                    """)
                    .build();

            // when-then: a connection failure is delivered through the stream (onError), surfaced here.
            assertThatThrownBy(() -> stream(client, request, mode)).isInstanceOf(Throwable.class);
        }
    }

    protected String incorrectUrl() {
        return "http://banana";
    }

    @ParameterizedTest
    @EnumSource(ExecutionMode.class)
    protected void should_return_successful_http_response_form_data(ExecutionMode mode) throws Exception {
        byte[] audioBytes;
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("sample.wav")) {
            audioBytes = is.readAllBytes();
        }

        for (HttpClient client : clients()) {

            // given
            HttpRequest request = HttpRequest.builder()
                    .method(POST)
                    .url("https://api.openai.com/v1/audio/transcriptions")
                    .addHeader("Authorization", "Bearer " + OPENAI_API_KEY)
                    .addHeader("Content-Type", "multipart/form-data; boundary=----LangChain4j")
                    .addFormDataField("model", "gpt-4o-transcribe")
                    .addFormDataField("response_format", "text")
                    .addFormDataFile("file", "audio.wav", "", audioBytes)
                    .build();

            // when
            SuccessfulHttpResponse response = execute(request, client, mode);

            // then
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.headers()).isNotEmpty();
            assertThat(response.body().toLowerCase()).containsAnyOf("hello", "hallo");
        }
    }

    @Test
    void should_return_binary_response_sync() {

        for (HttpClient client : clients()) {

            // given
            HttpRequest request = HttpRequest.builder()
                    .method(POST)
                    .url("https://api.openai.com/v1/audio/speech")
                    .addHeader("Authorization", "Bearer " + OPENAI_API_KEY)
                    .addHeader("Content-Type", "application/json")
                    .body(
                            """
                                    {
                                        "model": "tts-1",
                                        "input": "Hello world!",
                                        "voice": "alloy"
                                    }
                                    """)
                    .build();

            // when
            SuccessfulHttpResponse response = client.execute(request);

            // then
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.headers()).isNotEmpty();

            byte[] audio = response.bodyBytes();
            assertThat(audio).isNotNull();
            assertThat(audio.length).isGreaterThan(1000);

            // Verify the raw bytes are a real MP3 (frame-sync 0xFFEx or an "ID3" tag),
            // proving binary data is returned intact and not corrupted by text decoding.
            boolean isMp3 = (audio[0] == (byte) 0xFF && (audio[1] & 0xE0) == 0xE0)
                    || (audio[0] == 'I' && audio[1] == 'D' && audio[2] == '3');
            assertThat(isMp3)
                    .as("response body should be a valid MP3 (first bytes: %02X %02X %02X)", audio[0], audio[1], audio[2])
                    .isTrue();
        }
    }
}
