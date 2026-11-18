package dev.langchain4j.mcp.client.transport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.http.client.MockHttpClient;
import dev.langchain4j.http.client.MockHttpClient.StreamExchange;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransportListener;
import dev.langchain4j.mcp.protocol.McpInitializeRequest;
import dev.langchain4j.spi.PrioritizedFactory;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StreamableHttpMcpTransportListenerTest {

    private static final String SERVER_URL = "https://mcp.example.test/mcp";
    private static final String PROXY_URL = "http://localhost:4444/mcp";

    private static final List<String> NOTIFICATIONS = new ArrayList<>();

    @TempDir
    Path services;

    private final MockHttpClient httpClient = MockHttpClient.withScriptedStreaming();
    private ClassLoader originalClassLoader;
    private URLClassLoader listenersClassLoader;
    private StreamableHttpMcpTransport transport;

    @BeforeEach
    void rememberClassLoader() {
        NOTIFICATIONS.clear();
        originalClassLoader = Thread.currentThread().getContextClassLoader();
    }

    @AfterEach
    void restoreClassLoader() throws IOException {
        Thread.currentThread().setContextClassLoader(originalClassLoader);
        if (listenersClassLoader != null) {
            listenersClassLoader.close();
        }
        if (transport != null) {
            transport.close();
        }
    }

    @Test
    void should_build_without_listeners() throws Exception {
        transport = builder().build();

        assertThat(requestedUrl()).isEqualTo(SERVER_URL);
        assertThat(NOTIFICATIONS).isEmpty();
    }

    @Test
    void should_let_a_listener_read_and_replace_the_url() throws Exception {
        register(RoutingThroughProxy.class);

        transport = builder().build();

        assertThat(NOTIFICATIONS).containsExactly("proxy saw " + SERVER_URL);
        assertThat(requestedUrl()).isEqualTo(PROXY_URL);
    }

    @Test
    void should_notify_listeners_on_every_build() throws Exception {
        register(Recording.class);
        StreamableHttpMcpTransport.Builder builder = builder();

        builder.build().close();
        transport = builder.build();

        assertThat(NOTIFICATIONS).containsExactly("recorded " + SERVER_URL, "recorded " + SERVER_URL);
    }

    @Test
    void should_notify_listeners_with_higher_priority_first() throws Exception {
        register(Recording.class, RoutingThroughProxy.class);

        transport = builder().build();

        assertThat(NOTIFICATIONS).containsExactly("proxy saw " + SERVER_URL, "recorded " + PROXY_URL);
    }

    @Test
    void should_fail_the_build_when_a_listener_fails() throws Exception {
        register(Failing.class);

        assertThatThrownBy(() -> builder().build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("listener failed");
    }

    private StreamableHttpMcpTransport.Builder builder() {
        return StreamableHttpMcpTransport.builder().url(SERVER_URL).httpClient(httpClient);
    }

    /**
     * Makes the given listeners discoverable the way an application or integration does: through a
     * service file on the thread context class loader. A file in the test resources would instead
     * notify the listeners for every transport built by every test of this module.
     */
    @SafeVarargs
    private void register(Class<? extends StreamableHttpMcpTransportListener>... listeners) throws IOException {
        List<String> names = new ArrayList<>();
        for (Class<? extends StreamableHttpMcpTransportListener> listener : listeners) {
            names.add(listener.getName());
        }
        Path file = services.resolve("META-INF/services/" + StreamableHttpMcpTransportListener.class.getName());
        Files.createDirectories(file.getParent());
        Files.write(file, names);
        listenersClassLoader = new URLClassLoader(new URL[] {services.toUri().toURL()}, originalClassLoader);
        Thread.currentThread().setContextClassLoader(listenersClassLoader);
    }

    private String requestedUrl() throws Exception {
        transport.start(new McpOperationHandler(
                new ConcurrentHashMap<>(),
                List::of,
                transport,
                null,
                () -> {},
                null,
                () -> {},
                null,
                null,
                null,
                null,
                null,
                null));
        CompletableFuture<String> initialized = transport.sendInitializeRequest(new McpInitializeRequest(0L));
        StreamExchange exchange = httpClient.nextExchange();
        initialized.cancel(true);
        return exchange.request().url();
    }

    public static class Recording implements StreamableHttpMcpTransportListener {

        @Override
        public void onNewTransport(StreamableHttpMcpTransport.Builder builder) {
            NOTIFICATIONS.add("recorded " + builder.getUrl());
        }
    }

    public static class RoutingThroughProxy implements StreamableHttpMcpTransportListener, PrioritizedFactory {

        @Override
        public void onNewTransport(StreamableHttpMcpTransport.Builder builder) {
            NOTIFICATIONS.add("proxy saw " + builder.getUrl());
            builder.url(PROXY_URL);
        }

        @Override
        public int priority() {
            return 1;
        }
    }

    public static class Failing implements StreamableHttpMcpTransportListener {

        @Override
        public void onNewTransport(StreamableHttpMcpTransport.Builder builder) {
            throw new IllegalStateException("listener failed");
        }
    }
}
