package dev.langchain4j.mcp.resourcesastools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.internal.Json;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import dev.langchain4j.service.tool.ToolExecutor;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class ListResourcesToolExecutorTest {

    @Test
    void forwards_invocation_context_to_both_lists_on_every_client() {
        InvocationContext context = InvocationContext.builder().build();
        McpClient first = mock(McpClient.class);
        McpClient second = mock(McpClient.class);
        for (McpClient client : List.of(first, second)) {
            when(client.listResources(context)).thenReturn(List.of());
            when(client.listResourceTemplates(context)).thenReturn(List.of());
        }

        ToolExecutor executor = presenter().createListResourcesExecutor(List.of(first, second));
        assertThat(executor.executeWithContext(null, context).resultText()).isEqualTo("[]");

        for (McpClient client : List.of(first, second)) {
            verify(client).listResources(context);
            verify(client).listResourceTemplates(context);
            verify(client, never()).listResourceTemplates();
        }
    }

    @Test
    void legacy_execution_keeps_null_context_for_both_lists() {
        McpClient client = mock(McpClient.class);
        when(client.listResources(null)).thenReturn(List.of());
        when(client.listResourceTemplates(null)).thenReturn(List.of());

        assertThat(presenter().createListResourcesExecutor(List.of(client)).execute(null, null))
                .isEqualTo("[]");

        verify(client).listResources(null);
        verify(client).listResourceTemplates(null);
        verify(client, never()).listResourceTemplates();
    }

    @Test
    @Timeout(20)
    void invocation_metadata_reaches_every_resource_and_template_page() throws Exception {
        List<ObservedRequest> requests = new CopyOnWriteArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/mcp", exchange -> respond(exchange, requests));
        server.start();
        try (McpClient client = new DefaultMcpClient.Builder()
                .key("test")
                .transport(StreamableHttpMcpTransport.builder()
                        .url("http://127.0.0.1:" + server.getAddress().getPort() + "/mcp")
                        .setHttpVersion1_1()
                        .build())
                .protocolVersion("2025-11-25")
                .initializationTimeout(Duration.ofSeconds(3))
                .resourcesTimeout(Duration.ofSeconds(3))
                .autoHealthCheck(false)
                .cacheResourceList(false)
                .metaSupplier(context -> context.invocationContext() == null
                        ? Map.of()
                        : Map.of(
                                "test/invocation",
                                context.invocationContext().invocationId().toString()))
                .build()) {
            ToolExecutor executor = presenter().createListResourcesExecutor(List.of(client));
            InvocationContext first = InvocationContext.builder()
                    .invocationId(UUID.fromString("00000000-0000-0000-0000-000000000001"))
                    .build();
            InvocationContext second = InvocationContext.builder()
                    .invocationId(UUID.fromString("00000000-0000-0000-0000-000000000002"))
                    .build();

            for (InvocationContext context : List.of(first, second)) {
                String result = executor.executeWithContext(null, context).resultText();
                assertThat(result).contains("resource-first", "resource-next", "template-first", "template-next");
            }

            for (String method : List.of("resources/list", "resources/templates/list")) {
                assertThat(requests.stream().filter(request -> method.equals(request.method())))
                        .extracting(ObservedRequest::invocation)
                        .containsExactly(
                                first.invocationId().toString(),
                                first.invocationId().toString(),
                                second.invocationId().toString(),
                                second.invocationId().toString());
                assertThat(requests.stream().filter(request -> method.equals(request.method())))
                        .extracting(ObservedRequest::cursor)
                        .containsExactly(null, "next", null, "next");
            }
        } finally {
            server.stop(0);
        }
    }

    private static DefaultMcpResourcesAsToolsPresenter presenter() {
        return DefaultMcpResourcesAsToolsPresenter.builder().build();
    }

    @SuppressWarnings("unchecked")
    private static void respond(HttpExchange exchange, List<ObservedRequest> requests) throws IOException {
        try (exchange) {
            Map<String, Object> request = Json.fromJson(
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8), Map.class);
            String method = (String) request.get("method");
            if (!request.containsKey("id")) {
                exchange.sendResponseHeaders(202, -1);
                return;
            }
            Map<String, Object> result;
            if ("initialize".equals(method)) {
                result = Map.of(
                        "protocolVersion", "2025-11-25",
                        "capabilities", Map.of("resources", Map.of()),
                        "serverInfo", Map.of("name", "test", "version", "1"));
            } else if ("resources/list".equals(method) || "resources/templates/list".equals(method)) {
                Map<String, Object> params = (Map<String, Object>) request.getOrDefault("params", Map.of());
                Map<String, Object> meta = (Map<String, Object>) params.getOrDefault("_meta", Map.of());
                String cursor = (String) params.get("cursor");
                requests.add(new ObservedRequest(method, cursor, (String) meta.get("test/invocation")));
                boolean first = cursor == null;
                String page = first ? "first" : "next";
                if ("resources/list".equals(method)) {
                    List<?> resources = List.of(Map.of("uri", "test://" + page, "name", "resource-" + page));
                    result = first
                            ? Map.of("resources", resources, "nextCursor", "next")
                            : Map.of("resources", resources);
                } else {
                    List<?> templates =
                            List.of(Map.of("uriTemplate", "test://" + page + "/{id}", "name", "template-" + page));
                    result = first
                            ? Map.of("resourceTemplates", templates, "nextCursor", "next")
                            : Map.of("resourceTemplates", templates);
                }
            } else {
                result = Map.of();
            }
            byte[] body = Json.toJson(Map.of("jsonrpc", "2.0", "id", request.get("id"), "result", result))
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
        }
    }

    private record ObservedRequest(String method, String cursor, String invocation) {}
}
