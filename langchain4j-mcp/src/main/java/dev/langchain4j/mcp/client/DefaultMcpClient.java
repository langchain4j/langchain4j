package dev.langchain4j.mcp.client;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.exception.ToolArgumentsException;
import dev.langchain4j.exception.ToolExecutionException;
import dev.langchain4j.mcp.client.logging.DefaultMcpLogMessageHandler;
import dev.langchain4j.mcp.client.logging.McpLogMessageHandler;
import dev.langchain4j.mcp.client.transport.McpOperationHandler;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.protocol.McpCallToolRequest;
import dev.langchain4j.mcp.protocol.McpCancellationNotification;
import dev.langchain4j.mcp.protocol.McpGetPromptRequest;
import dev.langchain4j.mcp.protocol.McpImplementation;
import dev.langchain4j.mcp.protocol.McpInitializeParams;
import dev.langchain4j.mcp.protocol.McpInitializeRequest;
import dev.langchain4j.mcp.protocol.McpListPromptsRequest;
import dev.langchain4j.mcp.protocol.McpListResourceTemplatesRequest;
import dev.langchain4j.mcp.protocol.McpListResourcesRequest;
import dev.langchain4j.mcp.protocol.McpListToolsRequest;
import dev.langchain4j.mcp.protocol.McpPingRequest;
import dev.langchain4j.mcp.protocol.McpReadResourceRequest;
import dev.langchain4j.mcp.protocol.McpRootsListChangedNotification;
import dev.langchain4j.service.tool.ToolExecutionResult;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultMcpClient implements McpClient {

    private static final Logger log = LoggerFactory.getLogger(DefaultMcpClient.class);

    static final ObjectMapper OBJECT_MAPPER =
            new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private final AtomicLong idGenerator = new AtomicLong(0);
    private final McpTransport transport;
    private final String key;
    private final String clientName;
    private final String clientVersion;
    private final String protocolVersion;
    private final Duration initializationTimeout;
    private final Duration toolExecutionTimeout;
    private final Duration resourcesTimeout;
    private final Duration promptsTimeout;
    private final Duration pingTimeout;
    private final JsonNode RESULT_TIMEOUT;
    private final String toolExecutionTimeoutErrorMessage;
    private final Map<Long, CompletableFuture<JsonNode>> pendingOperations = new ConcurrentHashMap<>();
    private final McpOperationHandler messageHandler;
    private final McpLogMessageHandler logHandler;
    private final AtomicReference<List<McpResource>> resourceRefs = new AtomicReference<>();
    private final AtomicReference<List<McpResourceTemplate>> resourceTemplateRefs = new AtomicReference<>();
    private final AtomicReference<List<McpPrompt>> promptRefs = new AtomicReference<>();
    private final AtomicReference<List<ToolSpecification>> toolListRefs = new AtomicReference<>();
    private final AtomicBoolean toolListOutOfDate = new AtomicBoolean(true);
    private final AtomicReference<CompletableFuture<Void>> toolListUpdateInProgress = new AtomicReference<>(null);
    private final Duration reconnectInterval;
    private volatile boolean closed = false;
    private final Boolean autoHealthCheck;
    private final Duration autoHealthCheckInterval;
    private final ScheduledExecutorService healthCheckScheduler;
    private final ReentrantLock initializationLock = new ReentrantLock();
    private final AtomicReference<List<McpRoot>> mcpRoots;
    private final Boolean cacheToolList;

    public DefaultMcpClient(Builder builder) {
        try {
            transport = ensureNotNull(builder.transport, "transport");
            key = getOrDefault(builder.key, () -> UUID.randomUUID().toString());
            clientName = getOrDefault(builder.clientName, "langchain4j");
            clientVersion = getOrDefault(builder.clientVersion, "1.0");
            protocolVersion = getOrDefault(builder.protocolVersion, "2025-06-18");
            initializationTimeout = getOrDefault(builder.initializationTimeout, Duration.ofSeconds(30));
            toolExecutionTimeout = getOrDefault(builder.toolExecutionTimeout, Duration.ofSeconds(60));
            resourcesTimeout = getOrDefault(builder.resourcesTimeout, Duration.ofSeconds(60));
            promptsTimeout = getOrDefault(builder.promptsTimeout, Duration.ofSeconds(60));
            logHandler = getOrDefault(builder.logHandler, new DefaultMcpLogMessageHandler());
            pingTimeout = getOrDefault(builder.pingTimeout, Duration.ofSeconds(10));
            reconnectInterval = getOrDefault(builder.reconnectInterval, Duration.ofSeconds(5));
            autoHealthCheck = getOrDefault(builder.autoHealthCheck, Boolean.TRUE);
            autoHealthCheckInterval = getOrDefault(builder.autoHealthCheckInterval, Duration.ofSeconds(30));
            healthCheckScheduler = autoHealthCheck
                    ? Executors.newSingleThreadScheduledExecutor(r -> {
                        Thread t = new Thread(r, "mcp-server-health-checker");
                        t.setDaemon(true);
                        return t;
                    })
                    : null;
            toolExecutionTimeoutErrorMessage =
                    getOrDefault(builder.toolExecutionTimeoutErrorMessage, "There was a timeout executing the tool");
            mcpRoots = new AtomicReference<>(getOrDefault(builder.roots, new ArrayList<>()));
            cacheToolList = getOrDefault(builder.cacheToolList, Boolean.TRUE);
            RESULT_TIMEOUT = JsonNodeFactory.instance.objectNode();
            messageHandler = new McpOperationHandler(
                    pendingOperations,
                    mcpRoots::get,
                    transport,
                    logHandler::handleLogMessage,
                    () -> toolListOutOfDate.set(true));
            ((ObjectNode) RESULT_TIMEOUT)
                    .putObject("result")
                    .putArray("content")
                    .addObject()
                    .put("type", "text")
                    .put("text", toolExecutionTimeoutErrorMessage);
            transport.onFailure(() -> {
                if (!closed) {
                    try {
                        TimeUnit.MILLISECONDS.sleep(reconnectInterval.toMillis());
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    log.info("Trying to reconnect...");
                    triggerReconnection();
                }
            });
            initialize();
            startAutoHealthCheck();
        } catch (RuntimeException e) {
            // Mark the client as closed if initialization fails,
            // so that the transport callback won't try to
            // reinitialize it (indefinitely).
            closed = true;
            throw e;
        }
    }

    private void initialize() {
        transport.start(messageHandler);
        long operationId = idGenerator.getAndIncrement();
        McpInitializeRequest request = new McpInitializeRequest(operationId);
        McpInitializeParams params = createInitializeParams();
        request.setParams(params);
        try {
            JsonNode capabilities =
                    transport.initialize(request).get(initializationTimeout.toMillis(), TimeUnit.MILLISECONDS);
            log.debug("MCP server capabilities: {}", capabilities.get("result"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            pendingOperations.remove(operationId);
        }
    }

    private McpInitializeParams createInitializeParams() {
        McpInitializeParams params = new McpInitializeParams();
        params.setProtocolVersion(protocolVersion);

        McpImplementation clientInfo = new McpImplementation();
        clientInfo.setName(clientName);
        clientInfo.setVersion(clientVersion);
        params.setClientInfo(clientInfo);

        McpInitializeParams.Capabilities capabilities = new McpInitializeParams.Capabilities();
        McpInitializeParams.Capabilities.Roots roots = new McpInitializeParams.Capabilities.Roots();
        roots.setListChanged(true);
        capabilities.setRoots(roots);
        params.setCapabilities(capabilities);

        return params;
    }

    @Override
    public String key() {
        return key;
    }

    @Override
    public List<ToolSpecification> listTools() {
        assertNotClosed();
        if (isToolListRefreshNeeded()) {
            CompletableFuture<Void> updateInProgress = this.toolListUpdateInProgress.get();
            if (updateInProgress != null) {
                // if an update is already in progress, wait for it to finish
                updateInProgress.join();
                return toolListRefs.get();
            } else {
                // if no update is in progress, start one
                CompletableFuture<Void> update = new CompletableFuture<>();
                this.toolListUpdateInProgress.set(update);
                try {
                    obtainToolList();
                } finally {
                    update.complete(null);
                    toolListOutOfDate.set(false);
                    toolListUpdateInProgress.set(null);
                }
                return toolListRefs.get();
            }
        } else {
            return toolListRefs.get();
        }
    }

    private boolean isToolListRefreshNeeded() {
        return Boolean.FALSE.equals(cacheToolList) || toolListOutOfDate.get();
    }

    /**
     * Evicts the tool list cache, forcing the next call to
     * {@link #listTools()} to retrieve a fresh list of tools
     * from the MCP server.
     */
    public void evictToolListCache() {
        toolListOutOfDate.set(true);
    }

    @Override
    public ToolExecutionResult executeTool(ToolExecutionRequest executionRequest) {
        assertNotClosed();
        ObjectNode arguments = null;
        try {
            String args = executionRequest.arguments();
            if (isNullOrBlank(args)) {
                args = "{}";
            }
            arguments = OBJECT_MAPPER.readValue(args, ObjectNode.class);
        } catch (JsonProcessingException e) {
            throw new ToolArgumentsException(e);
        }
        long operationId = idGenerator.getAndIncrement();
        McpCallToolRequest operation = new McpCallToolRequest(operationId, executionRequest.name(), arguments);
        long timeoutMillis = toolExecutionTimeout.toMillis() == 0 ? Integer.MAX_VALUE : toolExecutionTimeout.toMillis();
        CompletableFuture<JsonNode> resultFuture = null;
        JsonNode result = null;
        try {
            resultFuture = transport.executeOperationWithResponse(operation);
            result = resultFuture.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException timeout) {
            transport.executeOperationWithoutResponse(new McpCancellationNotification(operationId, "Timeout"));
            return ToolExecutionHelper.extractResult(RESULT_TIMEOUT);
        } catch (ExecutionException e) {
            throw new ToolExecutionException(e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } finally {
            pendingOperations.remove(operationId);
        }
        return ToolExecutionHelper.extractResult(result);
    }

    @Override
    public List<McpResource> listResources() {
        assertNotClosed();
        if (resourceRefs.get() == null) {
            obtainResourceList();
        }
        return resourceRefs.get();
    }

    @Override
    public McpReadResourceResult readResource(String uri) {
        assertNotClosed();
        final long operationId = idGenerator.getAndIncrement();
        McpReadResourceRequest operation = new McpReadResourceRequest(operationId, uri);
        long timeoutMillis = resourcesTimeout.toMillis() == 0 ? Integer.MAX_VALUE : resourcesTimeout.toMillis();
        JsonNode result = null;
        CompletableFuture<JsonNode> resultFuture = null;
        try {
            resultFuture = transport.executeOperationWithResponse(operation);
            result = resultFuture.get(timeoutMillis, TimeUnit.MILLISECONDS);
            return ResourcesHelper.parseResourceContents(result);
        } catch (ExecutionException | InterruptedException | TimeoutException e) {
            throw new RuntimeException(e);
        } finally {
            pendingOperations.remove(operationId);
        }
    }

    @Override
    public List<McpPrompt> listPrompts() {
        assertNotClosed();
        if (promptRefs.get() == null) {
            obtainPromptList();
        }
        return promptRefs.get();
    }

    @Override
    public McpGetPromptResult getPrompt(String name, Map<String, Object> arguments) {
        assertNotClosed();
        long operationId = idGenerator.getAndIncrement();
        McpGetPromptRequest operation = new McpGetPromptRequest(operationId, name, arguments);
        long timeoutMillis = promptsTimeout.toMillis() == 0 ? Integer.MAX_VALUE : promptsTimeout.toMillis();
        JsonNode result = null;
        CompletableFuture<JsonNode> resultFuture = null;
        try {
            resultFuture = transport.executeOperationWithResponse(operation);
            result = resultFuture.get(timeoutMillis, TimeUnit.MILLISECONDS);
            return PromptsHelper.parsePromptContents(result);
        } catch (ExecutionException | InterruptedException | TimeoutException e) {
            throw new RuntimeException(e);
        } finally {
            pendingOperations.remove(operationId);
        }
    }

    @Override
    public void checkHealth() {
        assertNotClosed();
        transport.checkHealth();
        long operationId = idGenerator.getAndIncrement();
        McpPingRequest ping = new McpPingRequest(operationId);
        try {
            CompletableFuture<JsonNode> resultFuture = transport.executeOperationWithResponse(ping);
            resultFuture.get(pingTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (ExecutionException | InterruptedException | TimeoutException e) {
            throw new RuntimeException(e);
        } finally {
            pendingOperations.remove(operationId);
        }
    }

    @Override
    public void setRoots(final List<McpRoot> roots) {
        this.mcpRoots.set(roots);
        transport.executeOperationWithoutResponse(new McpRootsListChangedNotification());
    }

    @Override
    public List<McpResourceTemplate> listResourceTemplates() {
        assertNotClosed();
        if (resourceTemplateRefs.get() == null) {
            obtainResourceTemplateList();
        }
        return resourceTemplateRefs.get();
    }

    private synchronized void obtainToolList() {
        McpListToolsRequest operation = new McpListToolsRequest(idGenerator.getAndIncrement());
        CompletableFuture<JsonNode> resultFuture = transport.executeOperationWithResponse(operation);
        JsonNode result = null;
        try {
            result = resultFuture.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        } finally {
            pendingOperations.remove(operation.getId());
        }

        final List<ToolSpecification> toolList = ToolSpecificationHelper.toolSpecificationListFromMcpResponse(
                (ArrayNode) result.get("result").get("tools"));
        toolListRefs.set(toolList);
    }

    private synchronized void obtainResourceList() {
        if (resourceRefs.get() != null) {
            return;
        }
        McpListResourcesRequest operation = new McpListResourcesRequest(idGenerator.getAndIncrement());
        long timeoutMillis = resourcesTimeout.toMillis() == 0 ? Integer.MAX_VALUE : resourcesTimeout.toMillis();
        JsonNode result = null;
        CompletableFuture<JsonNode> resultFuture = null;
        try {
            resultFuture = transport.executeOperationWithResponse(operation);
            result = resultFuture.get(timeoutMillis, TimeUnit.MILLISECONDS);
            resourceRefs.set(ResourcesHelper.parseResourceRefs(result));
        } catch (ExecutionException | InterruptedException | TimeoutException e) {
            throw new RuntimeException(e);
        } finally {
            pendingOperations.remove(operation.getId());
        }
    }

    private synchronized void obtainResourceTemplateList() {
        if (resourceTemplateRefs.get() != null) {
            return;
        }
        McpListResourceTemplatesRequest operation = new McpListResourceTemplatesRequest(idGenerator.getAndIncrement());
        long timeoutMillis = toolExecutionTimeout.toMillis() == 0 ? Integer.MAX_VALUE : toolExecutionTimeout.toMillis();
        JsonNode result = null;
        CompletableFuture<JsonNode> resultFuture = null;
        try {
            resultFuture = transport.executeOperationWithResponse(operation);
            result = resultFuture.get(timeoutMillis, TimeUnit.MILLISECONDS);
            resourceTemplateRefs.set(ResourcesHelper.parseResourceTemplateRefs(result));
        } catch (ExecutionException | InterruptedException | TimeoutException e) {
            throw new RuntimeException(e);
        } finally {
            pendingOperations.remove(operation.getId());
        }
    }

    private void startAutoHealthCheck() {
        if (Boolean.FALSE.equals(autoHealthCheck)) {
            return;
        }
        Runnable healthCheckTask = () -> {
            try {
                checkHealth();
            } catch (Exception e) {
                log.warn("mcp server health check failed. Attempting to reconnect...", e);
                triggerReconnection();
            }
        };
        healthCheckScheduler.scheduleAtFixedRate(
                healthCheckTask,
                autoHealthCheckInterval.toMillis(),
                autoHealthCheckInterval.toMillis(),
                TimeUnit.MILLISECONDS);
    }

    private void triggerReconnection() {
        if (initializationLock.tryLock()) {
            try {
                initialize();
            } catch (Exception e) {
                log.warn("mcp server reconnection failed", e);
            } finally {
                initializationLock.unlock();
            }
        }
    }

    private synchronized void obtainPromptList() {
        if (promptRefs.get() != null) {
            return;
        }
        McpListPromptsRequest operation = new McpListPromptsRequest(idGenerator.getAndIncrement());
        long timeoutMillis = promptsTimeout.toMillis() == 0 ? Integer.MAX_VALUE : promptsTimeout.toMillis();
        JsonNode result = null;
        CompletableFuture<JsonNode> resultFuture = null;
        try {
            resultFuture = transport.executeOperationWithResponse(operation);
            result = resultFuture.get(timeoutMillis, TimeUnit.MILLISECONDS);
            promptRefs.set(PromptsHelper.parsePromptRefs(result));
        } catch (ExecutionException | InterruptedException | TimeoutException e) {
            throw new RuntimeException(e);
        } finally {
            pendingOperations.remove(operation.getId());
        }
    }

    @Override
    public void close() {
        closed = true;
        if (healthCheckScheduler != null) {
            healthCheckScheduler.shutdownNow();
        }
        try {
            transport.close();
        } catch (Exception e) {
            log.warn("Cannot close MCP transport", e);
        }
    }

    private void assertNotClosed() {
        if (closed) {
            throw new IllegalStateException("The client is closed");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String toolExecutionTimeoutErrorMessage;
        private McpTransport transport;
        private String key;
        private String clientName;
        private String clientVersion;
        private String protocolVersion;
        private Duration initializationTimeout;
        private Duration toolExecutionTimeout;
        private Duration resourcesTimeout;
        private Duration pingTimeout;
        private Duration promptsTimeout;
        private McpLogMessageHandler logHandler;
        private Duration reconnectInterval;
        private Boolean autoHealthCheck;
        private Duration autoHealthCheckInterval;
        private List<McpRoot> roots;
        private Boolean cacheToolList;

        /**
         * Sets the transport protocol to use for communicating with the
         * MCP server. This is a mandatory parameter. A successfully
         * constructed DefaultMcpClient takes over the resource ownership
         * of this transport and will close it when it itself is closed.
         */
        public Builder transport(McpTransport transport) {
            this.transport = transport;
            return this;
        }

        /**
         * Sets a unique identifier for the client. If none is provided, a
         * UUID will be automatically generated.
         */
        public Builder key(String key) {
            this.key = key;
            return this;
        }

        /**
         * Sets the name that the client will use to identify itself to the
         * MCP server in the initialization message. The default value is
         * "langchain4j".
         */
        public Builder clientName(String clientName) {
            this.clientName = clientName;
            return this;
        }

        /**
         * Sets the version string that the client will use to identify
         * itself to the MCP server in the initialization message. The
         * default value is "1.0".
         */
        public Builder clientVersion(String clientVersion) {
            this.clientVersion = clientVersion;
            return this;
        }

        /**
         * Sets the protocol version that the client will advertise in the
         * initialization message. The default value right now is
         * "2024-11-05", but will change over time in later langchain4j
         * versions.
         */
        public Builder protocolVersion(String protocolVersion) {
            this.protocolVersion = protocolVersion;
            return this;
        }

        /**
         * Sets the timeout for initializing the client.
         * The default value is 30 seconds.
         */
        public Builder initializationTimeout(Duration initializationTimeout) {
            this.initializationTimeout = initializationTimeout;
            return this;
        }

        /**
         * Sets the timeout for tool execution.
         * This value applies to each tool execution individually.
         * The default value is 60 seconds.
         * A value of zero means no timeout.
         */
        public Builder toolExecutionTimeout(Duration toolExecutionTimeout) {
            this.toolExecutionTimeout = toolExecutionTimeout;
            return this;
        }

        /**
         * Sets the timeout for resource-related operations (listing resources as well as reading the contents of a resource).
         * The default value is 60 seconds.
         * A value of zero means no timeout.
         */
        public Builder resourcesTimeout(Duration resourcesTimeout) {
            this.resourcesTimeout = resourcesTimeout;
            return this;
        }

        /**
         * Sets the timeout for prompt-related operations (listing prompts as well as rendering the contents of a prompt).
         * The default value is 60 seconds.
         * A value of zero means no timeout.
         */
        public Builder promptsTimeout(Duration promptsTimeout) {
            this.promptsTimeout = promptsTimeout;
            return this;
        }

        /**
         * Sets the error message to return when a tool execution times out.
         * The default value is "There was a timeout executing the tool".
         */
        public Builder toolExecutionTimeoutErrorMessage(String toolExecutionTimeoutErrorMessage) {
            this.toolExecutionTimeoutErrorMessage = toolExecutionTimeoutErrorMessage;
            return this;
        }

        /**
         * Sets the log message handler for the client.
         */
        public Builder logHandler(McpLogMessageHandler logHandler) {
            this.logHandler = logHandler;
            return this;
        }

        /**
         * The timeout to apply when waiting for a ping response.
         * Currently, this is only used in the health check - if the
         * server does not send a pong within this timeframe, the health
         * check will fail. The timeout is 10 seconds.
         */
        public Builder pingTimeout(Duration pingTimeout) {
            this.pingTimeout = pingTimeout;
            return this;
        }

        /**
         * The delay before attempting to reconnect after a failed connection.
         * The default is 5 seconds.
         */
        public Builder reconnectInterval(Duration reconnectInterval) {
            this.reconnectInterval = reconnectInterval;
            return this;
        }

        /**
         * Enables or disables the automatic health check feature.
         * When enabled, the client will periodically send ping messages to the server
         * to ensure the connection is alive, and will attempt to reconnect if it's not.
         * The default is enabled
         */
        public Builder autoHealthCheck(boolean autoHealthCheck) {
            this.autoHealthCheck = autoHealthCheck;
            return this;
        }

        /**
         * Sets the interval for the automatic health checks.
         * This is only used when the auto health check feature is enabled.
         * The default is 30 seconds
         */
        public Builder autoHealthCheckInterval(Duration interval) {
            this.autoHealthCheckInterval = interval;
            return this;
        }

        /**
         * Specify the initial set of roots that are available to the server upon its request.
         */
        public Builder roots(List<McpRoot> roots) {
            this.roots = new ArrayList<>(roots);
            return this;
        }

        /**
         * If set to true, the client will cache the tool list obtained
         * from the server until it's notified by the server that the tools
         * have changed or until the cache is evicted. If set to false,
         * there is no tool caching and the client will always fetch the
         * tool list from the server.
         * The default is true.
         */
        public Builder cacheToolList(boolean cacheToolList) {
            this.cacheToolList = cacheToolList;
            return this;
        }

        public DefaultMcpClient build() {
            return new DefaultMcpClient(this);
        }
    }
}
