package dev.langchain4j.mcp.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.exception.ToolExecutionException;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpOperationHandler;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.protocol.McpCallToolRequest;
import dev.langchain4j.mcp.protocol.McpInitializationNotification;
import dev.langchain4j.mcp.protocol.McpInitializeRequest;
import dev.langchain4j.mcp.protocol.McpJsonRpcMessage;
import dev.langchain4j.mcp.server.transport.StdioMcpServerTransport;
import dev.langchain4j.mcp.transport.stdio.JsonRpcIoHandler;
import dev.langchain4j.service.tool.ToolExecutionResult;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class McpServerErrorIT {

    private static final int PIPE_BUFFER_SIZE = 10240;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final long UNKNOWN_TOOL_REQUEST_ID = 10_000L;

    @Test
    void should_handle_errors_without_exiting_transport() throws Exception {
        ExecutorService serverExecutor = Executors.newSingleThreadExecutor();
        StdioMcpServerTransport serverTransport = null;

        try (PipedInputStream serverInputStream = new PipedInputStream(PIPE_BUFFER_SIZE);
                PipedOutputStream clientOutputStream = new PipedOutputStream(serverInputStream);
                PipedInputStream clientInputStream = new PipedInputStream(PIPE_BUFFER_SIZE);
                PipedOutputStream serverOutputStream = new PipedOutputStream(clientInputStream)) {
            McpServer server = new McpServer(List.of(new ErrorTool()));
            CountDownLatch serverReady = new CountDownLatch(1);
            AtomicReference<StdioMcpServerTransport> serverTransportRef = new AtomicReference<>();

            serverExecutor.submit(() -> {
                StdioMcpServerTransport transport =
                        new StdioMcpServerTransport(serverInputStream, serverOutputStream, server);
                serverTransportRef.set(transport);
                serverReady.countDown();
                return transport;
            });

            assertThat(serverReady.await(2, TimeUnit.SECONDS)).isTrue();
            serverTransport = serverTransportRef.get();

            McpTransport transport = new InMemoryMcpTransport(clientInputStream, clientOutputStream);
            try (McpClient client = DefaultMcpClient.builder()
                    .transport(transport)
                    .autoHealthCheck(false)
                    .toolExecutionTimeout(Duration.ofSeconds(2))
                    .build()) {
                List<ToolSpecification> tools = client.listTools();
                ToolSpecification failTool = toolByName(tools, "fail");
                ToolSpecification addTool = toolByName(tools, "add");

                Map<String, Object> failArgs = toolArgumentsFor(failTool, "boom");
                ToolExecutionRequest failRequest = ToolExecutionRequest.builder()
                        .name("fail")
                        .arguments(toJson(failArgs))
                        .build();

                assertThatThrownBy(() -> client.executeTool(failRequest))
                        .isInstanceOf(ToolExecutionException.class)
                        .hasMessageContaining("Invalid input");

                JsonNode unknownToolResponse = executeRawCall(
                        transport,
                        "non_existent_tool",
                        OBJECT_MAPPER.createObjectNode());
                assertThat(unknownToolResponse.has("error")).isFalse();
                JsonNode unknownToolResult = unknownToolResponse.get("result");
                assertThat(unknownToolResult.get("isError").asBoolean()).isTrue();
                assertThat(unknownToolResult.get("content").get(0).get("text").asText())
                        .contains("Unknown tool");

                Map<String, Object> missingArgs = toolArgumentsFor(addTool, 1L);
                ToolExecutionRequest missingArgsRequest = ToolExecutionRequest.builder()
                        .name("add")
                        .arguments(toJson(missingArgs))
                        .build();

                assertThatThrownBy(() -> client.executeTool(missingArgsRequest))
                        .isInstanceOf(ToolExecutionException.class)
                        .hasMessageContaining("Missing args");

                Map<String, Object> validArgs = toolArgumentsFor(addTool, 1L, 2L);
                ToolExecutionRequest validRequest = ToolExecutionRequest.builder()
                        .name("add")
                        .arguments(toJson(validArgs))
                        .build();

                ToolExecutionResult result = client.executeTool(validRequest);
                assertThat(result.resultText()).isEqualTo("3");
            }
        } finally {
            if (serverTransport != null) {
                serverTransport.close();
            }
            serverExecutor.shutdownNow();
            boolean terminated = serverExecutor.awaitTermination(2, TimeUnit.SECONDS);
            assertThat(terminated).isTrue();
        }
    }

    private static ToolSpecification toolByName(List<ToolSpecification> tools, String name) {
        return tools.stream()
                .filter(tool -> name.equals(tool.name()))
                .findFirst()
                .orElseThrow();
    }

    private static String toJson(Map<String, Object> arguments) throws JsonProcessingException {
        return OBJECT_MAPPER.writeValueAsString(arguments);
    }

    @SuppressWarnings("SameParameterValue")
    private static JsonNode executeRawCall(
            McpTransport transport,
            String toolName,
            ObjectNode arguments) throws Exception {
        McpCallToolRequest request = new McpCallToolRequest(
                UNKNOWN_TOOL_REQUEST_ID,
                toolName,
                arguments);
        return transport.executeOperationWithResponse(request)
                .get(2, TimeUnit.SECONDS);
    }

    private static Map<String, Object> toolArgumentsFor(ToolSpecification tool, Object... values) {
        if (tool.parameters() == null || tool.parameters().properties() == null) {
            return Map.of();
        }
        List<String> keys = tool.parameters().properties().keySet().stream().toList();
        Map<String, Object> args = new LinkedHashMap<>();
        int count = Math.min(values.length, keys.size());
        for (int i = 0; i < count; i++) {
            args.put(keys.get(i), values[i]);
        }
        return args;
    }

    static class ErrorTool {
        @Tool
        @SuppressWarnings("unused")
        public String fail(String input) {
            throw new IllegalArgumentException("Invalid input");
        }

        @Tool
        public long add(Long a, Long b) {
            if (a == null || b == null) {
                throw new IllegalArgumentException("Missing args");
            }
            return a + b;
        }
    }

    private static class InMemoryMcpTransport implements McpTransport {

        private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

        private final InputStream input;
        private final OutputStream output;
        private McpOperationHandler messageHandler;
        private JsonRpcIoHandler ioHandler;

        private InMemoryMcpTransport(InputStream input, OutputStream output) {
            this.input = input;
            this.output = output;
        }

        @Override
        public void start(McpOperationHandler messageHandler) {
            this.messageHandler = messageHandler;
            this.ioHandler = new JsonRpcIoHandler(input, output, messageHandler::handle, false);
            Thread thread = new Thread(ioHandler, "mcp-client-stdio");
            thread.setDaemon(true);
            thread.start();
        }

        @Override
        public CompletableFuture<JsonNode> initialize(McpInitializeRequest request) {
            try {
                String requestString = OBJECT_MAPPER.writeValueAsString(request);
                String initializationNotification =
                        OBJECT_MAPPER.writeValueAsString(new McpInitializationNotification());
                return execute(requestString, request.getId())
                        .thenCompose(originalResponse -> execute(initializationNotification, null)
                                .thenCompose(ignored -> CompletableFuture.completedFuture(originalResponse)));
            } catch (JsonProcessingException e) {
                return CompletableFuture.failedFuture(e);
            }
        }

        @Override
        public CompletableFuture<JsonNode> executeOperationWithResponse(McpJsonRpcMessage request) {
            try {
                String requestString = OBJECT_MAPPER.writeValueAsString(request);
                return execute(requestString, request.getId());
            } catch (JsonProcessingException e) {
                return CompletableFuture.failedFuture(e);
            }
        }

        @Override
        public void executeOperationWithoutResponse(McpJsonRpcMessage request) {
            try {
                String requestString = OBJECT_MAPPER.writeValueAsString(request);
                execute(requestString, null);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void checkHealth() {
            // No-op: in-memory transport has no external process to check.
        }

        @Override
        public void onFailure(Runnable actionOnFailure) {
            // No-op: in-memory transport does not support reconnection.
        }

        @Override
        public void close() throws IOException {
            if (ioHandler != null) {
                ioHandler.close();
            }
        }

        private CompletableFuture<JsonNode> execute(String request, Long id) {
            CompletableFuture<JsonNode> future = new CompletableFuture<>();
            if (id != null) {
                messageHandler.startOperation(id, future);
            }
            try {
                ioHandler.submit(request);
                if (id == null) {
                    future.complete(null);
                }
            } catch (IOException e) {
                future.completeExceptionally(e);
            }
            return future;
        }
    }
}
