package dev.langchain4j.mcp.client.transport.stdio;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;

import com.fasterxml.jackson.databind.JsonNode;
import dev.langchain4j.internal.DefaultExecutorProvider;
import dev.langchain4j.mcp.client.McpCallContext;
import dev.langchain4j.mcp.client.transport.McpOperationHandler;
import dev.langchain4j.mcp.client.transport.McpJson;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.protocol.McpClientMessage;
import dev.langchain4j.mcp.protocol.McpInitializationNotification;
import dev.langchain4j.mcp.protocol.McpInitializeRequest;
import dev.langchain4j.mcp.transport.stdio.JsonRpcIoHandler;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StdioMcpTransport implements McpTransport {

    private final List<String> command;
    private final Map<String, String> environment;
    // These are (re)assigned by start(), which may be invoked again from the health-check thread
    // during reconnection, so they are volatile to ensure visibility across threads.
    private volatile Process process;
    private volatile JsonRpcIoHandler jsonRpcIoHandler;
    private final boolean logEvents;
    private final Logger logger;
    private static final Logger log = LoggerFactory.getLogger(StdioMcpTransport.class);
    private volatile McpOperationHandler messageHandler;
    private volatile ProcessStderrHandler stderrHandler;
    private ExecutorService executorService;
    private boolean shouldShutdownExecutorService;

    public StdioMcpTransport(Builder builder) {
        this.command = builder.command;
        this.environment = builder.environment;
        this.logEvents = builder.logEvents;
        this.logger = builder.logger;
        this.executorService =
                getOrDefault(builder.executorService, DefaultExecutorProvider::getDefaultExecutorService);
        // FIXME: are there actually any cases where we should shut down the executor service?
        // the DefaultExecutorProvider always returns a single shared instance, so we can't shut it down
        this.shouldShutdownExecutorService = false;
    }

    @Override
    public void start(McpOperationHandler messageHandler) {
        // start() may be called again during reconnection (e.g. from the health-check thread).
        // Tear down any previous process and I/O handlers first, otherwise the old subprocess and
        // its handler tasks are leaked on every reconnect.
        stopCurrentProcess();
        this.messageHandler = messageHandler;
        log.debug("Starting process: {}", command);
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.environment().putAll(environment);
        try {
            process = processBuilder.start();
            log.debug("PID of the started process: {}", process.pid());
            process.onExit().thenRun(() -> {
                if (messageHandler != null) {
                    messageHandler.cancelAllPendingOperations("Process has exited");
                }
                log.debug("Subprocess has exited with code: {}", process.exitValue());
            });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        jsonRpcIoHandler = new JsonRpcIoHandler(
                process.getInputStream(), process.getOutputStream(), messageHandler::onMessage, logEvents, logger);
        stderrHandler = new ProcessStderrHandler(process);
        executorService.submit(jsonRpcIoHandler);
        executorService.submit(stderrHandler);
    }

    @Override
    public CompletableFuture<String> sendInitializeRequest(McpInitializeRequest operation) {
        try {
            String requestString = McpJson.serialize(operation);
            String initializationNotification = McpJson.serialize(new McpInitializationNotification());
            return execute(requestString, operation.getId())
                    .thenCompose(originalResponse -> execute(initializationNotification, null)
                            .thenCompose(nullNode -> CompletableFuture.completedFuture(originalResponse)));
        } catch (IllegalArgumentException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override
    public CompletableFuture<String> sendRequest(McpClientMessage operation) {
        return sendRequest(new McpCallContext(null, operation));
    }

    @Override
    public CompletableFuture<String> sendRequest(McpCallContext context) {
        try {
            String requestString = McpJson.serialize(context.message());
            return execute(requestString, context.message().getId());
        } catch (IllegalArgumentException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override
    public void sendMessage(McpClientMessage operation) {
        sendMessage(new McpCallContext(null, operation));
    }

    @Override
    public void sendMessage(McpCallContext context) {
        String requestString = McpJson.serialize(context.message());
        execute(requestString, null);
    }

    @Override
    public void checkHealth() {
        if (!process.isAlive()) {
            throw new IllegalStateException("Process is not alive");
        }
    }

    @Override
    public boolean requiresCancellationNotification() {
        return true;
    }

    @Override
    public void onFailure(Runnable actionOnFailure) {
        // ignore, for stdio transport, we currently don't do reconnection attempts
    }

    /**
     * Closes the current I/O handlers and destroys the current subprocess, if any.
     * Does not shut down the (potentially shared) executor service, so it is safe to call
     * before starting a replacement process during reconnection.
     */
    private void stopCurrentProcess() {
        if (stderrHandler != null) {
            try {
                stderrHandler.close();
            } catch (Exception ignored) {
            }
            stderrHandler = null;
        }
        if (jsonRpcIoHandler != null) {
            try {
                jsonRpcIoHandler.close();
            } catch (Exception ignored) {
            }
            jsonRpcIoHandler = null;
        }
        if (process != null) {
            process.destroy();
            process = null;
        }
    }

    @Override
    public void close() throws IOException {
        stopCurrentProcess();
        if (executorService != null && shouldShutdownExecutorService) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    private CompletableFuture<String> execute(String request, Long id) {
        CompletableFuture<String> future = new CompletableFuture<>();
        if (id != null) {
            messageHandler.expectResponse(id, future);
        }
        try {
            jsonRpcIoHandler.submit(request);
            // For messages with null ID, we don't wait for a corresponding response
            if (id == null) {
                future.complete(null);
            }
        } catch (IOException e) {
            future.completeExceptionally(e);
        }
        return future;
    }

    public Process getProcess() {
        return process;
    }

    public static class Builder {

        private List<String> command;
        private Map<String, String> environment;
        private boolean logEvents;
        private Logger logger;
        private ExecutorService executorService;

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

        /**
         * Sets the {@link ExecutorService} to use for background I/O operations.
         * If not provided, will use {@link DefaultExecutorProvider#getDefaultExecutorService()}.
         * <p>
         * Frameworks like Quarkus should provide their managed executor here.
         * If an executor is provided, it will not be shut down when the transport is closed.
         *
         * @param executorService the executor service to use
         * @return {@code this}
         */
        public Builder executorService(ExecutorService executorService) {
            this.executorService = executorService;
            return this;
        }

        /**
         * @param logger an alternate {@link Logger} to be used instead of the default one provided by Langchain4J for traffic logging.
         * @return {@code this}.
         */
        public Builder logger(Logger logger) {
            this.logger = logger;
            return this;
        }

        public StdioMcpTransport build() {
            ensureNotEmpty(command, "command");
            if (environment == null) {
                environment = Map.of();
            }
            return new StdioMcpTransport(this);
        }
    }

    /**
     * @deprecated use {@link #sendInitializeRequest(McpInitializeRequest)} instead, which does not
     * expose Jackson types.
     */
    @Deprecated(since = "1.20.0", forRemoval = true)
    @Override
    public CompletableFuture<JsonNode> initialize(McpInitializeRequest request) {
        return McpJson.map(sendInitializeRequest(request), McpJson::parse);
    }

    /**
     * @deprecated use {@link #sendRequest(McpClientMessage)} instead, which does not expose Jackson
     * types.
     */
    @Deprecated(since = "1.20.0", forRemoval = true)
    @Override
    public CompletableFuture<JsonNode> executeOperationWithResponse(McpClientMessage request) {
        return McpJson.map(sendRequest(request), McpJson::parse);
    }

    /**
     * @deprecated use {@link #sendRequest(McpCallContext)} instead, which does not expose Jackson
     * types.
     */
    @Deprecated(since = "1.20.0", forRemoval = true)
    @Override
    public CompletableFuture<JsonNode> executeOperationWithResponse(McpCallContext context) {
        return McpJson.map(sendRequest(context), McpJson::parse);
    }

    /**
     * @deprecated use {@link #sendMessage(McpClientMessage)} instead, which does not expose Jackson
     * types.
     */
    @Deprecated(since = "1.20.0", forRemoval = true)
    @Override
    public void executeOperationWithoutResponse(McpClientMessage request) {
        sendMessage(request);
    }

    /**
     * @deprecated use {@link #sendMessage(McpCallContext)} instead, which does not expose Jackson
     * types.
     */
    @Deprecated(since = "1.20.0", forRemoval = true)
    @Override
    public void executeOperationWithoutResponse(McpCallContext context) {
        sendMessage(context);
    }

}
