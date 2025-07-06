package dev.langchain4j.http.client;

import static dev.langchain4j.http.client.HttpMethod.POST;
import static java.util.Collections.synchronizedList;
import static java.util.Collections.synchronizedSet;
import static java.util.stream.Collectors.joining;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;

import dev.langchain4j.exception.HttpException;
import dev.langchain4j.http.client.sse.DefaultServerSentEventParser;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.InOrder;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public abstract class HttpClientIT {

    private static final String OPENAI_API_KEY = System.getenv("OPENAI_API_KEY");

    protected abstract List<HttpClient> clients();

    @Test
    void should_return_successful_http_response_sync() {

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
            SuccessfulHttpResponse response = client.execute(request);

            // then
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.headers()).isNotEmpty();
            assertThat(response.body()).contains("Berlin");
        }
    }

    @Test
    void should_throw_400_sync() {

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
                client.execute(request);
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

    @Test
    void should_throw_401_sync() {

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
                client.execute(request);
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
            StreamingResult streamingResult = completableFuture.get(30, TimeUnit.SECONDS);

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
            inOrder.verify(spyListener, atLeastOnce()).onEvent(any());
            inOrder.verify(spyListener, times(1)).onClose();
            inOrder.verifyNoMoreInteractions();
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
            StreamingResult streamingResult = completableFuture.get(30, TimeUnit.SECONDS);

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
            inOrder.verify(spyListener, atLeastOnce()).onEvent(any());
            inOrder.verify(spyListener, times(1)).onClose();
            inOrder.verifyNoMoreInteractions();
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
            StreamingResult streamingResult = completableFuture.get(30, TimeUnit.SECONDS);

            assertThat(streamingResult.throwable())
                    .isExactlyInstanceOf(HttpException.class)
                    .extracting("statusCode")
                    .isEqualTo(400);
            assertThat(streamingResult.throwable()).hasMessageContaining("Missing required parameter: 'messages'");

            assertThat(streamingResult.threads()).hasSize(1);
            assertThat(streamingResult.threads().iterator().next()).isNotEqualTo(Thread.currentThread());

            InOrder inOrder = inOrder(spyListener);
            inOrder.verify(spyListener, times(1)).onError(any());
            inOrder.verifyNoMoreInteractions();
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
                public void onError(Throwable throwable) {
                    errors.add(throwable);
                    threads.add(Thread.currentThread());
                }

                @Override
                public void onClose() {
                    threads.add(Thread.currentThread());
                }
            };
            ServerSentEventListener spyListener = spy(listener);
            client.execute(request, new DefaultServerSentEventParser(), spyListener);
            Thread.sleep(5_000);

            // then
            assertThat(response.get()).isNotNull();
            assertThat(events).isNotEmpty();
            assertThat(errors).isEmpty();

            assertThat(threads).hasSize(1);
            assertThat(threads.iterator().next()).isNotEqualTo(Thread.currentThread());

            InOrder inOrder = inOrder(spyListener);
            inOrder.verify(spyListener, times(1)).onOpen(any());
            inOrder.verify(spyListener, atLeastOnce()).onEvent(any());
            inOrder.verify(spyListener, times(1)).onClose();
            inOrder.verifyNoMoreInteractions();
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
                public void onError(Throwable throwable) {
                    errors.add(throwable);
                    threads.add(Thread.currentThread());
                }

                @Override
                public void onClose() {
                    threads.add(Thread.currentThread());
                }
            };
            ServerSentEventListener spyListener = spy(listener);
            client.execute(request, new DefaultServerSentEventParser(), spyListener);
            Thread.sleep(5_000);

            // then
            assertThat(response.get()).isNotNull();
            assertThat(events).hasSizeGreaterThan(1);
            assertThat(errors).isEmpty();

            assertThat(threads).hasSize(1);
            assertThat(threads.iterator().next()).isNotEqualTo(Thread.currentThread());

            InOrder inOrder = inOrder(spyListener);
            inOrder.verify(spyListener, times(1)).onOpen(any());
            inOrder.verify(spyListener, times(events.size())).onEvent(any());
            inOrder.verify(spyListener, times(1)).onClose();
            inOrder.verifyNoMoreInteractions();
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

                    throw new RuntimeException("Unexpected exception in onError()");
                }

                @Override
                public void onClose() {
                    threads.add(Thread.currentThread());
                }
            };
            ServerSentEventListener spyListener = spy(listener);
            client.execute(request, new DefaultServerSentEventParser(), spyListener);
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

            InOrder inOrder = inOrder(spyListener);
            inOrder.verify(spyListener, times(1)).onError(any());
            inOrder.verifyNoMoreInteractions();
        }
    }

    @Test
    void should_call_listener_onError_when_fails_to_connect() throws Exception {

        for (HttpClient client : clients()) {

            // given
            String incorrectUrl = "http://banana";

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
                }

                @Override
                public void onClose() {
                    threads.add(Thread.currentThread());
                }
            };
            ServerSentEventListener spyListener = spy(listener);
            client.execute(request, new DefaultServerSentEventParser(), spyListener);
            Thread.sleep(5_000);

            // then
            assertThat(response.get()).isNull();
            assertThat(events).isEmpty();
            assertThat(errors).hasSize(1);

            assertThat(threads).hasSize(1);
            assertThat(threads.iterator().next()).isNotEqualTo(Thread.currentThread());

            InOrder inOrder = inOrder(spyListener);
            inOrder.verify(spyListener, times(1)).onError(any());
            inOrder.verifyNoMoreInteractions();
        }
    }
}
