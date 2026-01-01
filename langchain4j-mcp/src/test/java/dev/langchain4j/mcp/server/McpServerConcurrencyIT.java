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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import org.junit.jupiter.api.Test;

class McpServerConcurrencyIT {

    private static final int PIPE_BUFFER_SIZE = 10240;
    private static final int REQUEST_COUNT = 50;
    private static final int CLIENT_POOL_SIZE = 8;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void should_handle_concurrent_requests_without_id_mixup() throws Exception {
        ExecutorService serverExecutor = Executors.newSingleThreadExecutor();
        ExecutorService clientExecutor = Executors.newFixedThreadPool(CLIENT_POOL_SIZE);
        StdioMcpServerTransport serverTransport = null;

        try (PipedInputStream serverInputStream = new PipedInputStream(PIPE_BUFFER_SIZE);
                PipedOutputStream clientOutputStream = new PipedOutputStream(serverInputStream);
                PipedInputStream clientInputStream = new PipedInputStream(PIPE_BUFFER_SIZE);
                PipedOutputStream serverOutputStream = new PipedOutputStream(clientInputStream)) {
            McpServer server = new McpServer(List.of(new SlowTool()));
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
                    .toolExecutionTimeout(Duration.ofSeconds(5))
                    .build()) {
                ToolSpecification slowTool = toolByName(client.listTools(), "slowEcho");
                List<String> expected = new ArrayList<>();
                List<ToolExecutionRequest> requests = new ArrayList<>();

                for (int i = 0; i < REQUEST_COUNT; i++) {
                    String value = "req-" + i;
                    expected.add(value);
                    Map<String, Object> argumentsMap = toolArgumentsFor(slowTool, value);
                    requests.add(ToolExecutionRequest.builder()
                            .name(slowTool.name())
                            .arguments(toJson(argumentsMap))
                            .build());
                }

                List<CompletableFuture<String>> futures = new ArrayList<>();
                for (ToolExecutionRequest request : requests) {
                    futures.add(CompletableFuture.supplyAsync(
                            () -> client.executeTool(request).resultText(),
                            clientExecutor));
                }

                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                        .get(10, TimeUnit.SECONDS);

                for (int i = 0; i < futures.size(); i++) {
                    assertThat(futures.get(i).get()).isEqualTo(expected.get(i));
                }
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

    private static Map<String, Object> toolArgumentsFor(ToolSpecification tool, String value) {
        if (tool.parameters() == null || tool.parameters().properties() == null) {
            return Map.of("value", value);
        }
        List<String> keys = tool.parameters().properties().keySet().stream().toList();
        if (keys.contains("value")) {
            return Map.of("value", value);
        }
        if (!keys.isEmpty()) {
            return Map.of(keys.get(0), value);
        }
        return Map.of("value", value);
    }

    static class SlowTool {
        @Tool
        @SuppressWarnings("unused")
        public String slowEcho(String value) {
            long delayMillis = ThreadLocalRandom.current().nextLong(10, 50);
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(delayMillis));
            if (Thread.currentThread().isInterrupted()) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting");
            }
            return value;
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
