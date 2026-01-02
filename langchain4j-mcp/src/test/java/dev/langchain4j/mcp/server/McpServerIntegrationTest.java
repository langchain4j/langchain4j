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
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class McpServerIntegrationTest {

    private static final int PIPE_BUFFER_SIZE = 10240;

    @Test
    void should_execute_tool_over_piped_stdio() throws Exception {
        ExecutorService serverExecutor = Executors.newSingleThreadExecutor();
        StdioMcpServerTransport serverTransport = null;
        McpClient client = null;

        try (PipedInputStream serverInputStream = new PipedInputStream(PIPE_BUFFER_SIZE);
                PipedOutputStream clientOutputStream = new PipedOutputStream(serverInputStream);
                PipedInputStream clientInputStream = new PipedInputStream(PIPE_BUFFER_SIZE);
                PipedOutputStream serverOutputStream = new PipedOutputStream(clientInputStream)) {
            McpServer server = new McpServer(List.of(new Calculator()));
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
            client = DefaultMcpClient.builder()
                    .transport(transport)
                    .autoHealthCheck(false)
                    .build();

            List<ToolSpecification> tools = client.listTools();
            assertThat(tools).extracting(ToolSpecification::name).contains("add");

            ToolSpecification addTool = tools.stream()
                    .filter(tool -> "add".equals(tool.name()))
                    .findFirst()
                    .orElseThrow();
            Map<String, Object> argumentsMap = toolArgumentsFor(addTool, 1, 2);
            ObjectMapper objectMapper = new ObjectMapper();
            String arguments = objectMapper.writeValueAsString(argumentsMap);
            ToolExecutionRequest request = ToolExecutionRequest.builder()
                    .name("add")
                    .arguments(arguments)
                    .build();

            ToolExecutionResult result = client.executeTool(request);
            assertThat(result.resultText()).isEqualTo("3");
        } finally {
            if (client != null) {
                client.close();
            }
            if (serverTransport != null) {
                serverTransport.close();
            }
            serverExecutor.shutdownNow();
            serverExecutor.awaitTermination(2, TimeUnit.SECONDS);
        }
    }

    private static Map<String, Object> toolArgumentsFor(ToolSpecification tool, long a, long b) {
        if (tool.parameters() == null || tool.parameters().properties() == null) {
            return Map.of("a", a, "b", b);
        }
        List<String> keys = tool.parameters().properties().keySet().stream().toList();
        if (keys.contains("a") && keys.contains("b")) {
            return Map.of("a", a, "b", b);
        }
        if (keys.size() >= 2) {
            return Map.of(keys.get(0), a, keys.get(1), b);
        }
        return Map.of("a", a, "b", b);
    }

    static class Calculator {
        @Tool
        public long add(long a, long b) {
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
