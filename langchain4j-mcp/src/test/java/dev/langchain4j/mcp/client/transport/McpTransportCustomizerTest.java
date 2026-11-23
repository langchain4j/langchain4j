package dev.langchain4j.mcp.client.transport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.http.client.MockHttpClient;
import dev.langchain4j.http.client.MockHttpClient.StreamExchange;
import dev.langchain4j.http.client.jdk.JdkHttpClient;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import dev.langchain4j.mcp.client.transport.websocket.WebSocketMcpTransport;
import dev.langchain4j.mcp.protocol.McpInitializeRequest;
import dev.langchain4j.spi.PrioritizedFactory;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import javax.net.ssl.SSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

class McpTransportCustomizerTest {

    private static final String SERVER_URL = "https://mcp.example.test/mcp";
    private static final String PROXY_URL = "http://localhost:4444/mcp";
    private static final String WEBSOCKET_URL = "ws://mcp.example.test/mcp/ws";

    private static final List<String> NOTIFICATIONS = new ArrayList<>();
    private static final List<Object> CUSTOMIZED = new ArrayList<>();

    @TempDir
    Path services;

    private final MockHttpClient httpClient = MockHttpClient.withScriptedStreaming();
    private ClassLoader originalClassLoader;
    private URLClassLoader customizersClassLoader;
    private StreamableHttpMcpTransport transport;

    @BeforeEach
    void rememberClassLoader() {
        NOTIFICATIONS.clear();
        CUSTOMIZED.clear();
        originalClassLoader = Thread.currentThread().getContextClassLoader();
    }

    @AfterEach
    void restoreClassLoader() throws IOException {
        Thread.currentThread().setContextClassLoader(originalClassLoader);
        if (customizersClassLoader != null) {
            customizersClassLoader.close();
        }
        if (transport != null) {
            transport.close();
        }
    }

    @Test
    void should_build_without_customizers() throws Exception {
        transport = builder().build();

        assertThat(requestedUrl()).isEqualTo(SERVER_URL);
        assertThat(NOTIFICATIONS).isEmpty();
    }

    @Test
    void should_let_a_customizer_read_and_replace_the_url() throws Exception {
        register(RoutingThroughProxy.class);

        transport = builder().build();

        assertThat(NOTIFICATIONS).containsExactly("proxy saw " + SERVER_URL);
        assertThat(requestedUrl()).isEqualTo(PROXY_URL);
    }

    @Test
    void should_let_a_customizer_read_the_name() throws Exception {
        register(Recording.class);

        transport = builder().name("weather").build();

        assertThat(NOTIFICATIONS).containsExactly("http " + SERVER_URL + " named weather");
    }

    @Test
    void should_have_no_name_unless_one_is_set() throws Exception {
        register(Recording.class);

        transport = builder().build();

        assertThat(NOTIFICATIONS).containsExactly("http " + SERVER_URL + " named null");
    }

    @Test
    void should_customize_a_copy_and_leave_the_builder_of_the_application_unchanged() throws Exception {
        register(RoutingThroughProxy.class);
        StreamableHttpMcpTransport.Builder builder = builder();

        builder.build().close();
        transport = builder.build();

        assertThat(NOTIFICATIONS).containsExactly("proxy saw " + SERVER_URL, "proxy saw " + SERVER_URL);
        assertThat(builder.getUrl()).isEqualTo(SERVER_URL);
        assertThat(requestedUrl()).isEqualTo(PROXY_URL);
    }

    @Test
    void should_call_customizers_with_higher_priority_first() throws Exception {
        register(Recording.class, RoutingThroughProxy.class);

        transport = builder().build();

        assertThat(NOTIFICATIONS).containsExactly("proxy saw " + SERVER_URL, "http " + PROXY_URL + " named null");
    }

    @Test
    void should_customize_when_the_constructor_is_called() throws Exception {
        register(RoutingThroughProxy.class);

        transport = new StreamableHttpMcpTransport(builder());

        assertThat(NOTIFICATIONS).containsExactly("proxy saw " + SERVER_URL);
        assertThat(requestedUrl()).isEqualTo(PROXY_URL);
    }

    @Test
    void should_look_up_customizers_each_time_a_transport_is_created() throws Exception {
        StreamableHttpMcpTransport.Builder builder = builder();
        register(Recording.class);
        builder.build().close();
        Thread.currentThread().setContextClassLoader(originalClassLoader);

        transport = builder.build();

        assertThat(NOTIFICATIONS).containsExactly("http " + SERVER_URL + " named null");
    }

