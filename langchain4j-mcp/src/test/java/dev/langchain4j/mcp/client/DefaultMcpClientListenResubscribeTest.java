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
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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

    private static String discoverResult() {
        return """
                {"result":{"supportedVersions":["2026-07-28"],"capabilities":{},"resultType":"complete"}}
                """;
    }
}
