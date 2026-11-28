package dev.langchain4j.mcp.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.mcp.client.transport.McpOperationHandler;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.protocol.McpClientMessage;
import dev.langchain4j.mcp.protocol.McpListToolsRequest;
import dev.langchain4j.mcp.protocol.McpServerDiscoverRequest;
import dev.langchain4j.mcp.protocol.McpSubscriptionsListenRequest;
import dev.langchain4j.spi.CapturedContext;
import dev.langchain4j.spi.ExecutorProvider;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class DefaultMcpClientListenResubscribeTest {

    @Test
    public void should_resubscribe_when_listen_fails() throws Exception {
        McpTransport transport = mock(McpTransport.class);
        when(transport.requiresCancellationNotification()).thenReturn(false);

        CompletableFuture<String> firstListen = new CompletableFuture<>();
        CountDownLatch listenStartedAgain = new CountDownLatch(1);
        AtomicInteger listenCalls = new AtomicInteger();

        when(transport.sendRequest(any(McpCallContext.class))).thenAnswer(invocation -> {
            McpClientMessage message =
                    invocation.getArgument(0, McpCallContext.class).message();
            if (message instanceof McpServerDiscoverRequest) {
                return CompletableFuture.completedFuture(discoverResult());
            }
            if (message instanceof McpSubscriptionsListenRequest) {
                int n = listenCalls.incrementAndGet();
                if (n == 1) {
                    return firstListen;
                }
                listenStartedAgain.countDown();
                return new CompletableFuture<>();
            }
            return CompletableFuture.completedFuture("{}");
        });

        try (DefaultMcpClient client = client(transport).build()) {
            assertThat(listenCalls.get()).isEqualTo(1);

            firstListen.completeExceptionally(new RuntimeException("Connection prematurely closed"));

            assertThat(listenStartedAgain.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(listenCalls.get()).isGreaterThanOrEqualTo(2);
        }
    }

    @Test
    public void should_resubscribe_when_listen_is_rejected_with_a_retryable_error() throws Exception {
        McpTransport transport = mock(McpTransport.class);
        when(transport.requiresCancellationNotification()).thenReturn(false);

        CountDownLatch listenStartedAgain = new CountDownLatch(1);
        AtomicInteger listenCalls = new AtomicInteger();
        when(transport.sendRequest(any(McpCallContext.class))).thenAnswer(invocation -> {
            McpClientMessage message =
                    invocation.getArgument(0, McpCallContext.class).message();
            if (message instanceof McpServerDiscoverRequest) {
                return CompletableFuture.completedFuture(discoverResult());
            }
            if (message instanceof McpSubscriptionsListenRequest) {
                int n = listenCalls.incrementAndGet();
                if (n == 1) {
                    return CompletableFuture.completedFuture(
                            "{\"error\":{\"code\":-32603,\"message\":\"Internal error\"}}");
                }
                listenStartedAgain.countDown();
                return new CompletableFuture<>();
            }
            return CompletableFuture.completedFuture("{}");
        });

        try (DefaultMcpClient client = client(transport).build()) {
            assertThat(listenCalls.get()).isEqualTo(1);
            assertThat(listenStartedAgain.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(listenCalls.get()).isGreaterThanOrEqualTo(2);
        }
    }

    @Test
    public void should_deliver_list_changed_after_listen_reconnects() throws Exception {
        McpTransport transport = mock(McpTransport.class);
        when(transport.requiresCancellationNotification()).thenReturn(false);
        AtomicReference<McpOperationHandler> handlerRef = captureMessageHandler(transport);

        CompletableFuture<String> firstListen = new CompletableFuture<>();
        CountDownLatch listenStartedAgain = new CountDownLatch(1);
        AtomicInteger listenCalls = new AtomicInteger();
        when(transport.sendRequest(any(McpCallContext.class))).thenAnswer(invocation -> {
            McpClientMessage message =
                    invocation.getArgument(0, McpCallContext.class).message();
            if (message instanceof McpServerDiscoverRequest) {
                return CompletableFuture.completedFuture(discoverResult());
            }
            if (message instanceof McpSubscriptionsListenRequest) {
                int n = listenCalls.incrementAndGet();
                if (n == 1) {
                    return firstListen;
                }
                listenStartedAgain.countDown();
                return new CompletableFuture<>();
            }
            return CompletableFuture.completedFuture("{}");
        });

        AtomicInteger notifications = new AtomicInteger();
        try (DefaultMcpClient client = client(transport)
                .addListener(new McpClientListener() {
                    @Override
                    public void onNotificationToolsListChanged() {
                        notifications.incrementAndGet();
                    }
                })
                .build()) {
            assertThat(listenCalls.get()).isEqualTo(1);
            // Drop the stream only after the first list_changed has already been delivered.
            handlerRef.get().onMessage("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/tools/list_changed\"}");
            assertThat(notifications.get()).isEqualTo(1);

            firstListen.completeExceptionally(new RuntimeException("Connection prematurely closed"));
            assertThat(listenStartedAgain.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(listenCalls.get()).isGreaterThanOrEqualTo(2);

            handlerRef.get().onMessage("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/tools/list_changed\"}");
            assertThat(notifications.get()).isEqualTo(2);
        }
    }

    @Test
    public void should_escalate_delay_across_consecutive_failures_and_reset_after_success() throws Exception {
        McpTransport transport = mock(McpTransport.class);
        when(transport.requiresCancellationNotification()).thenReturn(false);
        AtomicReference<McpOperationHandler> handlerRef = captureMessageHandler(transport);

        AtomicInteger listenCalls = new AtomicInteger();
        List<CompletableFuture<String>> listens = new CopyOnWriteArrayList<>();
        List<Long> listenAt = new CopyOnWriteArrayList<>();
        List<Long> failedAt = new CopyOnWriteArrayList<>();
        when(transport.sendRequest(any(McpCallContext.class))).thenAnswer(invocation -> {
            McpClientMessage message =
                    invocation.getArgument(0, McpCallContext.class).message();
            if (message instanceof McpServerDiscoverRequest) {
                return CompletableFuture.completedFuture(discoverResult());
            }
            if (message instanceof McpSubscriptionsListenRequest) {
                int n = listenCalls.incrementAndGet();
                CompletableFuture<String> future = new CompletableFuture<>();
                listens.add(future);
                listenAt.add(System.nanoTime());
                // Complete asynchronously so the future is still pending when
                // sendRequest returns — the previous reset-on-!isDone() bug would
                // zero the attempt counter here and never escalate.
                if (n <= 3) {
                    CompletableFuture.delayedExecutor(25, TimeUnit.MILLISECONDS).execute(() -> {
                        failedAt.add(System.nanoTime());
                        future.completeExceptionally(new RuntimeException("async listen failure " + n));
                    });
                }
                return future;
            }
            return CompletableFuture.completedFuture("{}");
        });

        Duration reconnectInterval = Duration.ofMillis(200);
        try (DefaultMcpClient client =
                client(transport).reconnectInterval(reconnectInterval).build()) {
            assertThat(awaitListenCalls(listenCalls, 4, 5_000)).isEqualTo(4);
            // Listen 4 is still pending and no list_changed has arrived, so backoff
            // must still be at attempt 3. Resetting on a pending future would be 0.
            assertThat(retryAttempt(client).get()).isEqualTo(3);
            assertThat(failedAt).hasSize(3);

            long delay1 = listenAt.get(1) - failedAt.get(0);
            long delay3 = listenAt.get(3) - failedAt.get(2);
            assertThat(delay3).isGreaterThan(delay1);

            handlerRef.get().onMessage("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/tools/list_changed\"}");
            assertThat(retryAttempt(client).get()).isEqualTo(0);

            long failed4 = System.nanoTime();
            listens.get(3).completeExceptionally(new RuntimeException("blip after recovery"));
            assertThat(awaitRetryAttempt(client, 1, 500)).isEqualTo(1);
            assertThat(awaitListenCalls(listenCalls, 5, 5_000)).isEqualTo(5);
            long delayAfterSuccess = listenAt.get(4) - failed4;
            assertThat(delayAfterSuccess).isLessThan(delay3);
        }
    }

    @Test
    public void should_ignore_a_queued_resubscribe_after_a_newer_generation() throws Exception {
        McpTransport transport = mock(McpTransport.class);
        when(transport.requiresCancellationNotification()).thenReturn(false);

        CompletableFuture<String> firstListen = new CompletableFuture<>();
        AtomicInteger listenCalls = new AtomicInteger();
        when(transport.sendRequest(any(McpCallContext.class))).thenAnswer(invocation -> {
            McpClientMessage message =
                    invocation.getArgument(0, McpCallContext.class).message();
            if (message instanceof McpServerDiscoverRequest) {
                return CompletableFuture.completedFuture(discoverResult());
            }
            if (message instanceof McpSubscriptionsListenRequest) {
                listenCalls.incrementAndGet();
                return firstListen;
            }
            return CompletableFuture.completedFuture("{}");
        });

        Duration reconnectInterval = Duration.ofMillis(400);
        try (DefaultMcpClient client =
                client(transport).reconnectInterval(reconnectInterval).build()) {
            assertThat(listenCalls.get()).isEqualTo(1);
            long generationAtFailure = generation(client).get();

            firstListen.completeExceptionally(new RuntimeException("Connection prematurely closed"));
            assertThat(awaitRetryAttempt(client, 1, 200)).isEqualTo(1);

            generation(client).incrementAndGet();
            assertThat(generation(client).get()).isEqualTo(generationAtFailure + 1);

            Thread.sleep(600);
            assertThat(listenCalls.get()).isEqualTo(1);
        }
    }

    @Test
    public void should_cancel_and_restart_listen_when_initialize_is_rerun() throws Exception {
        McpTransport transport = mock(McpTransport.class);
        when(transport.requiresCancellationNotification()).thenReturn(false);
        AtomicReference<Runnable> onFailure = captureOnFailure(transport);

        List<CompletableFuture<String>> listens = new CopyOnWriteArrayList<>();
        AtomicInteger listenCalls = new AtomicInteger();
        when(transport.sendRequest(any(McpCallContext.class))).thenAnswer(invocation -> {
            McpClientMessage message =
                    invocation.getArgument(0, McpCallContext.class).message();
            if (message instanceof McpServerDiscoverRequest) {
                return CompletableFuture.completedFuture(discoverResult());
            }
            if (message instanceof McpSubscriptionsListenRequest) {
                listenCalls.incrementAndGet();
                CompletableFuture<String> future = new CompletableFuture<>();
                listens.add(future);
                return future;
            }
            return CompletableFuture.completedFuture("{}");
        });

        try (DefaultMcpClient client = client(transport).build()) {
            assertThat(listenCalls.get()).isEqualTo(1);
            assertThat(onFailure.get()).isNotNull();

            // initialize() after a transport failure must cancel the previous listen
            // and start a new one, not drop the start as a duplicate.
            onFailure.get().run();
            assertThat(awaitListenCalls(listenCalls, 2, 2_000)).isEqualTo(2);
            assertThat(listens.get(0).isCancelled()).isTrue();
            assertThat(listens.get(1).isDone()).isFalse();
        }
    }

    @Test
    public void should_drop_a_duplicate_retry_while_the_same_listen_is_open() throws Exception {
        McpTransport transport = mock(McpTransport.class);
        when(transport.requiresCancellationNotification()).thenReturn(false);

        CompletableFuture<String> firstListen = new CompletableFuture<>();
        AtomicInteger listenCalls = new AtomicInteger();
        when(transport.sendRequest(any(McpCallContext.class))).thenAnswer(invocation -> {
            McpClientMessage message =
                    invocation.getArgument(0, McpCallContext.class).message();
            if (message instanceof McpServerDiscoverRequest) {
                return CompletableFuture.completedFuture(discoverResult());
            }
            if (message instanceof McpSubscriptionsListenRequest) {
                listenCalls.incrementAndGet();
                return firstListen;
            }
            return CompletableFuture.completedFuture("{}");
        });

        try (DefaultMcpClient client = client(transport).build()) {
            assertThat(listenCalls.get()).isEqualTo(1);

            startListChangeSubscriptions(client, true);

            assertThat(listenCalls.get()).isEqualTo(1);
            assertThat(firstListen.isDone()).isFalse();
        }
    }

    @Test
    public void should_not_resubscribe_when_listen_is_rejected_as_method_not_found() throws Exception {
        McpTransport transport = mock(McpTransport.class);
        when(transport.requiresCancellationNotification()).thenReturn(false);

        AtomicInteger listenCalls = new AtomicInteger();
        when(transport.sendRequest(any(McpCallContext.class))).thenAnswer(invocation -> {
            McpClientMessage message =
                    invocation.getArgument(0, McpCallContext.class).message();
            if (message instanceof McpServerDiscoverRequest) {
                return CompletableFuture.completedFuture(discoverResult());
            }
            if (message instanceof McpSubscriptionsListenRequest) {
                listenCalls.incrementAndGet();
                return CompletableFuture.completedFuture(
                        "{\"error\":{\"code\":-32601,\"message\":\"Method not found\"}}");
            }
            return CompletableFuture.completedFuture("{}");
        });

        try (DefaultMcpClient client = client(transport).build()) {
            assertThat(listenCalls.get()).isEqualTo(1);
            Thread.sleep(300);
            assertThat(listenCalls.get()).isEqualTo(1);
            assertThat(retryAttempt(client).get()).isEqualTo(0);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403})
    public void should_not_resubscribe_when_listen_fails_with_non_retryable_http_status(int status) throws Exception {
        McpTransport transport = mock(McpTransport.class);
        when(transport.requiresCancellationNotification()).thenReturn(false);

        AtomicInteger listenCalls = new AtomicInteger();
        when(transport.sendRequest(any(McpCallContext.class))).thenAnswer(invocation -> {
            McpClientMessage message =
                    invocation.getArgument(0, McpCallContext.class).message();
            if (message instanceof McpServerDiscoverRequest) {
                return CompletableFuture.completedFuture(discoverResult());
            }
            if (message instanceof McpSubscriptionsListenRequest) {
                listenCalls.incrementAndGet();
                return CompletableFuture.failedFuture(new HttpException(status, "Unexpected status code: " + status));
            }
            return CompletableFuture.completedFuture("{}");
        });

        try (DefaultMcpClient client = client(transport).build()) {
            assertThat(listenCalls.get()).isEqualTo(1);
            Thread.sleep(300);
            assertThat(listenCalls.get()).isEqualTo(1);
            assertThat(retryAttempt(client).get()).isEqualTo(0);
        }
    }

    @Test
    public void should_resubscribe_on_the_configured_executor_with_captured_context() throws Exception {
        McpTransport transport = mock(McpTransport.class);
        when(transport.requiresCancellationNotification()).thenReturn(false);

        ThreadLocal<String> token = new ThreadLocal<>();
        token.set("request-token");
        AtomicReference<String> tokenOnRetry = new AtomicReference<>();
        AtomicReference<String> retryThreadName = new AtomicReference<>();
        CountDownLatch listenStartedAgain = new CountDownLatch(1);
        AtomicInteger listenCalls = new AtomicInteger();
        CompletableFuture<String> firstListen = new CompletableFuture<>();

        when(transport.sendRequest(any(McpCallContext.class))).thenAnswer(invocation -> {
            McpClientMessage message =
                    invocation.getArgument(0, McpCallContext.class).message();
            if (message instanceof McpServerDiscoverRequest) {
                return CompletableFuture.completedFuture(discoverResult());
            }
            if (message instanceof McpSubscriptionsListenRequest) {
                int n = listenCalls.incrementAndGet();
                if (n == 1) {
                    return firstListen;
                }
                tokenOnRetry.set(token.get());
                retryThreadName.set(Thread.currentThread().getName());
                listenStartedAgain.countDown();
                return new CompletableFuture<>();
            }
            return CompletableFuture.completedFuture("{}");
        });

        ExecutorService pool = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "list-change-resubscribe-pool");
            t.setDaemon(true);
            return t;
        });
        ExecutorProvider previous = ExecutorProvider.get();
        ExecutorProvider.set(new ExecutorProvider() {
            @Override
            public Executor executor() {
                return null;
            }

            @Override
            public CapturedContext captureContext() {
                String value = token.get();
                return task -> () -> {
                    String previousValue = token.get();
                    token.set(value);
                    try {
                        task.run();
                    } finally {
                        token.set(previousValue);
                    }
                };
            }
        });
        try (DefaultMcpClient client =
                client(transport).listChangeResubscribeExecutor(pool).build()) {
            // Drop the token from this thread so a retry that forgot the
            // captured context would send the listen without it.
            token.remove();
            firstListen.completeExceptionally(new RuntimeException("Connection prematurely closed"));
            assertThat(listenStartedAgain.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(listenCalls.get()).isGreaterThanOrEqualTo(2);
            assertThat(tokenOnRetry.get()).isEqualTo("request-token");
            assertThat(retryThreadName.get()).isEqualTo("list-change-resubscribe-pool");
        } finally {
            ExecutorProvider.set(previous);
            pool.shutdownNow();
            token.remove();
        }
    }

    @Test
    public void should_not_resubscribe_after_close() throws Exception {
        McpTransport transport = mock(McpTransport.class);
        when(transport.requiresCancellationNotification()).thenReturn(false);

        CompletableFuture<String> firstListen = new CompletableFuture<>();
        AtomicInteger listenCalls = new AtomicInteger();
        when(transport.sendRequest(any(McpCallContext.class))).thenAnswer(invocation -> {
            McpClientMessage message =
                    invocation.getArgument(0, McpCallContext.class).message();
            if (message instanceof McpServerDiscoverRequest) {
                return CompletableFuture.completedFuture(discoverResult());
            }
            if (message instanceof McpSubscriptionsListenRequest) {
                listenCalls.incrementAndGet();
                return firstListen;
            }
            return CompletableFuture.completedFuture("{}");
        });

        DefaultMcpClient client =
                client(transport).reconnectInterval(Duration.ofSeconds(2)).build();
        assertThat(listenCalls.get()).isEqualTo(1);
        firstListen.completeExceptionally(new RuntimeException("Connection prematurely closed"));
        client.close();
        Thread.sleep(250);
        assertThat(listenCalls.get()).isEqualTo(1);
    }

    @Test
    public void should_keep_cached_tools_while_listen_is_down() throws Exception {
        McpTransport transport = mock(McpTransport.class);
        when(transport.requiresCancellationNotification()).thenReturn(false);

        CompletableFuture<String> firstListen = new CompletableFuture<>();
        AtomicInteger listenCalls = new AtomicInteger();
        AtomicInteger toolsListCalls = new AtomicInteger();
        when(transport.sendRequest(any(McpCallContext.class))).thenAnswer(invocation -> {
            McpClientMessage message =
                    invocation.getArgument(0, McpCallContext.class).message();
            if (message instanceof McpServerDiscoverRequest) {
                return CompletableFuture.completedFuture(discoverResult());
            }
            if (message instanceof McpSubscriptionsListenRequest) {
                listenCalls.incrementAndGet();
                return firstListen;
            }
            if (message instanceof McpListToolsRequest) {
                toolsListCalls.incrementAndGet();
                return CompletableFuture.completedFuture(toolsListResult("echo"));
            }
            return CompletableFuture.completedFuture("{}");
        });

        // Long interval so the retry listen is not opened before we assert fail-open.
        try (DefaultMcpClient client = client(transport)
                .reconnectInterval(Duration.ofSeconds(2))
                .cacheToolList(true)
                .build()) {
            assertThat(client.listTools()).extracting(ToolSpecification::name).containsExactly("echo");
            assertThat(toolsListCalls.get()).isEqualTo(1);

            firstListen.completeExceptionally(new RuntimeException("Connection prematurely closed"));

            assertThat(client.listTools()).extracting(ToolSpecification::name).containsExactly("echo");
            assertThat(toolsListCalls.get()).isEqualTo(1);
        }
    }

    @Test
    public void should_evict_caches_only_after_a_new_listen_is_established() throws Exception {
        McpTransport transport = mock(McpTransport.class);
        when(transport.requiresCancellationNotification()).thenReturn(false);

        CompletableFuture<String> firstListen = new CompletableFuture<>();
        CountDownLatch listenStartedAgain = new CountDownLatch(1);
        AtomicInteger listenCalls = new AtomicInteger();
        AtomicInteger toolsListCalls = new AtomicInteger();
        when(transport.sendRequest(any(McpCallContext.class))).thenAnswer(invocation -> {
            McpClientMessage message =
                    invocation.getArgument(0, McpCallContext.class).message();
            if (message instanceof McpServerDiscoverRequest) {
                return CompletableFuture.completedFuture(discoverResult());
            }
            if (message instanceof McpSubscriptionsListenRequest) {
                int n = listenCalls.incrementAndGet();
                if (n == 1) {
                    return firstListen;
                }
                listenStartedAgain.countDown();
                return new CompletableFuture<>();
            }
            if (message instanceof McpListToolsRequest) {
                int n = toolsListCalls.incrementAndGet();
                String name = n == 1 ? "echo" : "echo2";
                return CompletableFuture.completedFuture(toolsListResult(name));
            }
            return CompletableFuture.completedFuture("{}");
        });

        try (DefaultMcpClient client = client(transport).cacheToolList(true).build()) {
            assertThat(client.listTools()).extracting(ToolSpecification::name).containsExactly("echo");
            assertThat(toolsListCalls.get()).isEqualTo(1);

            firstListen.completeExceptionally(new RuntimeException("Connection prematurely closed"));
            assertThat(listenStartedAgain.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(listenCalls.get()).isGreaterThanOrEqualTo(2);

            assertThat(client.listTools()).extracting(ToolSpecification::name).containsExactly("echo2");
            assertThat(toolsListCalls.get()).isEqualTo(2);
        }
    }

    private static DefaultMcpClient.Builder client(McpTransport transport) {
        return new DefaultMcpClient.Builder()
                .transport(transport)
                .protocolVersion("2026-07-28")
                .autoHealthCheck(false)
                .reconnectInterval(Duration.ofMillis(50))
                .subscribeToToolListChanges(true)
                .subscribeToPromptListChanges(false)
                .subscribeToResourceListChanges(false);
    }

    private static AtomicReference<McpOperationHandler> captureMessageHandler(McpTransport transport) {
        AtomicReference<McpOperationHandler> handlerRef = new AtomicReference<>();
        doAnswer(invocation -> {
                    handlerRef.set(invocation.getArgument(0));
                    return null;
                })
                .when(transport)
                .start(any(McpOperationHandler.class));
        return handlerRef;
    }

    private static AtomicReference<Runnable> captureOnFailure(McpTransport transport) {
        AtomicReference<Runnable> onFailure = new AtomicReference<>();
        doAnswer(invocation -> {
                    onFailure.set(invocation.getArgument(0));
                    return null;
                })
                .when(transport)
                .onFailure(any());
        return onFailure;
    }

    private static void startListChangeSubscriptions(DefaultMcpClient client, boolean retry) throws Exception {
        Method method = DefaultMcpClient.class.getDeclaredMethod("startListChangeSubscriptions", boolean.class);
        method.setAccessible(true);
        method.invoke(client, retry);
    }

    private static int awaitListenCalls(AtomicInteger listenCalls, int expected, long timeoutMs) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        int current;
        do {
            current = listenCalls.get();
            if (current == expected) {
                return current;
            }
            Thread.sleep(10);
        } while (System.nanoTime() < deadline);
        return current;
    }

    private static int awaitRetryAttempt(DefaultMcpClient client, int expected, long timeoutMs) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        int current;
        do {
            current = retryAttempt(client).get();
            if (current == expected) {
                return current;
            }
            Thread.sleep(10);
        } while (System.nanoTime() < deadline);
        return current;
    }

    private static AtomicInteger retryAttempt(DefaultMcpClient client) throws Exception {
        Field field = DefaultMcpClient.class.getDeclaredField("listChangeRetryAttempt");
        field.setAccessible(true);
        return (AtomicInteger) field.get(client);
    }

    private static AtomicLong generation(DefaultMcpClient client) throws Exception {
        Field field = DefaultMcpClient.class.getDeclaredField("listChangeSubscribeGeneration");
        field.setAccessible(true);
        return (AtomicLong) field.get(client);
    }

    private static String discoverResult() {
        return """
                {"result":{"supportedVersions":["2026-07-28"],"capabilities":{},"resultType":"complete"}}
                """;
    }

    private static String toolsListResult(String name) {
        return "{\"result\":{\"tools\":[{\"name\":\""
                + name
                + "\",\"description\":\""
                + name
                + "\",\"inputSchema\":{\"type\":\"object\",\"properties\":{}}}]}}";
    }
}
