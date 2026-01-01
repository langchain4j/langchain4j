package dev.langchain4j.mcp.server;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.mcp.server.transport.StdioMcpServerTransport;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

class McpServerProtocolIT {

    private static final int PIPE_BUFFER_SIZE = 10240;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void should_allow_call_before_initialize() throws Exception {
        try (ServerHarness harness = new ServerHarness(new McpServer(List.of(new EchoTool())))) {
            String request = jsonRequest(
                    1L,
                    "tools/call",
                    Map.of(
                            "name", "echo",
                            "arguments", Map.of("input", "hello", "arg0", "hello")));
            harness.client().send(request);

            JsonNode response = harness.client().readResponse(Duration.ofSeconds(2));
            assertThat(response.get("id").asLong()).isEqualTo(1L);
            assertThat(response.has("error")).isFalse();
            assertThat(response.get("result").get("content").get(0).get("text").asText())
                    .isEqualTo("hello");
        }
    }

    @Test
    void should_ignore_malformed_json_and_continue() throws Exception {
        try (ServerHarness harness = new ServerHarness(new McpServer(List.of(new EchoTool())))) {
            harness.client().send("{\"jsonrpc\":\"2.0\",\"method\":\"tools/list\"");

            String request = jsonRequest(2L, "tools/list", Map.of());
            harness.client().send(request);

            JsonNode response = harness.client().readResponse(Duration.ofSeconds(2));
            assertThat(response.get("id").asLong()).isEqualTo(2L);
            assertThat(response.has("error")).isFalse();
            List<String> toolNames = StreamSupport.stream(
                            response.get("result").get("tools").spliterator(),
                            false)
                    .map(tool -> tool.get("name").asText())
                    .toList();
            assertThat(toolNames).contains("echo");
        }
    }

    @Test
    void should_return_initialize_response() throws Exception {
        try (ServerHarness harness = new ServerHarness(new McpServer(List.of(new EchoTool())))) {
            String request = jsonRequest(
                    3L,
                    "initialize",
                    Map.of("protocolVersion", "2025-06-18"));
            harness.client().send(request);

            JsonNode response = harness.client().readResponse(Duration.ofSeconds(2));
            assertThat(response.get("id").asLong()).isEqualTo(3L);
            assertThat(response.has("error")).isFalse();

            JsonNode result = response.get("result");
            assertThat(result.get("protocolVersion").asText()).isEqualTo("2025-06-18");
            assertThat(result.get("capabilities").get("tools").asBoolean()).isTrue();

            JsonNode serverInfo = result.get("serverInfo");
            if (serverInfo != null && !serverInfo.isNull()) {
                assertThat(serverInfo.get("name").asText()).isEqualTo("LangChain4j");
                assertThat(serverInfo.get("version").asText()).isNotBlank();
            }
        }
    }

    private static String jsonRequest(long id, String method, Map<String, Object> params)
            throws JsonProcessingException {
        ObjectNode node = OBJECT_MAPPER.createObjectNode();
        node.put("jsonrpc", "2.0");
        node.put("id", id);
        node.put("method", method);
        if (params != null) {
            node.set("params", OBJECT_MAPPER.valueToTree(params));
        }
        return OBJECT_MAPPER.writeValueAsString(node);
    }

    static class EchoTool {
        @Tool
        @SuppressWarnings("unused")
        public String echo(String input) {
            return input;
        }
    }

    private static class ServerHarness implements AutoCloseable {

        private final PipedInputStream serverInputStream;
        private final PipedOutputStream clientOutputStream;
        private final PipedInputStream clientInputStream;
        private final PipedOutputStream serverOutputStream;
        private final StdioMcpServerTransport serverTransport;
        private final RawJsonClient client;

        private ServerHarness(McpServer server) throws IOException {
            this.serverInputStream = new PipedInputStream(PIPE_BUFFER_SIZE);
            this.clientOutputStream = new PipedOutputStream(serverInputStream);
            this.clientInputStream = new PipedInputStream(PIPE_BUFFER_SIZE);
            this.serverOutputStream = new PipedOutputStream(clientInputStream);
            this.serverTransport = new StdioMcpServerTransport(serverInputStream, serverOutputStream, server);
            this.client = new RawJsonClient(clientInputStream, clientOutputStream);
        }

        private RawJsonClient client() {
            return client;
        }

        @Override
        public void close() throws IOException {
            client.close();
            serverTransport.close();
            serverOutputStream.close();
            clientInputStream.close();
            clientOutputStream.close();
            serverInputStream.close();
        }
    }

    private static class RawJsonClient implements AutoCloseable {

        private final PrintWriter writer;
        private final BufferedReader reader;
        private final ExecutorService readerExecutor = Executors.newSingleThreadExecutor();

        private RawJsonClient(InputStream input, OutputStream output) {
            this.writer = new PrintWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8), true);
            this.reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
        }

        private void send(String json) {
            writer.println(json);
        }

        private JsonNode readResponse(Duration timeout) throws Exception {
            CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
                try {
                    return reader.readLine();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }, readerExecutor);
            String line = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (line == null) {
                throw new IllegalStateException("Server closed the response stream");
            }
            return OBJECT_MAPPER.readTree(line);
        }

        @Override
        public void close() throws IOException {
            writer.close();
            reader.close();
            readerExecutor.shutdownNow();
            boolean terminated;
            try {
                terminated = readerExecutor.awaitTermination(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
            assertThat(terminated).isTrue();
        }
    }
}
