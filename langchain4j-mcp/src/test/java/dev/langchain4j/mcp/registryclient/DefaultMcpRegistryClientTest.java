package dev.langchain4j.mcp.registryclient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.jdk.JdkHttpClient;
import dev.langchain4j.mcp.registryclient.model.McpServerListRequest;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;

class DefaultMcpRegistryClientTest {

    @ParameterizedTest(name = "{0} accepts immutable headers")
    @MethodSource("operations")
    void acceptsImmutableHeaders(String name, Function<DefaultMcpRegistryClient, Object> operation) {
        HttpClient http = mock(HttpClient.class);
        when(http.execute(any()))
                .thenReturn(SuccessfulHttpResponse.builder()
                        .statusCode(200)
                        .body("{}")
                        .build());
        Map<String, String> headers = Map.of("X-Registry-Key", "test-key");
        DefaultMcpRegistryClient client = DefaultMcpRegistryClient.builder()
                .httpClient(http)
                .headers(headers)
                .build();

        assertThat(operation.apply(client)).isNotNull();

        ArgumentCaptor<HttpRequest> request = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).execute(request.capture());
        assertThat(request.getValue().headers())
                .containsEntry("X-Registry-Key", List.of("test-key"))
                .containsEntry("Content-Type", List.of("application/json"))
                .containsEntry("Accept", List.of("application/json, application/problem+json"));
        assertThat(headers).containsExactlyEntriesOf(Map.of("X-Registry-Key", "test-key"));
    }

    static Stream<Arguments> operations() {
        return Stream.of(
                operation(
                        "list",
                        client -> client.listServers(
                                McpServerListRequest.builder().build())),
                operation("details", client -> client.getServerDetails("io.example/server")),
                operation("version", client -> client.getSpecificServerVersion("io.example/server", "1.0")),
                operation("versions", client -> client.getAllVersionsOfServer("io.example/server")),
                operation("health", DefaultMcpRegistryClient::healthCheck),
                operation("ping", DefaultMcpRegistryClient::ping));
    }

    private static Arguments operation(String name, Function<DefaultMcpRegistryClient, Object> operation) {
        return Arguments.of(name, operation);
    }

    @Test
    void doesNotWriteDefaultsIntoCallerHeaders() {
        HttpClient http = mock(HttpClient.class);
        when(http.execute(any()))
                .thenReturn(SuccessfulHttpResponse.builder()
                        .statusCode(200)
                        .body("{\"pong\":true}")
                        .build());
        Map<String, String> headers = new HashMap<>();
        headers.put("Accept", "application/custom+json");
        headers.put("Content-Type", "application/custom+json");
        headers.put("X-Registry-Key", "test-key");
        Map<String, String> original = Map.copyOf(headers);

        assertThat(DefaultMcpRegistryClient.builder()
                        .httpClient(http)
                        .headers(headers)
                        .build()
                        .ping()
                        .pong())
                .isTrue();

        assertThat(headers).containsExactlyInAnyOrderEntriesOf(original);
        ArgumentCaptor<HttpRequest> request = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).execute(request.capture());
        // Keep the existing protocol defaults on the request without replacing the caller's values.
        assertThat(request.getValue().headers())
                .containsEntry("Accept", List.of("application/json, application/problem+json"))
                .containsEntry("Content-Type", List.of("application/json"));
    }

    @Test
    void snapshotsFreshSupplierHeadersForEachRealRequest() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        List<String> observed = new CopyOnWriteArrayList<>();
        server.createContext("/v0/ping", exchange -> {
            observed.add(exchange.getRequestHeaders().getFirst("X-Registry-Key"));
            byte[] body = "{\"pong\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.start();
        try {
            Map<String, String> first = Map.of("X-Registry-Key", "first");
            Map<String, String> second = Map.of("X-Registry-Key", "second");
            AtomicReference<Map<String, String>> current = new AtomicReference<>(first);
            AtomicInteger supplierCalls = new AtomicInteger();
            DefaultMcpRegistryClient client = DefaultMcpRegistryClient.builder()
                    .baseUrl("http://localhost:" + server.getAddress().getPort())
                    .httpClient(JdkHttpClient.builder()
                            .connectTimeout(Duration.ofSeconds(5))
                            .readTimeout(Duration.ofSeconds(5))
                            .build())
                    .headersSupplier(() -> {
                        supplierCalls.incrementAndGet();
                        return current.get();
                    })
                    .build();

            assertThat(client.ping().pong()).isTrue();
            current.set(second);
            assertThat(client.ping().pong()).isTrue();

            assertThat(observed).containsExactly("first", "second");
            assertThat(supplierCalls).hasValue(2);
            assertThat(first).containsExactlyEntriesOf(Map.of("X-Registry-Key", "first"));
            assertThat(second).containsExactlyEntriesOf(Map.of("X-Registry-Key", "second"));
        } finally {
            server.stop(0);
        }
    }
}
