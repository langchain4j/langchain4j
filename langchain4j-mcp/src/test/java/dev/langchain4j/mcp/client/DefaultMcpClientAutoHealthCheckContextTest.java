package dev.langchain4j.mcp.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.protocol.McpListToolsRequest;
import dev.langchain4j.mcp.protocol.McpPingRequest;
import dev.langchain4j.mcp.protocol.McpServerDiscoverRequest;
import dev.langchain4j.spi.CapturedContext;
import dev.langchain4j.spi.ExecutorProvider;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** ExecutorProvider is process-wide, so context tests must run alone and sequentially. */
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
class DefaultMcpClientAutoHealthCheckContextTest {

    private static final String DISCOVER_RESULT =
            "{\"result\":{\"supportedVersions\":[\"2026-07-28\"],\"capabilities\":{},\"resultType\":\"complete\"}}";
    private static final String LIST_TOOLS_RESULT = "{\"result\":{\"tools\":[],\"resultType\":\"complete\"}}";

    private final ExecutorProvider previousProvider = ExecutorProvider.get();
    private final ThreadLocal<String> identity = new ThreadLocal<>();

    @AfterEach
    void tearDown() {
        ExecutorProvider.set(previousProvider);
        identity.remove();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void scheduled_check_and_reconnect_use_initial_context_without_affecting_foreground_calls(boolean modern)
            throws Exception {
        ExecutorProvider.set(new CapturingProvider());
        McpTransport transport = mock(McpTransport.class);
        AtomicInteger discoveries = new AtomicInteger();
        AtomicInteger initializations = new AtomicInteger();
        AtomicInteger healthChecks = new AtomicInteger();
        AtomicReference<String> firstCheckIdentity = new AtomicReference<>();
        AtomicReference<String> reconnectIdentity = new AtomicReference<>();
        AtomicReference<String> healthyCheckIdentity = new AtomicReference<>();
        AtomicReference<String> foregroundIdentity = new AtomicReference<>();
        AtomicReference<String> laterCheckIdentity = new AtomicReference<>();
        CountDownLatch reconnected = new CountDownLatch(1);
        CountDownLatch healthyCheck = new CountDownLatch(1);
        CountDownLatch laterCheck = new CountDownLatch(1);

        doAnswer(invocation -> {
                    if (healthChecks.incrementAndGet() == 1) {
                        firstCheckIdentity.set(identity.get());
                        throw new IllegalStateException("force reconnection");
                    }
                    return null;
                })
                .when(transport)
                .checkHealth();
        when(transport.sendInitializeRequest(any())).thenAnswer(invocation -> {
            if (initializations.incrementAndGet() == 2) {
                reconnectIdentity.set(identity.get());
                reconnected.countDown();
            }
            return CompletableFuture.completedFuture("{}");
        });
        when(transport.sendRequest(any(McpCallContext.class))).thenAnswer(invocation -> {
            McpCallContext context = invocation.getArgument(0);
            if (context.message() instanceof McpServerDiscoverRequest) {
                int count = discoveries.incrementAndGet();
                if (count == 2) {
                    reconnectIdentity.set(identity.get());
                    reconnected.countDown();
                } else if (count == 3) {
                    healthyCheckIdentity.set(identity.get());
                    healthyCheck.countDown();
                } else if (foregroundIdentity.get() != null) {
                    laterCheckIdentity.set(identity.get());
                    laterCheck.countDown();
                }
                return CompletableFuture.completedFuture(DISCOVER_RESULT);
            }
            if (context.message() instanceof McpPingRequest) {
                if (foregroundIdentity.get() == null) {
                    healthyCheckIdentity.set(identity.get());
                    healthyCheck.countDown();
                } else {
                    laterCheckIdentity.set(identity.get());
                    laterCheck.countDown();
                }
                return CompletableFuture.completedFuture("{}");
            }
            if (context.message() instanceof McpListToolsRequest) {
                foregroundIdentity.set(identity.get());
                return CompletableFuture.completedFuture(LIST_TOOLS_RESULT);
            }
            throw new AssertionError("Unexpected request: " + context.message());
        });

        identity.set("caller-A");
        try (DefaultMcpClient client = new DefaultMcpClient.Builder()
                .transport(transport)
                .protocolVersion(modern ? "2026-07-28" : "2025-11-25")
                .subscribeToToolListChanges(false)
                .subscribeToPromptListChanges(false)
                .subscribeToResourceListChanges(false)
                .autoHealthCheckInterval(Duration.ofMillis(100))
                .build()) {
            identity.remove();

            assertThat(reconnected.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(firstCheckIdentity.get()).isEqualTo("caller-A");
            assertThat(reconnectIdentity.get()).isEqualTo("caller-A");
            assertSchedulerContextCleared(client);

            assertThat(healthyCheck.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(healthyCheckIdentity.get()).isEqualTo("caller-A");
            assertSchedulerContextCleared(client);

            identity.set("caller-B");
            client.listTools();
            assertThat(foregroundIdentity.get()).isEqualTo("caller-B");
            assertThat(identity.get()).isEqualTo("caller-B");
            assertThat(laterCheck.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(laterCheckIdentity.get()).isEqualTo("caller-A");
        } finally {
            identity.remove();
        }
    }

    @Test
    void scheduled_check_works_without_a_context_provider() throws Exception {
        ExecutorProvider.set(null);
        McpTransport transport = mock(McpTransport.class);
        CountDownLatch pinged = new CountDownLatch(1);
        AtomicReference<String> healthIdentity = new AtomicReference<>();
        when(transport.sendInitializeRequest(any())).thenReturn(CompletableFuture.completedFuture("{}"));
        when(transport.sendRequest(any(McpCallContext.class))).thenAnswer(invocation -> {
            healthIdentity.set(identity.get());
            pinged.countDown();
            return CompletableFuture.completedFuture("{}");
        });

        identity.set("caller-A");
        try (DefaultMcpClient client = new DefaultMcpClient.Builder()
                .transport(transport)
                .protocolVersion("2025-11-25")
                .autoHealthCheckInterval(Duration.ofMillis(100))
                .build()) {
            identity.remove();
            assertThat(pinged.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(healthIdentity.get()).isNull();
        } finally {
            identity.remove();
        }
    }

    private void assertSchedulerContextCleared(DefaultMcpClient client) throws Exception {
        Field field = DefaultMcpClient.class.getDeclaredField("healthCheckScheduler");
        field.setAccessible(true);
        ScheduledExecutorService scheduler = (ScheduledExecutorService) field.get(client);
        assertThat(scheduler.submit(identity::get).get(5, TimeUnit.SECONDS)).isNull();
    }

    private final class CapturingProvider implements ExecutorProvider {

        @Override
        public Executor executor() {
            return null;
        }

        @Override
        public CapturedContext captureContext() {
            String captured = identity.get();
            return task -> () -> {
                String previous = identity.get();
                if (captured == null) {
                    identity.remove();
                } else {
                    identity.set(captured);
                }
                try {
                    task.run();
                } finally {
                    if (previous == null) {
                        identity.remove();
                    } else {
                        identity.set(previous);
                    }
                }
            };
        }
    }
}
