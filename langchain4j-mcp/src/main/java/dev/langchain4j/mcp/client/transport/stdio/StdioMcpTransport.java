package dev.langchain4j.mcp.client.transport.stdio;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.mcp.client.protocol.CancellationNotification;
import dev.langchain4j.mcp.client.protocol.McpCallToolRequest;
import dev.langchain4j.mcp.client.protocol.McpInitializeRequest;
import dev.langchain4j.mcp.client.protocol.McpListToolsRequest;
import dev.langchain4j.mcp.client.transport.McpOperationHandler;
import dev.langchain4j.mcp.client.transport.McpTransport;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StdioMcpTransport implements McpTransport {

    private final List<String> command;
    private final Map<String, String> environment;
    private Process process;
    private ProcessIOHandler processIOHandler;
    private final boolean logEvents;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Logger log = LoggerFactory.getLogger(StdioMcpTransport.class);
    private volatile McpOperationHandler messageHandler;

    public StdioMcpTransport(Builder builder) {
        this.command = builder.command;
        this.environment = builder.environment;
        this.logEvents = builder.logEvents;
    }

    @Override
    public void start(McpOperationHandler messageHandler) {
        this.messageHandler = messageHandler;
        log.debug("Starting process: {}", command);
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.environment().putAll(environment);
        try {
            process = processBuilder.start();
            log.debug("PID of the started process: {}", process.pid());
            process.onExit().thenRun(() -> {
                log.debug("Subprocess has exited with code: {}", process.exitValue());
            });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        processIOHandler = new ProcessIOHandler(process, messageHandler, logEvents);
        // FIXME: where should we obtain the thread?
        new Thread(processIOHandler).start();
        new Thread(new ProcessStderrHandler(process)).start();
    }

    @Override
    public CompletableFuture<JsonNode> initialize(McpInitializeRequest operation) {
        try {
            String requestString = OBJECT_MAPPER.writeValueAsString(operation);
            return execute(requestString, operation.getId());
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public CompletableFuture<JsonNode> listTools(McpListToolsRequest operation) {
        try {
            String requestString = OBJECT_MAPPER.writeValueAsString(operation);
            return execute(requestString, operation.getId());
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public CompletableFuture<JsonNode> executeTool(McpCallToolRequest operation) {
        try {
            String requestString = OBJECT_MAPPER.writeValueAsString(operation);
            return execute(requestString, operation.getId());
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void cancelOperation(long operationId) {
        try {
            String requestString =
                    OBJECT_MAPPER.writeValueAsString(new CancellationNotification(operationId, "Timeout"));
            // Note: we're passing a null operationId here because this
            // argument refers to the 'cancellation' notification, not the
            // operation being cancelled. The cancellation is a notification
            // so it does not have any ID and does not expect any response.
            execute(requestString, null);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() throws IOException {
        process.destroy();
    }

    private CompletableFuture<JsonNode> execute(String request, Long id) {
        CompletableFuture<JsonNode> future = new CompletableFuture<>();
        if (id != null) {
            messageHandler.startOperation(id, future);
        }
        try {
            processIOHandler.submit(request);
        } catch (IOException e) {
            future.completeExceptionally(e);
        }
        return future;
    }

    public static class Builder {

        private List<String> command;
        private Map<String, String> environment;
        private boolean logEvents;

        public Builder command(List<String> command) {
            this.command = command;
            return this;
        }

        public Builder environment(Map<String, String> environment) {
            this.environment = environment;
            return this;
        }

        public Builder logEvents(boolean logEvents) {
            this.logEvents = logEvents;
            return this;
        }

        public StdioMcpTransport build() {
            if (command == null || command.isEmpty()) {
                throw new IllegalArgumentException("Missing command");
            }
            if (environment == null) {
                environment = Map.of();
            }
            return new StdioMcpTransport(this);
        }
    }
}
