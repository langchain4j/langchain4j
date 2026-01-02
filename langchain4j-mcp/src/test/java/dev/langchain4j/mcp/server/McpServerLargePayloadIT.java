package dev.langchain4j.mcp.server;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpOperationHandler;
import dev.langchain4j.mcp.client.transport.McpTransport;
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
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class McpServerLargePayloadIT {

    private static final int PIPE_BUFFER_SIZE = 1024;
    private static final int PAYLOAD_SIZE_BYTES = 1024 * 1024;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void should_handle_large_payload_without_deadlock() throws Exception {
        ExecutorService serverExecutor = Executors.newSingleThreadExecutor();
        ExecutorService clientExecutor = Executors.newSingleThreadExecutor();
        StdioMcpServerTransport serverTransport = null;

        try (PipedInputStream serverInputStream = new PipedInputStream(PIPE_BUFFER_SIZE);
                PipedOutputStream clientOutputStream = new PipedOutputStream(serverInputStream);
                PipedInputStream clientInputStream = new PipedInputStream(PIPE_BUFFER_SIZE);
                PipedOutputStream serverOutputStream = new PipedOutputStream(clientInputStream)) {
            McpServer server = new McpServer(List.of(new EchoTool()));
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
                    .toolExecutionTimeout(Duration.ofSeconds(10))
                    .build()) {
                ToolSpecification echoTool = toolByName(client.listTools(), "echo");
                String payload = generatePayload(PAYLOAD_SIZE_BYTES);
                Map<String, Object> arguments = toolArgumentsFor(echoTool, payload);

                ToolExecutionRequest request = ToolExecutionRequest.builder()
                        .name(echoTool.name())
                        .arguments(toJson(arguments))
                        .build();

                CompletableFuture<ToolExecutionResult> future = CompletableFuture.supplyAsync(
                        () -> client.executeTool(request),
                        clientExecutor);
                ToolExecutionResult result = future.get(15, TimeUnit.SECONDS);

                assertThat(result.resultText()).isEqualTo(payload);
            }
        } finally {
            if (serverTransport != null) {
                serverTransport.close();
            }
            serverExecutor.shutdownNow();
            clientExecutor.shutdownNow();
            boolean serverTerminated = serverExecutor.awaitTermination(2, TimeUnit.SECONDS);
            boolean clientTerminated = clientExecutor.awaitTermination(2, TimeUnit.SECONDS);
            assertThat(serverTerminated).isTrue();
            assertThat(clientTerminated).isTrue();
        }
    }

    @SuppressWarnings("SameParameterValue")
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
    private static Map<String, Object> toolArgumentsFor(ToolSpecification tool, String input) {
        if (tool.parameters() == null || tool.parameters().properties() == null) {
            return Map.of("input", input);
        }
        List<String> keys = tool.parameters().properties().keySet().stream().toList();
        if (keys.contains("input")) {
            return Map.of("input", input);
        }
        if (!keys.isEmpty()) {
            return Map.of(keys.get(0), input);
        }
        return Map.of("input", input);
    }

    @SuppressWarnings("SameParameterValue")
    private static String generatePayload(int size) {
        Random random = new Random(42);
        StringBuilder builder = new StringBuilder(size);
        for (int i = 0; i < size; i++) {
            builder.append((char) ('a' + random.nextInt(26)));
        }
        return builder.toString();
    }

    static class EchoTool {
        @Tool
        @SuppressWarnings("unused")
        public String echo(String input) {
            return input;
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
