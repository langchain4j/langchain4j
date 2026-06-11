package dev.langchain4j.http.client;

import static dev.langchain4j.http.client.HttpMethod.POST;
import static java.util.Collections.synchronizedList;
import static java.util.Collections.synchronizedSet;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.joining;
import static org.assertj.core.api.Assertions.assertThat;
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

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
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

    @Test
    void should_return_successful_http_response_async() throws Exception {

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
            record StreamingResult(
                    SuccessfulHttpResponse response, List<ServerSentEvent> events, Set<Thread> threads) {}

            CompletableFuture<StreamingResult> completableFuture = new CompletableFuture<>();

            ServerSentEventListener listener = new ServerSentEventListener() {

                private final AtomicReference<SuccessfulHttpResponse> response = new AtomicReference<>();
                private final List<ServerSentEvent> events = new ArrayList<>();
                private final Set<Thread> threads = new HashSet<>();

                @Override
                public void onOpen(SuccessfulHttpResponse successfulHttpResponse) {
                    threads.add(Thread.currentThread());
                    response.set(successfulHttpResponse);
                }

                @Override
                public void onEvent(ServerSentEvent event) {
                    threads.add(Thread.currentThread());
                    events.add(event);
                }

                @Override
                public void onEvent(ServerSentEvent event, ServerSentEventContext context) {
                    threads.add(Thread.currentThread());
                    events.add(event);
                }

                @Override
                public void onError(Throwable throwable) {
                    threads.add(Thread.currentThread());
                    completableFuture.completeExceptionally(throwable);
                }

                @Override
                public void onClose() {
                    threads.add(Thread.currentThread());
                    completableFuture.complete(new StreamingResult(response.get(), events, threads));
                }
            };
            ServerSentEventListener spyListener = spy(listener);
            client.execute(request, new DefaultServerSentEventParser(), spyListener);

            // then
            StreamingResult streamingResult = completableFuture.get(30, SECONDS);

            assertThat(streamingResult.response()).isNotNull();
            assertThat(streamingResult.response().statusCode()).isEqualTo(200);
            assertThat(streamingResult.response().headers()).isNotEmpty();
            assertThat(streamingResult.response().body()).isNull();

            assertThat(streamingResult.events()).isNotEmpty();
            assertThat(streamingResult.events().stream()
                            .map(ServerSentEvent::data)
                            .collect(joining("")))
                    .contains("Berlin");

            assertThat(streamingResult.threads()).hasSize(1);
            assertThat(streamingResult.threads().iterator().next()).isNotEqualTo(Thread.currentThread());

            InOrder inOrder = inOrder(spyListener);
            inOrder.verify(spyListener, times(1)).onOpen(any());
            inOrder.verify(spyListener, atLeastOnce()).onEvent(any(), any());
            inOrder.verify(spyListener, times(1)).onClose();
            inOrder.verifyNoMoreInteractions();
            verifyNoMoreInteractions(spyListener);
        }
    }

    @Test
    void should_cancel_streaming_async() throws Exception {

        for (HttpClient client : clients()) {

            // given
            int eventsBeforeCancellation = 5;

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

            // when
            CompletableFuture<Void> completableFuture = new CompletableFuture<>();

            ServerSentEventListener listener = new ServerSentEventListener() {

                private AtomicInteger counter = new AtomicInteger();

                @Override
                public void onOpen(SuccessfulHttpResponse successfulHttpResponse) {}

                @Override
                public void onEvent(ServerSentEvent event) {
                    counter.incrementAndGet();
                }

                @Override
                public void onEvent(ServerSentEvent event, ServerSentEventContext context) {
                    if (counter.incrementAndGet() >= eventsBeforeCancellation) {
                        context.parsingHandle().cancel();
                    }
                }

                @Override
                public void onError(Throwable throwable) {
                    completableFuture.completeExceptionally(throwable);
                }

                @Override
                public void onClose() {
                    completableFuture.complete(null);
                }
            };
            ServerSentEventListener spyListener = spy(listener);
            client.execute(request, new DefaultServerSentEventParser(), spyListener);

            // then
            completableFuture.get(30, SECONDS);

            InOrder inOrder = inOrder(spyListener);
            inOrder.verify(spyListener, times(1)).onOpen(any());
            inOrder.verify(spyListener, times(eventsBeforeCancellation)).onEvent(any(), any());
            inOrder.verify(spyListener, times(1)).onClose();
            inOrder.verifyNoMoreInteractions();
            verifyNoMoreInteractions(spyListener);
        }
    }

    @Test
    void should_return_successful_http_response_with_double_newline_async() throws Exception {

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
            record StreamingResult(
                    SuccessfulHttpResponse response, List<ServerSentEvent> events, Set<Thread> threads) {}

            CompletableFuture<StreamingResult> completableFuture = new CompletableFuture<>();

            ServerSentEventListener listener = new ServerSentEventListener() {

                private final AtomicReference<SuccessfulHttpResponse> response = new AtomicReference<>();
                private final List<ServerSentEvent> events = new ArrayList<>();
                private final Set<Thread> threads = new HashSet<>();

                @Override
                public void onOpen(SuccessfulHttpResponse successfulHttpResponse) {
                    threads.add(Thread.currentThread());
                    response.set(successfulHttpResponse);
                }

                @Override
                public void onEvent(ServerSentEvent event) {
                    threads.add(Thread.currentThread());
                    events.add(event);
                }

                @Override
                public void onEvent(ServerSentEvent event, ServerSentEventContext context) {
                    threads.add(Thread.currentThread());
                    events.add(event);
                }

                @Override
                public void onError(Throwable throwable) {
                    threads.add(Thread.currentThread());
                    completableFuture.completeExceptionally(throwable);
                }

                @Override
                public void onClose() {
                    threads.add(Thread.currentThread());
                    completableFuture.complete(new StreamingResult(response.get(), events, threads));
                }
            };
            ServerSentEventListener spyListener = spy(listener);
            client.execute(request, new DefaultServerSentEventParser(), spyListener);

            // then
            StreamingResult streamingResult = completableFuture.get(30, SECONDS);

            assertThat(streamingResult.response()).isNotNull();
            assertThat(streamingResult.response().statusCode()).isEqualTo(200);
            assertThat(streamingResult.response().headers()).isNotEmpty();
            assertThat(streamingResult.response().body()).isNull();

            assertThat(streamingResult.events()).isNotEmpty();
            assertThat(streamingResult.events().stream()
                            .map(ServerSentEvent::data)
                            .collect(joining("")))
                    .contains("Berlin", "Paris", "\\n\\n");

            assertThat(streamingResult.threads()).hasSize(1);
            assertThat(streamingResult.threads().iterator().next()).isNotEqualTo(Thread.currentThread());

            InOrder inOrder = inOrder(spyListener);
            inOrder.verify(spyListener, times(1)).onOpen(any());
            inOrder.verify(spyListener, atLeastOnce()).onEvent(any(), any());
            inOrder.verify(spyListener, times(1)).onClose();
            inOrder.verifyNoMoreInteractions();
            verifyNoMoreInteractions(spyListener);
        }
    }

    @Test
    void should_throw_400_async() throws Exception {

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

            // when
            record StreamingResult(Throwable throwable, Set<Thread> threads) {}

            CompletableFuture<StreamingResult> completableFuture = new CompletableFuture<>();

            ServerSentEventListener listener = new ServerSentEventListener() {

                private final Set<Thread> threads = new HashSet<>();

                @Override
                public void onOpen(SuccessfulHttpResponse successfulHttpResponse) {
                    completableFuture.completeExceptionally(new IllegalStateException("onOpen() should not be called"));
                }

                @Override
                public void onEvent(ServerSentEvent event) {
                    completableFuture.completeExceptionally(
                            new IllegalStateException("onEvent() should not be called"));
                }

                @Override
                public void onError(Throwable throwable) {
                    threads.add(Thread.currentThread());
                    completableFuture.complete(new StreamingResult(throwable, threads));
                }

                @Override
                public void onClose() {
                    completableFuture.completeExceptionally(
                            new IllegalStateException("onClose() should not be called"));
                }
            };
            ServerSentEventListener spyListener = spy(listener);
            client.execute(request, new DefaultServerSentEventParser(), spyListener);

            // then
            StreamingResult streamingResult = completableFuture.get(30, SECONDS);

            assertThat(streamingResult.throwable())
                    .isExactlyInstanceOf(HttpException.class)
                    .extracting("statusCode")
                    .isEqualTo(400);
            assertThat(streamingResult.throwable()).hasMessageContaining("Missing required parameter: 'messages'");

            assertThat(streamingResult.threads()).hasSize(1);
            assertThat(streamingResult.threads().iterator().next()).isNotEqualTo(Thread.currentThread());

            verify(spyListener).onError(any());
            verifyNoMoreInteractions(spyListener);
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

    @Test
    void should_call_listener_onError_when_fails_to_connect() throws Exception {

        for (HttpClient client : clients()) {

            // given
            String incorrectUrl = incorrectUrl();

            HttpRequest request = HttpRequest.builder()
                    .method(POST)
                    .url(incorrectUrl)
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
                }

                @Override
                public void onError(Throwable throwable) {
                    errors.add(throwable);
                    threads.add(Thread.currentThread());
                    future.complete(null);
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

            assertThat(threads).hasSize(1);
            assertThat(threads.iterator().next()).isNotEqualTo(Thread.currentThread());

            verify(spyListener).onError(any());
            verifyNoMoreInteractions(spyListener);
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
}
