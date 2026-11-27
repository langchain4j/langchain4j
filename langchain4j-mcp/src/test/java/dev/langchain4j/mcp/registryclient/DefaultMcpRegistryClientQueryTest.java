package dev.langchain4j.mcp.registryclient;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.http.client.jdk.JdkHttpClient;
import dev.langchain4j.mcp.registryclient.model.McpServerListRequest;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class DefaultMcpRegistryClientQueryTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("queries")
    void preservesQueryValues(String name, McpServerListRequest request, Map<String, List<String>> expected)
            throws Exception {
        List<URI> observed = new CopyOnWriteArrayList<>();
        HttpServer server = startServer(observed);
        try {
            assertThat(client(server).listServers(request).getServers()).isEmpty();
            assertThat(observed).hasSize(1);
            assertThat(observed.get(0).getPath()).isEqualTo("/v0/servers");
            assertThat(query(observed.get(0))).containsExactlyInAnyOrderEntriesOf(expected);
        } finally {
            server.stop(0);
        }
    }

    static Stream<Arguments> queries() {
        return Stream.of(
                Arguments.of("no filters", McpServerListRequest.builder().build(), Map.of()),
                search("plain search", "io.example/server"),
                search("spaces and Unicode", "天气 server"),
                search("literal plus", "server+tools"),
                search("ampersand is not another filter", "server&limit=999"),
                search("fragment marker", "server#tools"),
                search("literal percent escape", "server%2Ftools"),
                Arguments.of(
                        "version build metadata",
                        McpServerListRequest.builder().version("1.2.3+build.4").build(),
                        Map.of("version", List.of("1.2.3+build.4"))),
                Arguments.of(
                        "empty filters remain present",
                        McpServerListRequest.builder()
                                .cursor("")
                                .search("")
                                .version("")
                                .build(),
                        Map.of("cursor", List.of(""), "search", List.of(""), "version", List.of(""))),
                Arguments.of(
                        "all filters",
                        McpServerListRequest.builder()
                                .cursor("page+/=&next=2")
                                .limit(10L)
                                .search("io.example/server")
                                .updatedSince(LocalDateTime.of(2026, 11, 27, 1, 2, 3))
                                .version("latest")
                                .build(),
                        Map.of(
                                "cursor", List.of("page+/=&next=2"),
                                "limit", List.of("10"),
                                "search", List.of("io.example/server"),
                                "updated_since", List.of("2026-11-27T01:02:03Z"),
                                "version", List.of("latest"))));
    }

    private static Arguments search(String name, String value) {
        return Arguments.of(
                name, McpServerListRequest.builder().search(value).build(), Map.of("search", List.of(value)));
    }

    @Test
    void returnsOpaqueCursorUnchangedOnNextPage() throws Exception {
        List<URI> observed = new CopyOnWriteArrayList<>();
        HttpServer server = startServer(observed);
        try {
            DefaultMcpRegistryClient client = client(server);
            String cursor = client.listServers(
                            McpServerListRequest.builder().limit(1L).build())
                    .getMetadata()
                    .getNextCursor();

            assertThat(cursor).isEqualTo("page+/=&next=2#tail%25");
            assertThat(client.listServers(McpServerListRequest.builder()
                                    .cursor(cursor)
                                    .limit(1L)
                                    .build())
                            .getServers())
                    .isEmpty();
            assertThat(observed).hasSize(2);
            assertThat(query(observed.get(0))).containsExactlyInAnyOrderEntriesOf(Map.of("limit", List.of("1")));
            assertThat(query(observed.get(1)))
                    .containsExactlyInAnyOrderEntriesOf(Map.of("cursor", List.of(cursor), "limit", List.of("1")));
        } finally {
            server.stop(0);
        }
    }

    private static HttpServer startServer(List<URI> observed) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/v0/servers", exchange -> {
            observed.add(exchange.getRequestURI());
            byte[] body = """
                    {"servers":[],"metadata":{"nextCursor":"page+/=&next=2#tail%25"}}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.start();
        return server;
    }

    private static DefaultMcpRegistryClient client(HttpServer server) {
        return DefaultMcpRegistryClient.builder()
                .baseUrl("http://localhost:" + server.getAddress().getPort())
                .httpClient(JdkHttpClient.builder()
                        .connectTimeout(Duration.ofSeconds(5))
                        .readTimeout(Duration.ofSeconds(5))
                        .build())
                .build();
    }

    private static Map<String, List<String>> query(URI uri) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        if (uri.getRawQuery() != null) {
            for (String parameter : uri.getRawQuery().split("&")) {
                String[] pair = parameter.split("=", 2);
                String name = URLDecoder.decode(pair[0], StandardCharsets.UTF_8);
                String value = URLDecoder.decode(pair.length == 2 ? pair[1] : "", StandardCharsets.UTF_8);
                result.computeIfAbsent(name, ignored -> new ArrayList<>()).add(value);
            }
        }
        return result;
    }
}
