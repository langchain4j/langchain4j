package dev.langchain4j.mcp.client.transport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.HttpClientBuilderFactory;
import dev.langchain4j.http.client.MockHttpClient;
import dev.langchain4j.http.client.jdk.JdkHttpClient;
import dev.langchain4j.http.client.jdk.JdkHttpClientBuilder;
import dev.langchain4j.http.client.jdk.JdkHttpClientBuilderFactory;
import dev.langchain4j.http.client.okhttp.OkHttpClient;
import dev.langchain4j.http.client.okhttp.OkHttpClientBuilderFactory;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import dev.langchain4j.mcp.protocol.McpListToolsRequest;
import dev.langchain4j.spi.ServiceHelper;
import java.io.File;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.net.ssl.SSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class StreamableHttpMcpTransportDiscoveryTest {

    private static final String PROPERTY = "langchain4j.http.clientBuilderFactory";
    private static final String URL = "https://mcp.example.test/mcp";
    private String previousFactory;

    @BeforeEach
    void clearFactoryOverride() {
        previousFactory = System.clearProperty(PROPERTY);
    }

    @AfterEach
    void restoreFactoryOverride() {
        if (previousFactory == null) {
            System.clearProperty(PROPERTY);
        } else {
            System.setProperty(PROPERTY, previousFactory);
        }
    }

    @Test
    void should_use_jdk_client_when_other_clients_are_on_the_classpath() throws Exception {
        // The JDK, OkHttp and Apache clients are all on this module's test classpath.
        try (var transport = StreamableHttpMcpTransport.builder().url(URL).build()) {
            assertThat(httpClientOf(transport)).isInstanceOf(JdkHttpClient.class);
        }
    }

    @Test
    void should_use_jdk_client_when_the_system_property_names_it() throws Exception {
        System.setProperty(PROPERTY, JdkHttpClientBuilderFactory.class.getName());
        try (var transport = StreamableHttpMcpTransport.builder().url(URL).build()) {
            assertThat(httpClientOf(transport)).isInstanceOf(JdkHttpClient.class);
        }
    }

    @Test
    void should_use_the_client_named_by_the_system_property() throws Exception {
        System.setProperty(PROPERTY, OkHttpClientBuilderFactory.class.getName());
        try (var transport = StreamableHttpMcpTransport.builder().url(URL).build()) {
            assertThat(httpClientOf(transport)).isInstanceOf(OkHttpClient.class);
        }
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = 13)
    void should_configure_timeouts_send_requests_and_close_the_selected_client(Integer timeout) throws Exception {
        MockHttpClient client = MockHttpClient.withScriptedStreaming();
        HttpClientBuilder clientBuilder = mock(HttpClientBuilder.class, RETURNS_SELF);
        when(clientBuilder.build()).thenReturn(client);
        HttpClientBuilderFactory factory = () -> clientBuilder;
        System.setProperty(PROPERTY, factory.getClass().getName());
        try (var services = mockStatic(ServiceHelper.class, CALLS_REAL_METHODS)) {
            services.when(() -> ServiceHelper.loadFactories(HttpClientBuilderFactory.class))
                    .thenReturn(List.of(new JdkHttpClientBuilderFactory(), factory));
            var builder = StreamableHttpMcpTransport.builder().url(URL);
            if (timeout != null) {
                builder.timeout(Duration.ofSeconds(timeout));
            }
            try (var transport = builder.build()) {
                verify(clientBuilder).connectTimeout(Duration.ofSeconds(timeout == null ? 60 : timeout));
                verify(clientBuilder).readTimeout(Duration.ZERO);
                start(transport);
                var response = transport.sendRequest(new McpListToolsRequest(1L, null));
                var exchange = client.nextExchange();
                exchange.open(200, Map.of("Content-Type", List.of("application/json")));
                String result = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"tools\":[]}}";
                exchange.body(result);
                exchange.complete();
                assertThat(response.get(5, TimeUnit.SECONDS)).isEqualTo(result);
            }
            assertThat(client.closeCount()).isEqualTo(1);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"sslContext", "setHttpVersion1_1", "followRedirects"})
    void should_reject_jdk_options_before_building_another_client(String option) throws Exception {
        HttpClientBuilder clientBuilder = mock(HttpClientBuilder.class, RETURNS_SELF);
        HttpClientBuilderFactory factory = () -> clientBuilder;
        System.setProperty(PROPERTY, factory.getClass().getName());
        try (var services = mockStatic(ServiceHelper.class, CALLS_REAL_METHODS)) {
            services.when(() -> ServiceHelper.loadFactories(HttpClientBuilderFactory.class))
                    .thenReturn(List.of(factory));
            var builder = StreamableHttpMcpTransport.builder().url(URL);
            switch (option) {
                case "sslContext" -> builder.sslContext(SSLContext.getDefault());
                case "setHttpVersion1_1" -> builder.setHttpVersion1_1();
                case "followRedirects" -> builder.followRedirects(true);
            }
            assertThatThrownBy(builder::build)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("only with the JDK HTTP client")
                    .hasMessageContaining(option);
            verify(clientBuilder, never()).build();
        }
    }

    @Test
    void should_keep_okhttp_stream_open_beyond_its_default_read_timeout() throws Exception {
        System.setProperty(PROPERTY, OkHttpClientBuilderFactory.class.getName());
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<Void> headersSent = new CompletableFuture<>();
        String result = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"tools\":[]}}";
        server.createContext("/mcp", exchange -> {
            try (exchange) {
                exchange.getRequestBody().readAllBytes();
                exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
                exchange.sendResponseHeaders(200, 0);
                exchange.getResponseBody().write(": connected\n\n".getBytes(StandardCharsets.UTF_8));
                exchange.getResponseBody().flush();
                headersSent.complete(null);
                if (release.await(20, TimeUnit.SECONDS)) {
                    exchange.getResponseBody().write(("data: " + result + "\n\n").getBytes(StandardCharsets.UTF_8));
                }
            } catch (Exception e) {
                headersSent.completeExceptionally(e);
            }
        });
        server.start();
        try (var transport = StreamableHttpMcpTransport.builder()
                .url("http://localhost:" + server.getAddress().getPort() + "/mcp")
                .build()) {
            start(transport);
            var response = transport.sendRequest(new McpListToolsRequest(1L, null));
            headersSent.get(5, TimeUnit.SECONDS);
            // OkHttp's unconfigured read timeout would end this idle stream after ten seconds.
            assertThatThrownBy(() -> response.get(11, TimeUnit.SECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown();
            assertThat(McpJson.toValue(response.get(5, TimeUnit.SECONDS))).isEqualTo(McpJson.toValue(result));
        } finally {
            release.countDown();
            server.stop(0);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"okhttp", "apache"})
    void should_discover_a_real_backend_without_jdk_on_the_classpath(String backend) throws Exception {
        List<URL> urls = new ArrayList<>();
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        for (String entry : classpath.split(File.pathSeparator)) {
            if (!entry.contains("langchain4j-http-client-jdk")
                    && !entry.contains("langchain4j-http-client-" + (backend.equals("okhttp") ? "apache" : "okhttp"))) {
                urls.add(Path.of(entry).toUri().toURL());
            }
        }
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        try (var loader = new URLClassLoader(urls.toArray(URL[]::new), ClassLoader.getPlatformClassLoader())) {
            Thread.currentThread().setContextClassLoader(loader);
            assertThatThrownBy(() -> loader.loadClass(JdkHttpClientBuilder.class.getName()))
                    .isInstanceOf(ClassNotFoundException.class);
            Class<?> transportType = loader.loadClass(StreamableHttpMcpTransport.class.getName());
            Object builder = transportType.getMethod("builder").invoke(null);
            builder.getClass().getMethod("url", String.class).invoke(builder, URL);
            try (var transport =
                    (AutoCloseable) builder.getClass().getMethod("build").invoke(builder)) {
                assertThat(httpClientOf(transport).getClass().getName()).containsIgnoringCase(backend);
            }
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
        }
    }

    private static Object httpClientOf(Object transport) throws Exception {
        Field field = transport.getClass().getDeclaredField("httpClient");
        field.setAccessible(true);
        return field.get(transport);
    }

    private static void start(StreamableHttpMcpTransport transport) {
        transport.start(new McpOperationHandler(
                new ConcurrentHashMap<>(),
                List::of,
                transport,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null));
    }
}
