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
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.mcp.client.logging.DefaultMcpLogMessageHandler;
import dev.langchain4j.mcp.client.logging.McpLogMessageHandler;
import dev.langchain4j.mcp.client.progress.McpProgressHandler;
import dev.langchain4j.mcp.client.transport.McpOperationHandler;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.protocol.McpCallToolRequest;
import dev.langchain4j.mcp.protocol.McpCancellationNotification;
import dev.langchain4j.mcp.protocol.McpClientMessage;
import dev.langchain4j.mcp.protocol.McpClientNotification;
import dev.langchain4j.mcp.protocol.McpClientParams;
import dev.langchain4j.mcp.protocol.McpClientRequest;
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
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
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
    private final McpProgressHandler progressHandler;
    private final AtomicReference<List<McpResource>> resourceRefs = new AtomicReference<>();
    private final AtomicReference<List<McpResourceTemplate>> resourceTemplateRefs = new AtomicReference<>();
    private final AtomicReference<List<McpPrompt>> promptRefs = new AtomicReference<>();
    private final AtomicReference<List<ToolSpecification>> toolListRefs = new AtomicReference<>();
    private final AtomicReference<CompletableFuture<List<ToolSpecification>>> toolListUpdateInProgress =
            new AtomicReference<>(null);
    private final AtomicReference<CompletableFuture<List<McpResource>>> resourceListUpdateInProgress =
            new AtomicReference<>(null);
    private final AtomicReference<CompletableFuture<List<McpResourceTemplate>>> resourceTemplateListUpdateInProgress =
            new AtomicReference<>(null);
    private final AtomicReference<CompletableFuture<List<McpPrompt>>> promptListUpdateInProgress =
            new AtomicReference<>(null);
    private final Duration reconnectInterval;
    private volatile boolean closed = false;
    private final Boolean autoHealthCheck;
    private final Duration autoHealthCheckInterval;
    private final ScheduledExecutorService healthCheckScheduler;
    private final ReentrantLock initializationLock = new ReentrantLock();
    private final AtomicReference<List<McpRoot>> mcpRoots;
    private final Boolean cacheToolList;
    private final Boolean cacheResourceList;
    private final Boolean cachePromptList;
    private final McpClientListener listener;
    private final McpMetaSupplier metaSupplier;

    public DefaultMcpClient(Builder builder) {
        try {
            transport = ensureNotNull(builder.transport, "transport");
            key = getOrDefault(builder.key, () -> UUID.randomUUID().toString());
            clientName = getOrDefault(builder.clientName, "langchain4j");
            clientVersion = getOrDefault(builder.clientVersion, "1.0");
            protocolVersion = getOrDefault(builder.protocolVersion, "2025-11-25");
            initializationTimeout = getOrDefault(builder.initializationTimeout, Duration.ofSeconds(30));
            toolExecutionTimeout = getOrDefault(builder.toolExecutionTimeout, Duration.ofSeconds(60));
            resourcesTimeout = getOrDefault(builder.resourcesTimeout, Duration.ofSeconds(60));
            promptsTimeout = getOrDefault(builder.promptsTimeout, Duration.ofSeconds(60));
            logHandler = getOrDefault(builder.logHandler, new DefaultMcpLogMessageHandler());
            progressHandler = builder.progressHandler;
            pingTimeout = getOrDefault(builder.pingTimeout, Duration.ofSeconds(10));
            reconnectInterval = getOrDefault(builder.reconnectInterval, Duration.ofSeconds(5));
            autoHealthCheck = getOrDefault(builder.autoHealthCheck, Boolean.TRUE);
            autoHealthCheckInterval = getOrDefault(builder.autoHealthCheckInterval, Duration.ofSeconds(30));
            listener = builder.listener;
            metaSupplier = builder.metaSupplier;
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
            cacheResourceList = getOrDefault(builder.cacheResourceList, Boolean.TRUE);
            cachePromptList = getOrDefault(builder.cachePromptList, Boolean.TRUE);
            RESULT_TIMEOUT = JsonNodeFactory.instance.objectNode();
            messageHandler = new McpOperationHandler(
                    pendingOperations,
                    mcpRoots::get,
                    transport,
                    logHandler::handleLogMessage,
                    () -> toolListRefs.set(null),
                    () -> {
                        resourceRefs.set(null);
                        resourceTemplateRefs.set(null);
                    },
                    () -> promptRefs.set(null),
                    progressHandler);
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
        applyMeta(request, null);
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
        return listTools(null);
    }

    @Override
    public List<ToolSpecification> listTools(InvocationContext invocationContext) {
        assertNotClosed();
        return retrieveWithPossibleCaching(
                cacheToolList,
                this::obtainToolList,
                toolListUpdateInProgress,
                () -> toolListRefs.get(),
                invocationContext);
    }

    /**
     * Evicts the tool list cache, forcing the next call to
     * {@link #listTools()} to retrieve a fresh list of tools
     * from the MCP server.
     */
    public void evictToolListCache() {
        toolListRefs.set(null);
    }

    @Override
    public ToolExecutionResult executeTool(ToolExecutionRequest executionRequest) {
        return executeTool(executionRequest, null);
    }

    @Override
    public ToolExecutionResult executeTool(ToolExecutionRequest executionRequest, InvocationContext invocationContext) {
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
        String progressToken = progressHandler != null ? String.valueOf(operationId) : null;
        McpCallToolRequest operation =
                new McpCallToolRequest(operationId, executionRequest.name(), arguments, progressToken);
        long timeoutMillis = toolExecutionTimeout.toMillis() == 0 ? Integer.MAX_VALUE : toolExecutionTimeout.toMillis();
        CompletableFuture<JsonNode> resultFuture = null;
        JsonNode result = null;
        McpCallContext context = new McpCallContext(invocationContext, operation);
        try {
            if (listener != null) {
                listener.beforeExecuteTool(context);
            }
            applyMeta(operation, context);
            resultFuture = transport.executeOperationWithResponse(context);
            result = resultFuture.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException timeout) {
            if (listener != null) {
                listener.onExecuteToolError(context, timeout);
            }
            McpCancellationNotification cancellation = new McpCancellationNotification(operationId, "Timeout");
            applyMeta(cancellation, null);
            transport.executeOperationWithoutResponse(cancellation);
            return ToolExecutionHelper.extractResult(RESULT_TIMEOUT, false);
        } catch (ExecutionException e) {
            if (listener != null) {
                listener.onExecuteToolError(context, e);
            }
            throw new ToolExecutionException(e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } finally {
            pendingOperations.remove(operationId);
        }
        try {
            ToolExecutionResult toolResult = ToolExecutionHelper.extractResult(result, false);
            if (listener != null) {
                listener.afterExecuteTool(
                        context, toolResult, (Map<String, Object>) ToolExecutionHelper.toObject(result));
            }
            return toolResult;
        } catch (ToolExecutionException e) {
            if (e.errorCode() != null) {
                // protocol error
                if (listener != null) {
                    listener.onExecuteToolError(context, e);
                }
            } else {
                // application-level error (called "Tool Execution Error" in MCP spec)
                // -> we notify the listener with afterExecuteTool
                if (listener != null) {
                    listener.afterExecuteTool(
                            context, ToolExecutionHelper.extractResult(result, true), (Map<String, Object>)
                                    ToolExecutionHelper.toObject(result));
                }
            }
            throw e;
        }
    }

    @Override
    public List<McpResource> listResources() {
        return listResources(null);
    }

    @Override
    public List<McpResource> listResources(InvocationContext invocationContext) {
        assertNotClosed();
        return retrieveWithPossibleCaching(
                cacheToolList,
                this::obtainResourceList,
                resourceListUpdateInProgress,
                () -> resourceRefs.get(),
                invocationContext);
    }

    @Override
    public McpReadResourceResult readResource(String uri) {
        return readResource(uri, null);
    }

    @Override
    public McpReadResourceResult readResource(String uri, InvocationContext invocationContext) {
        assertNotClosed();
        final long operationId = idGenerator.getAndIncrement();
        McpReadResourceRequest operation = new McpReadResourceRequest(operationId, uri);
        McpCallContext context = new McpCallContext(invocationContext, operation);
        long timeoutMillis = resourcesTimeout.toMillis() == 0 ? Integer.MAX_VALUE : resourcesTimeout.toMillis();
        JsonNode result = null;
        CompletableFuture<JsonNode> resultFuture = null;
        if (listener != null) {
            listener.beforeResourceGet(context);
        }
        applyMeta(operation, context);
        try {
            resultFuture = transport.executeOperationWithResponse(context);
            result = resultFuture.get(timeoutMillis, TimeUnit.MILLISECONDS);
            McpReadResourceResult resourceResult = ResourcesHelper.parseResourceContents(result);
            if (listener != null) {
                listener.afterResourceGet(
                        context, resourceResult, (Map<String, Object>) ToolExecutionHelper.toObject(result));
            }
            return resourceResult;
        } catch (ExecutionException | TimeoutException e) {
            if (listener != null) {
                listener.onResourceGetError(context, e);
            }
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            Thread.interrupted();
            throw new RuntimeException(e);
        } catch (McpException e) {
            if (listener != null) {
                listener.onResourceGetError(context, e);
            }
            throw e;
        } finally {
            pendingOperations.remove(operationId);
        }
    }

    @Override
    public List<McpPrompt> listPrompts() {
        assertNotClosed();
        return retrieveWithPossibleCaching(
                cachePromptList, this::obtainPromptList, promptListUpdateInProgress, () -> promptRefs.get(), null);
    }

    @Override
    public McpGetPromptResult getPrompt(String name, Map<String, Object> arguments) {
        assertNotClosed();
        long operationId = idGenerator.getAndIncrement();
        McpGetPromptRequest operation =
                new McpGetPromptRequest(operationId, name, arguments == null ? Map.of() : arguments);
        McpCallContext context = new McpCallContext(null, operation);
        long timeoutMillis = promptsTimeout.toMillis() == 0 ? Integer.MAX_VALUE : promptsTimeout.toMillis();
        JsonNode result = null;
        CompletableFuture<JsonNode> resultFuture = null;
        if (listener != null) {
            listener.beforePromptGet(context);
        }
        applyMeta(operation, context);
        try {
            resultFuture = transport.executeOperationWithResponse(context);
            result = resultFuture.get(timeoutMillis, TimeUnit.MILLISECONDS);
            McpGetPromptResult promptResult = PromptsHelper.parsePromptContents(result);
            if (listener != null) {
                listener.afterPromptGet(
                        context, promptResult, (Map<String, Object>) ToolExecutionHelper.toObject(result));
            }
            return promptResult;
        } catch (ExecutionException | TimeoutException e) {
            if (listener != null) {
                listener.onPromptGetError(context, e);
            }
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            Thread.interrupted();
            throw new RuntimeException(e);
        } catch (McpException e) {
            if (listener != null) {
                listener.onPromptGetError(context, e);
            }
            throw e;
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
        applyMeta(ping, null);
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
        McpRootsListChangedNotification notification = new McpRootsListChangedNotification();
        applyMeta(notification, null);
        transport.executeOperationWithoutResponse(notification);
    }

    @Override
    public List<McpResourceTemplate> listResourceTemplates() {
        return listResourceTemplates(null);
    }

    @Override
    public List<McpResourceTemplate> listResourceTemplates(InvocationContext invocationContext) {
        assertNotClosed();
        return retrieveWithPossibleCaching(
                cacheResourceList,
                this::obtainResourceTemplateList,
                resourceTemplateListUpdateInProgress,
                () -> resourceTemplateRefs.get(),
                invocationContext);
    }

    /**
     * Retrieves a value from the server (in this case, a list of tools/resources/prompts) taking
     * a cache into account, if configured to use one. If the cache was invalidated and an update is needed,
     * it launches a CompletableFuture that represents a running update so that we avoid
     * updating multiple times concurrently. If an update is already running, this method
     * will, instead of starting a new update, join on the existing update and return its result when available.
     */
    private <T> T retrieveWithPossibleCaching(
            boolean useCache,
            Function<InvocationContext, T> retriever,
            AtomicReference<CompletableFuture<T>> updateInProgressReference,
            Supplier<T> cachedReferenceSupplier,
            InvocationContext invocationContext) {
        if (useCache) {
            T cachedValue = cachedReferenceSupplier.get();
            if (cachedValue != null) {
                // if there is a value in the cache, just return it
                return cachedValue;
            } else {
                // we need to fetch a new value from the server
                CompletableFuture<T> newUpdate = new CompletableFuture<>();
                CompletableFuture<T> updateInProgress = updateInProgressReference.compareAndExchange(null, newUpdate);
                if (updateInProgress == null) {
                    // if no update is in progress, start one and retrieve a fresh value
                    try {
                        T result = retriever.apply(invocationContext);
                        newUpdate.complete(result);
                        return result;
                    } catch (RuntimeException e) {
                        newUpdate.completeExceptionally(e);
                        throw e;
                    } finally {
                        updateInProgressReference.set(null);
                    }
                } else {
                    // if an update is already in progress, wait for it to finish and return its result
                    return updateInProgress.join();
                }
            }
        } else {
            // if not using cache, always fetch a fresh value
            return retriever.apply(invocationContext);
        }
    }

    private List<ToolSpecification> obtainToolList(InvocationContext invocationContext) {
        List<ToolSpecification> list = fetchPaginatedList(
                (id, cursor) -> new McpListToolsRequest(id, cursor),
                toolExecutionTimeout,
                invocationContext,
                result -> ToolSpecificationHelper.toolSpecificationListFromMcpResponse(
                        (ArrayNode) result.get("result").get("tools")));
        toolListRefs.set(list);
        return list;
    }

    private List<McpResource> obtainResourceList(InvocationContext invocationContext) {
        List<McpResource> list = fetchPaginatedList(
                (id, cursor) -> new McpListResourcesRequest(id, cursor),
                resourcesTimeout,
                invocationContext,
                ResourcesHelper::parseResourceRefs);
        resourceRefs.set(list);
        return list;
    }

    private List<McpResourceTemplate> obtainResourceTemplateList(InvocationContext invocationContext) {
        List<McpResourceTemplate> list = fetchPaginatedList(
                (id, cursor) -> new McpListResourceTemplatesRequest(id, cursor),
                resourcesTimeout,
                invocationContext,
                ResourcesHelper::parseResourceTemplateRefs);
        resourceTemplateRefs.set(list);
        return list;
    }

    private List<McpPrompt> obtainPromptList(InvocationContext invocationContext) {
        List<McpPrompt> list = fetchPaginatedList(
                (id, cursor) -> new McpListPromptsRequest(id, cursor),
                promptsTimeout,
                invocationContext,
                PromptsHelper::parsePromptRefs);
        promptRefs.set(list);
        return list;
    }

    private void startAutoHealthCheck() {
        if (Boolean.FALSE.equals(autoHealthCheck)) {
            return;
        }
        Runnable healthCheckTask = () -> {
            try {
                checkHealth();
            } catch (Exception e) {
                log.warn("MCP server health check (client key: " + key + ") failed. Attempting to reconnect...", e);
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

    private <T> List<T> fetchPaginatedList(
            BiFunction<Long, String, McpClientRequest> requestFactory,
            Duration timeout,
            InvocationContext invocationContext,
            Function<JsonNode, List<T>> resultParser) {
        long timeoutMillis = timeout.toMillis() == 0 ? Integer.MAX_VALUE : timeout.toMillis();
        List<T> allItems = new ArrayList<>();
        String cursor = null;
        do {
            McpClientRequest operation = requestFactory.apply(idGenerator.getAndIncrement(), cursor);
            McpCallContext context = new McpCallContext(invocationContext, operation);
            applyMeta(operation, context);
            JsonNode result;
            try {
                CompletableFuture<JsonNode> resultFuture = transport.executeOperationWithResponse(context);
                result = resultFuture.get(timeoutMillis, TimeUnit.MILLISECONDS);
            } catch (ExecutionException | InterruptedException | TimeoutException e) {
                throw new RuntimeException(e);
            } finally {
                pendingOperations.remove(operation.getId());
            }
            allItems.addAll(resultParser.apply(result));
            cursor = getNextCursor(result);
        } while (cursor != null);
        return allItems;
    }

    private static String getNextCursor(JsonNode response) {
        JsonNode resultNode = response.get("result");
        if (resultNode != null && resultNode.has("nextCursor")) {
            String nextCursor = resultNode.get("nextCursor").asText();
            if (!nextCursor.isEmpty()) {
                return nextCursor;
            }
        }
        return null;
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

    private void applyMeta(McpClientMessage message, McpCallContext context) {
        if (metaSupplier == null) {
            return;
        }
        Map<String, Object> meta = metaSupplier.apply(context);
        if (meta == null || meta.isEmpty()) {
            return;
        }
        if (message instanceof McpClientRequest request) {
            if (request.getParams() == null) {
                request.setParams(new McpClientParams());
            }
            request.getParams().setMeta(meta);
        } else if (message instanceof McpClientNotification notification) {
            if (notification.getParams() == null) {
                notification.setParams(new McpClientParams());
            }
            notification.getParams().setMeta(meta);
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
        private Boolean cacheResourceList;
        private Boolean cachePromptList;
        private McpClientListener listener;
        private McpProgressHandler progressHandler;
        private McpMetaSupplier metaSupplier;

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

        /**
         * If set to true, the client will cache the resource and resource
         * template lists obtained from the server until it's notified by
         * the server that the resources have changed. If set to false,
         * there is no caching and the client will always fetch the
         * resource list from the server.
         * The default is true.
         */
        public Builder cacheResourceList(boolean cacheResourceList) {
            this.cacheResourceList = cacheResourceList;
            return this;
        }

        /**
         * If set to true, the client will cache the prompt list obtained
         * from the server until it's notified by the server that the
         * prompts have changed. If set to false, there is no caching
         * and the client will always fetch the prompt list from the server.
         * The default is true.
         */
        public Builder cachePromptList(boolean cachePromptList) {
            this.cachePromptList = cachePromptList;
            return this;
        }

        /**
         * Sets a listener to receive MCP client events.
         * A listener is notified before and after each call to the MCP server.
         * Currently, this applies to tool calls, resource retrievals, and prompt retrievals.
         */
        public Builder listener(McpClientListener listener) {
            this.listener = listener;
            return this;
        }

        /**
         * Sets the progress handler for the client. When set, the client will include
         * a progress token in tool execution requests, and progress notifications
         * received from the server will be forwarded to this handler.
         */
        public Builder progressHandler(McpProgressHandler progressHandler) {
            this.progressHandler = progressHandler;
            return this;
        }

        /**
         * Sets a supplier of {@code _meta} fields for MCP client requests and notifications.
         * The supplier is called before every request or notification sent to the server.
         * Unlike HTTP headers, this applies to all transports.
         */
        public Builder metaSupplier(McpMetaSupplier metaSupplier) {
            this.metaSupplier = metaSupplier;
            return this;
        }

        public DefaultMcpClient build() {
            return new DefaultMcpClient(this);
        }
    }
}
