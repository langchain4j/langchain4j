package dev.langchain4j.mcp.client;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.exception.HttpException;
import dev.langchain4j.http.client.MockHttpClient;
import dev.langchain4j.http.client.MockHttpClient.StreamExchange;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import dev.langchain4j.spi.CapturedContext;
import dev.langchain4j.spi.ExecutorProvider;
import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;

/**
 * {@link ExecutorProvider#set(ExecutorProvider)} is process-wide, so this test runs alone.
 */
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
class DefaultMcpClientAutoHealthCheckHttpContextTest {

    private static final Pattern REQUEST_ID = Pattern.compile("\\\"id\\\":(\\d+)");
    private final ThreadLocal<String> identity = new ThreadLocal<>();

    @Test
    void scheduled_ping_and_reconnection_submit_http_requests_in_initialization_context() throws Exception {
        ExecutorProvider previousProvider = ExecutorProvider.get();
        ExecutorService caller = Executors.newSingleThreadExecutor();
        AtomicReference<DefaultMcpClient> client = new AtomicReference<>();
        MockHttpClient httpClient = MockHttpClient.withScriptedStreaming().withContextProbe(identity::get);
        StreamableHttpMcpTransport transport = StreamableHttpMcpTransport.builder()
                .url("https://mcp.example.test/mcp")
                .httpClient(httpClient)
                .build();
        try {
            ExecutorProvider.set(new ExecutorProvider() {
                @Override
                public Executor executor() {
                    return null;
                }

                @Override
                public CapturedContext captureContext() {
                    String captured = identity.get();
                    return task -> () -> {
                        String previous = identity.get();
                        setIdentity(captured);
                        try {
                            task.run();
                        } finally {
                            setIdentity(previous);
                        }
                    };
                }
            });

            Future<DefaultMcpClient> building = caller.submit(() -> {
                identity.set("session-A");
                try {
                    DefaultMcpClient built = new DefaultMcpClient.Builder()
                            .transport(transport)
                            .protocolVersion("2025-11-25")
                            .autoHealthCheckInterval(Duration.ofMillis(200))
                            .build();
                    client.set(built);
                    return built;
                } finally {
                    identity.remove();
                }
            });

            StreamExchange initialize = httpClient.nextExchange();
            assertThat(initialize.request().body()).contains("\"method\":\"initialize\"");
            assertThat(initialize.streamContext()).isEqualTo("session-A");
            initialize.respond(200, initializeResult(initialize));
            StreamExchange initialized = httpClient.nextExchange();
            assertThat(initialized.request().body()).contains("notifications/initialized");
            initialized.respond(202, null);
            building.get(5, TimeUnit.SECONDS);
            assertThat(identity.get()).isNull();

            StreamExchange ping = httpClient.nextExchange();
            assertThat(ping.request().body()).contains("\"method\":\"ping\"");
            assertThat(ping.streamContext()).isEqualTo("session-A");
            ping.fail(new HttpException(401, "unauthorized"));

            StreamExchange reconnect = httpClient.nextExchange();
            assertThat(reconnect.request().body()).contains("\"method\":\"initialize\"");
            assertThat(reconnect.streamContext()).isEqualTo("session-A");
            reconnect.respond(200, initializeResult(reconnect));
            StreamExchange reinitialized = httpClient.nextExchange();
            assertThat(reinitialized.request().body()).contains("notifications/initialized");
            reinitialized.respond(202, null);
            assertThat(identity.get()).isNull();
        } finally {
            try {
                if (client.get() != null) {
                    client.get().close();
                } else {
                    transport.close();
                }
            } finally {
                caller.shutdownNow();
                ExecutorProvider.set(previousProvider);
                identity.remove();
            }
        }
    }

    private void setIdentity(String value) {
        if (value == null) {
            identity.remove();
        } else {
            identity.set(value);
        }
    }

    private static String initializeResult(StreamExchange exchange) {
        Matcher matcher = REQUEST_ID.matcher(exchange.request().body());
        assertThat(matcher.find()).isTrue();
        return "{\"jsonrpc\":\"2.0\",\"id\":" + matcher.group(1)
                + ",\"result\":{\"protocolVersion\":\"2025-11-25\",\"capabilities\":{},"
                + "\"serverInfo\":{\"name\":\"test\",\"version\":\"1\"}}}";
    }
}