    @Test
    void should_fail_with_a_clear_message_when_no_url_is_set() throws Exception {
        register(Recording.class);

        assertThatThrownBy(() -> StreamableHttpMcpTransport.builder()
                        .httpClient(httpClient)
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Missing server endpoint URL");
        assertThat(NOTIFICATIONS).containsExactly("http null named null");
    }

    @Test
    void should_fail_the_build_when_a_customizer_fails() throws Exception {
        register(Failing.class);

        assertThatThrownBy(() -> builder().build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("customizer failed");
    }

    @Test
    void should_give_a_customizer_a_copy_with_every_setting_of_the_http_builder() throws Exception {
        register(Capturing.class);
        StreamableHttpMcpTransport.Builder builder = StreamableHttpMcpTransport.builder()
                .httpClientBuilder(JdkHttpClient.builder())
                .httpClient(httpClient)
                .executor(Runnable::run)
                .url(SERVER_URL)
                .name("weather")
                .customHeaders(Map.of("X-Test", "1"))
                .timeout(Duration.ofSeconds(5))
                .logRequests(true)
                .logResponses(true)
                .logger(LoggerFactory.getLogger("test"))
                .sslContext(SSLContext.getDefault())
                .setHttpVersion1_1()
                .subsidiaryChannel(true)
                .followRedirects(true)
                .maxMessageSize(1234);

        assertThatThrownBy(builder::build).hasMessageContaining("not both"); // the customizer ran before

        assertCopiedWithEverySetting(builder);
    }

    @Test
    void should_let_a_customizer_read_the_url_and_name_of_a_websocket_transport() throws Exception {
        register(Recording.class);

        WebSocketMcpTransport.builder().url(WEBSOCKET_URL).name("weather").build().close();
        WebSocketMcpTransport.builder().url(WEBSOCKET_URL).build().close();

        assertThat(NOTIFICATIONS)
                .containsExactly("websocket " + WEBSOCKET_URL + " named weather", "websocket " + WEBSOCKET_URL + " named null");
    }

    @Test
    void should_create_the_websocket_transport_from_the_customized_copy() throws Exception {
        register(RemovingTheWebSocketUrl.class);
        WebSocketMcpTransport.Builder builder = WebSocketMcpTransport.builder().url(WEBSOCKET_URL);

        assertThatThrownBy(builder::build)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Missing server endpoint URL");
        assertThat(builder.getUrl()).isEqualTo(WEBSOCKET_URL);
    }

    @Test
    void should_customize_a_websocket_transport_when_the_constructor_is_called() throws Exception {
        register(RemovingTheWebSocketUrl.class);

        assertThatThrownBy(() -> new WebSocketMcpTransport(
                        WebSocketMcpTransport.builder().url(WEBSOCKET_URL)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Missing server endpoint URL");
    }

    @Test
    void should_give_a_customizer_a_copy_with_every_setting_of_the_websocket_builder() throws Exception {
        register(Capturing.class);
        WebSocketMcpTransport.Builder builder = WebSocketMcpTransport.builder()
                .logResponses(true)
                .logRequests(true)
                .url(WEBSOCKET_URL)
                .name("weather")
                .logger(LoggerFactory.getLogger("test"))
                .executor(Runnable::run)
                .timeout(Duration.ofSeconds(5))
                .sslContext(SSLContext.getDefault())
                .headersSupplier(context -> Map.of("X-Test", "1"))
                .maxMessageSize(1234);

        builder.build().close();

        assertCopiedWithEverySetting(builder);
    }

    private StreamableHttpMcpTransport.Builder builder() {
        return StreamableHttpMcpTransport.builder().url(SERVER_URL).httpClient(httpClient);
    }

    /**
     * Every field of the builder is set (so a new field fails this test until the test sets it too), and the
     * customizer got another builder with the same value in every field (so a field missing from the copy fails).
     */
    private static void assertCopiedWithEverySetting(Object builder) throws IllegalAccessException {
        assertThat(CUSTOMIZED).hasSize(1);
        Object copy = CUSTOMIZED.get(0);
        assertThat(copy).isNotSameAs(builder).hasSameClassAs(builder);
        for (Field field : builder.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            field.setAccessible(true);
            Object value = field.get(builder);
            assertThat(value).as("%s is set by the test", field.getName()).isNotNull().isNotEqualTo(false);
            assertThat(field.get(copy)).as("%s is copied", field.getName()).isSameAs(value);
        }
    }

    /**
     * Makes the given customizers discoverable the way an application or integration does: through a
     * service file on the thread context class loader. A file in the test resources would instead
     * customize every transport built by every test of this module.
     */
    @SafeVarargs
    private void register(Class<? extends McpTransportCustomizer>... customizers) throws IOException {
        List<String> names = new ArrayList<>();
        for (Class<? extends McpTransportCustomizer> customizer : customizers) {
            names.add(customizer.getName());
        }
        Path file = services.resolve("META-INF/services/" + McpTransportCustomizer.class.getName());
        Files.createDirectories(file.getParent());
        Files.write(file, names);
        customizersClassLoader = new URLClassLoader(new URL[] {services.toUri().toURL()}, originalClassLoader);
        Thread.currentThread().setContextClassLoader(customizersClassLoader);
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

    public static class Recording implements McpTransportCustomizer {

        @Override
        public void customize(StreamableHttpMcpTransport.Builder builder) {
            NOTIFICATIONS.add("http " + builder.getUrl() + " named " + builder.getName());
        }

        @Override
        public void customize(WebSocketMcpTransport.Builder builder) {
            NOTIFICATIONS.add("websocket " + builder.getUrl() + " named " + builder.getName());
        }
    }

    public static class RoutingThroughProxy implements McpTransportCustomizer, PrioritizedFactory {

        @Override
        public void customize(StreamableHttpMcpTransport.Builder builder) {
            NOTIFICATIONS.add("proxy saw " + builder.getUrl());
            builder.url(PROXY_URL);
        }

        @Override
        public int priority() {
            return 1;
        }
    }

    public static class RemovingTheWebSocketUrl implements McpTransportCustomizer {

        @Override
        public void customize(WebSocketMcpTransport.Builder builder) {
            builder.url(null);
        }
    }

    public static class Capturing implements McpTransportCustomizer {

        @Override
        public void customize(StreamableHttpMcpTransport.Builder builder) {
            CUSTOMIZED.add(builder);
        }

        @Override
        public void customize(WebSocketMcpTransport.Builder builder) {
            CUSTOMIZED.add(builder);
        }
    }

    public static class Failing implements McpTransportCustomizer {

        @Override
        public void customize(StreamableHttpMcpTransport.Builder builder) {
            throw new IllegalStateException("customizer failed");
        }
    }
}
