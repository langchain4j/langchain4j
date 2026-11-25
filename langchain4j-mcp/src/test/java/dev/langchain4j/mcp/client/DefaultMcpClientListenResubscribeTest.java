package dev.langchain4j.mcp.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.langchain4j.mcp.client.transport.McpOperationHandler;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.protocol.McpClientMessage;
import dev.langchain4j.mcp.protocol.McpServerDiscoverRequest;
import dev.langchain4j.mcp.protocol.McpSubscriptionsListenRequest;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

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
    public void should_resubscribe_when_listen_is_rejected() throws Exception {
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
                            "{\"error\":{\"code\":-32601,\"message\":\"Method not found\"}}");
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
    public void should_use_base_delay_for_a_blip_after_a_successful_reconnect() throws Exception {
        McpTransport transport = mock(McpTransport.class);
        when(transport.requiresCancellationNotification()).thenReturn(false);

        CompletableFuture<String> firstListen = new CompletableFuture<>();
        CompletableFuture<String> secondListen = new CompletableFuture<>();
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
                if (n == 2) {
                    listenStartedAgain.countDown();
                    return secondListen;
                }
                return new CompletableFuture<>();
            }
            return CompletableFuture.completedFuture("{}");
        });

        Duration reconnectInterval = Duration.ofMillis(400);
        try (DefaultMcpClient client =
                client(transport).reconnectInterval(reconnectInterval).build()) {
            assertThat(listenCalls.get()).isEqualTo(1);

            firstListen.completeExceptionally(new RuntimeException("Connection prematurely closed"));
            assertThat(listenStartedAgain.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(listenCalls.get()).isEqualTo(2);
            // Handshake of the reconnect succeeded; the counter must be back at zero.
            assertThat(retryAttempt(client).get()).isEqualTo(0);

            secondListen.completeExceptionally(new RuntimeException("Unrelated later blip"));
            // Attempt is incremented when the next retry is scheduled, and stays at 1 until
            // that listen handshake succeeds (delay is [200, 400] ms with this interval).
            assertThat(awaitRetryAttempt(client, 1, 200)).isEqualTo(1);
            assertThat(listenCalls.get()).isEqualTo(2);
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
    public void should_drop_a_duplicate_listen_with_the_same_notification_set() throws Exception {
        McpTransport transport = mock(McpTransport.class);
        when(transport.requiresCancellationNotification()).thenReturn(false);
        AtomicReference<Runnable> onFailure = captureOnFailure(transport);

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
            assertThat(onFailure.get()).isNotNull();

            // Reconnection runs initialize() and would otherwise issue a second listen
            // with the same notification set while the first is still open.
            onFailure.get().run();
            Thread.sleep(200);
            assertThat(listenCalls.get()).isEqualTo(1);
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
}
