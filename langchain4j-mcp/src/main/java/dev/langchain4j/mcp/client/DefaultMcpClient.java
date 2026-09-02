package dev.langchain4j.mcp.client;

import static dev.langchain4j.internal.Exceptions.unwrapCompletionException;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import com.fasterxml.jackson.databind.JsonNode;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.exception.ToolArgumentsException;
import dev.langchain4j.exception.ToolExecutionException;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.mcp.client.logging.DefaultMcpLogMessageHandler;
import dev.langchain4j.mcp.client.logging.McpLogMessageHandler;
import dev.langchain4j.mcp.client.progress.McpProgressHandler;
import dev.langchain4j.mcp.client.transport.McpHeaderEncoding;
import dev.langchain4j.mcp.client.transport.McpJson;
import dev.langchain4j.mcp.client.transport.McpOperationHandler;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.protocol.McpCallToolParams;
import dev.langchain4j.mcp.protocol.McpCallToolRequest;
import dev.langchain4j.mcp.protocol.McpCancellationNotification;
import dev.langchain4j.mcp.protocol.McpClientMessage;
import dev.langchain4j.mcp.protocol.McpClientNotification;
import dev.langchain4j.mcp.protocol.McpClientParams;
import dev.langchain4j.mcp.protocol.McpClientRequest;
import dev.langchain4j.mcp.protocol.McpErrorResponse;
import dev.langchain4j.mcp.protocol.McpGetPromptParams;
import dev.langchain4j.mcp.protocol.McpGetPromptRequest;
import dev.langchain4j.mcp.protocol.McpImplementation;
import dev.langchain4j.mcp.protocol.McpInitializeParams;
import dev.langchain4j.mcp.protocol.McpInitializeRequest;
import dev.langchain4j.mcp.protocol.McpInitializeResult;
import dev.langchain4j.mcp.protocol.McpListPromptsRequest;
import dev.langchain4j.mcp.protocol.McpListResourceTemplatesRequest;
import dev.langchain4j.mcp.protocol.McpListResourcesRequest;
import dev.langchain4j.mcp.protocol.McpListToolsRequest;
import dev.langchain4j.mcp.protocol.McpListToolsResult;
import dev.langchain4j.mcp.protocol.McpPaginatedResult;
import dev.langchain4j.mcp.protocol.McpPingRequest;
import dev.langchain4j.mcp.protocol.McpReadResourceParams;
import dev.langchain4j.mcp.protocol.McpReadResourceRequest;
import dev.langchain4j.mcp.protocol.McpRootsListChangedNotification;
import dev.langchain4j.mcp.protocol.McpServerDiscoverParams;
import dev.langchain4j.mcp.protocol.McpServerDiscoverRequest;
import dev.langchain4j.mcp.protocol.McpServerDiscoverResponse;
import dev.langchain4j.mcp.protocol.McpSubscribeResourceRequest;
import dev.langchain4j.mcp.protocol.McpSubscriptionsListenParams;
import dev.langchain4j.mcp.protocol.McpSubscriptionsListenRequest;
import dev.langchain4j.mcp.protocol.McpUnsubscribeResourceRequest;
import dev.langchain4j.service.tool.ToolExecutionResult;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CancellationException;
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
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultMcpClient implements McpClient {

    private static final Logger log = LoggerFactory.getLogger(DefaultMcpClient.class);
    private static final int DEFAULT_MULTI_ROUND_TRIP_MAX_RETRIES = 3;

    /**
     * The first MCP protocol version that conveys version, identity and capabilities as
     * per-request metadata instead of an {@code initialize} handshake.
     */
    static final String PROTOCOL_VERSION_MODERN = "2026-07-28";

    static final String PROTOCOL_VERSION_LEGACY = "2025-11-25";
    static final String PROTOCOL_VERSION_LEGACY_OLD = "2024-11-05";

    private final AtomicLong idGenerator = new AtomicLong(0);
    private final McpTransport transport;
    private final String key;
    private final String clientName;
    private final String clientVersion;
    private final String protocolVersion;
    private volatile boolean modernProtocol;
    private final Duration initializationTimeout;
    private final Duration protocolDetectionTimeout;
    private final Duration toolExecutionTimeout;
    private final Duration resourcesTimeout;
    private final Duration promptsTimeout;
    private final Duration pingTimeout;
    private static final String MCP_SERVER_INFO_META_KEY = "io.modelcontextprotocol/serverInfo";

    private final String toolExecutionTimeoutErrorMessage;
    private final Map<Long, CompletableFuture<String>> pendingOperations = new ConcurrentHashMap<>();
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
    private final BiConsumer<McpClient, String> onResourceUpdated;
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
    private final List<McpClientListener> listeners;
    private final McpMetaSupplier metaSupplier;
    private final McpToolResultConverter toolResultConverter;

    private volatile @Nullable McpInitializeResult initializeResult;
    private final int multiRoundTripMaxRetries;
    private final boolean subscribeToToolListChanges;
    private final boolean subscribeToPromptListChanges;
    private final boolean subscribeToResourceListChanges;
    private final Map<Long, CompletableFuture<JsonNode>> activeSubscriptions = new ConcurrentHashMap<>();
    private final Map<Long, CompletableFuture<Map<String, Object>>> pendingSubscriptionAcks = new ConcurrentHashMap<>();
    private volatile CompletableFuture<JsonNode> listChangeSubscriptionFuture;

    public DefaultMcpClient(Builder builder) {
        try {
            transport = ensureNotNull(builder.transport, "transport");
            key = getOrDefault(builder.key, () -> UUID.randomUUID().toString());
            clientName = getOrDefault(builder.clientName, "langchain4j");
            clientVersion = getOrDefault(builder.clientVersion, "1.0");
            protocolVersion = builder.protocolVersion;
            initializationTimeout = getOrDefault(builder.initializationTimeout, Duration.ofSeconds(30));
            // Defaults to the initialization timeout: a stdio server is usually a subprocess that
            // has to boot before it can answer anything, and cutting the detection request short
            // would misdetect it as a legacy server.
            protocolDetectionTimeout = getOrDefault(builder.protocolDetectionTimeout, initializationTimeout);
            toolExecutionTimeout = getOrDefault(builder.toolExecutionTimeout, Duration.ofSeconds(60));
            resourcesTimeout = getOrDefault(builder.resourcesTimeout, Duration.ofSeconds(60));
            promptsTimeout = getOrDefault(builder.promptsTimeout, Duration.ofSeconds(60));
            logHandler = getOrDefault(builder.logHandler, new DefaultMcpLogMessageHandler());
            progressHandler = builder.progressHandler;
            pingTimeout = getOrDefault(builder.pingTimeout, Duration.ofSeconds(10));
            reconnectInterval = getOrDefault(builder.reconnectInterval, Duration.ofSeconds(5));
            autoHealthCheck = getOrDefault(builder.autoHealthCheck, Boolean.TRUE);
            autoHealthCheckInterval = getOrDefault(builder.autoHealthCheckInterval, Duration.ofSeconds(30));
            listeners = List.copyOf(builder.listeners);
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
            onResourceUpdated = builder.onResourceUpdated;
            if (builder.toolResultConverter != null && builder.toolResultExtractor != null) {
                throw new IllegalArgumentException(
                        "Set either toolResultConverter or the deprecated " + "toolResultExtractor, not both");
            }
            toolResultConverter = builder.toolResultExtractor != null
                    ? new LegacyToolResultConverterAdapter(builder.toolResultExtractor)
                    : getOrDefault(builder.toolResultConverter, DefaultMcpToolResultConverter::new);
            multiRoundTripMaxRetries =
                    getOrDefault(builder.multiRoundTripMaxRetries, DEFAULT_MULTI_ROUND_TRIP_MAX_RETRIES);
            subscribeToToolListChanges = getOrDefault(builder.subscribeToToolListChanges, Boolean.TRUE);
            subscribeToPromptListChanges = getOrDefault(builder.subscribeToPromptListChanges, Boolean.TRUE);
            subscribeToResourceListChanges = getOrDefault(builder.subscribeToResourceListChanges, Boolean.TRUE);
            messageHandler = new McpOperationHandler(
                    pendingOperations,
                    mcpRoots::get,
                    transport,
                    message -> {
                        logHandler.handleLogMessage(message);
                        notifyListeners(l -> l.onNotificationMessage(message));
                    },
                    () -> {
                        toolListRefs.set(null);
                        notifyListeners(l -> l.onNotificationToolsListChanged());
                    },
                    () -> {
                        resourceRefs.set(null);
                        resourceTemplateRefs.set(null);
                        notifyListeners(l -> l.onNotificationResourcesListChanged());
                    },
                    () -> {
                        promptRefs.set(null);
                        notifyListeners(l -> l.onNotificationPromptsListChanged());
                    },
                    uri -> {
                        if (onResourceUpdated != null) {
                            onResourceUpdated.accept(this, uri);
                        }
                        notifyListeners(l -> l.onNotificationResourceUpdated(uri));
                    },
                    notification -> {
                        if (progressHandler != null) {
                            progressHandler.onProgress(notification);
                        }
                        notifyListeners(l -> l.onNotificationProgress(notification));
                    },
                    () -> notifyListeners(l -> l.onServerPing()),
                    () -> notifyListeners(l -> l.onServerRootsList()),
                    (requestId, reason) -> notifyListeners(l -> l.onNotificationCancelled(requestId, reason)),
                    (subscriptionId, message) -> {
                        CompletableFuture<Map<String, Object>> ackFuture =
                                pendingSubscriptionAcks.remove(subscriptionId);
                        if (ackFuture != null) {
                            ackFuture.complete(message);
                        }
                    });
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
        String resolvedVersion = protocolVersion;

        if (resolvedVersion == null || resolvedVersion.isEmpty()) {
            autoDetect(PROTOCOL_VERSION_MODERN, PROTOCOL_VERSION_LEGACY);
        } else if (isModernVersion(resolvedVersion)) {
            initializeModern(resolvedVersion, false);
        } else if (PROTOCOL_VERSION_LEGACY.equals(resolvedVersion)
                || PROTOCOL_VERSION_LEGACY_OLD.equals(resolvedVersion)) {
            initializeLegacy(resolvedVersion);
        } else {
            autoDetect(resolvedVersion, resolvedVersion);
        }
    }

    private void autoDetect(String modernVersion, String legacyFallbackVersion) {
        try {
            initializeModern(modernVersion, true);
        } catch (McpException e) {
            if (isModernErrorCode(e.errorCode())) {
                if (e.errorCode() == -32022) {
                    retryWithSupportedVersion(e);
                    return;
                }
                throw e;
            }
            log.debug("Server does not support modern protocol, falling back to legacy initialize");
            initializeLegacy(legacyFallbackVersion);
        } catch (Exception e) {
            if (e.getCause() instanceof TimeoutException) {
                // Unlike an error answer, silence does not prove the server is a legacy one, so say
                // so: a server that was merely slow is about to be used with the wrong protocol.
                log.warn(
                        "The server did not answer the {} protocol detection request within {}, so it is treated as a {} server. If it does support {}, raise protocolDetectionTimeout or set protocolVersion explicitly.",
                        modernVersion,
                        protocolDetectionTimeout,
                        legacyFallbackVersion,
                        modernVersion);
            } else {
                log.debug("Modern initialization (server/discover) failed, falling back to legacy initialize");
            }
            try {
                initializeLegacy(legacyFallbackVersion);
            } catch (RuntimeException legacyFailure) {
                throw new RuntimeException(
                        ("%s. The server answered neither the %s protocol detection request nor the %s initialization"
                                        + " that followed it. If the server does not tolerate being sent a method it"
                                        + " does not know, skip detection by setting the protocol version explicitly,"
                                        + " for example .protocolVersion(\"%s\").")
                                .formatted(
                                        legacyFailure.getMessage(),
                                        modernVersion,
                                        legacyFallbackVersion,
                                        legacyFallbackVersion),
                        legacyFailure);
            }
        }
    }

    private static boolean isModernErrorCode(int code) {
        return code == -32022 || code == -32021 || code == -32020;
    }

    private boolean shouldSendCancellationNotification() {
        return transport.requiresCancellationNotification() || !modernProtocol;
    }

    private void retryWithSupportedVersion(McpException e) {
        Map<String, Object> data = e.errorDataAsMap();
        if (data != null && data.get("supported") instanceof List) {
            List<?> supported = (List<?>) data.get("supported");
            // Prefer modern versions, fall back to legacy
            String bestModern = null;
            String bestLegacy = null;
            for (Object v : supported) {
                String version = String.valueOf(v);
                if (isModernVersion(version)) {
                    if (bestModern == null || version.compareTo(bestModern) > 0) {
                        bestModern = version;
                    }
                } else {
                    if (bestLegacy == null || version.compareTo(bestLegacy) > 0) {
                        bestLegacy = version;
                    }
                }
            }
            if (bestModern != null) {
                initializeModern(bestModern, false);
                return;
            }
            if (bestLegacy != null) {
                initializeLegacy(bestLegacy);
                return;
            }
        }
        throw new RuntimeException("Server does not support any compatible protocol version", e);
    }

    private void initializeLegacy(String version) {
        transport.setModernProtocol(false);
        long operationId = idGenerator.getAndIncrement();
        McpInitializeRequest request = new McpInitializeRequest(operationId);
        McpInitializeParams params = createInitializeParams(version);
        request.setParams(params);
        McpCallContext context = new McpCallContext(null, request);
        notifyListeners(l -> l.beforeInitialize(context));
        applyMeta(request, context);
        try {
            JsonNode capabilities =
                    initializeViaTransport(request).get(initializationTimeout.toMillis(), TimeUnit.MILLISECONDS);
            if (capabilities.get("result") != null) {
                log.debug("MCP server capabilities: {}", capabilities.get("result"));
            }
            initializeResult = toInitializeResult(capabilities);
            modernProtocol = false;
            notifyListeners(l -> l.afterInitialize(context));
        } catch (Exception e) {
            notifyListeners(l -> l.onInitializeError(context, e));
            throw new RuntimeException(e);
        } finally {
            pendingOperations.remove(operationId);
        }
    }

    private McpInitializeParams createInitializeParams(String version) {
        McpInitializeParams params = new McpInitializeParams();
        params.setProtocolVersion(version);

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

    private static McpInitializeResult toInitializeResult(JsonNode response) {
        return McpJson.deserialize(response, McpInitializeResult.class);
    }

    /**
     * The discover result carries the same shape as an initialize result, except that the server
     * info sits under a reserved '_meta' key and no protocol version is reported.
     */
    private static McpInitializeResult toInitializeResultFromDiscover(
            JsonNode response, McpServerDiscoverResponse.Result discoveredResult) {
        McpInitializeResult discovered = McpJson.deserialize(response, McpInitializeResult.class);
        McpInitializeResult.Result result = discovered.getResult();
        if (result == null) {
            return discovered;
        }

        Map<String, Object> meta = discoveredResult == null ? null : discoveredResult.getMeta();
        Object serverInfoValue = meta == null ? null : meta.get(MCP_SERVER_INFO_META_KEY);
        McpImplementation serverInfo =
                serverInfoValue == null ? null : McpJson.convert(serverInfoValue, McpImplementation.class);

        return new McpInitializeResult(
                discovered.getId(),
                new McpInitializeResult.Result(null, result.getCapabilities(), serverInfo, result.getInstructions()));
    }

    private void initializeModern(String versionToAdvertise, boolean isProbe) {
        // Set modern mode on HTTP transport BEFORE sending server/discover,
        // so the required HTTP headers (MCP-Protocol-Version, Mcp-Method) are included
        transport.setModernProtocol(true);
        transport.setProtocolVersion(versionToAdvertise);
        long operationId = idGenerator.getAndIncrement();
        McpServerDiscoverRequest request = new McpServerDiscoverRequest(operationId);
        McpServerDiscoverParams params = new McpServerDiscoverParams();
        applyModernMeta(params, versionToAdvertise);
        request.setParams(params);
        McpCallContext context = new McpCallContext(null, request);
        if (!isProbe) {
            notifyListeners(l -> l.beforeServerDiscover(context));
        }
        applyMeta(request, context);
        CompletableFuture<JsonNode> resultFuture = null;
        try {
            resultFuture = executeViaTransport(context);
            Duration timeout = isProbe ? protocolDetectionTimeout : initializationTimeout;
            JsonNode response = resultFuture.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            McpErrorResponse.Error error =
                    McpJson.deserialize(response, McpErrorResponse.class).getError();
            if (error != null) {
                throw McpException.withErrorData(
                        error.getCode(),
                        error.getMessage(),
                        error.getData() == null ? null : McpJson.serialize(error.getData()));
            }
            log.debug("MCP server discover result: {}", response.get("result"));
            McpServerDiscoverResponse.Result discovered = McpJson.deserialize(response, McpServerDiscoverResponse.class)
                    .getResult();
            initializeResult = toInitializeResultFromDiscover(response, discovered);
            modernProtocol = true;
            McpDiscoverResult discoverResult = toDiscoverResult(discovered);
            notifyListeners(l -> l.afterServerDiscover(context, discoverResult));
            // Auto-start list-change subscriptions if enabled
            if (subscribeToToolListChanges || subscribeToPromptListChanges || subscribeToResourceListChanges) {
                startListChangeSubscriptions();
            }
        } catch (McpException e) {
            if (!isProbe) {
                notifyListeners(l -> l.onServerDiscoverError(context, e));
            }
            throw e;
        } catch (Exception e) {
            if (!isProbe) {
                notifyListeners(l -> l.onServerDiscoverError(context, e));
            }
            if (resultFuture != null) {
                resultFuture.cancel(true);
            }
            throw new RuntimeException(e);
        } finally {
            pendingOperations.remove(operationId);
        }
    }

    private static McpDiscoverResult toDiscoverResult(McpServerDiscoverResponse.Result result) {
        if (result == null) {
            return new McpDiscoverResult(List.of(), Map.of(), null, null, null);
        }

        McpServerInfo serverInfo = null;
        Object serverInfoValue =
                result.getMeta() == null ? null : result.getMeta().get(MCP_SERVER_INFO_META_KEY);
        if (serverInfoValue != null) {
            McpImplementation implementation = McpJson.convert(serverInfoValue, McpImplementation.class);
            serverInfo =
                    new McpServerInfo(implementation.getName(), implementation.getVersion(), implementation.getTitle());
        }

        return new McpDiscoverResult(
                result.getSupportedVersions() == null ? List.of() : result.getSupportedVersions(),
                result.getCapabilities() == null ? Map.of() : result.getCapabilities(),
                serverInfo,
                result.getInstructions(),
                result.getResultType());
    }

    private void applyModernMeta(McpClientParams params, String versionToAdvertise) {
        Map<String, Object> meta = params.getMeta();
        if (meta == null) {
            meta = new LinkedHashMap<>();
        } else {
            meta = new LinkedHashMap<>(meta);
        }
        meta.put("io.modelcontextprotocol/protocolVersion", versionToAdvertise);
        Map<String, Object> clientInfoMap = new LinkedHashMap<>();
        clientInfoMap.put("name", clientName);
        clientInfoMap.put("version", clientVersion);
        meta.put("io.modelcontextprotocol/clientInfo", clientInfoMap);
        meta.put("io.modelcontextprotocol/clientCapabilities", Map.of());
        params.setMeta(meta);
    }

    private static boolean isModernVersion(String version) {
        return version != null && version.compareTo(PROTOCOL_VERSION_MODERN) >= 0;
    }

    public boolean isModernProtocol() {
        return modernProtocol;
    }

    private void startListChangeSubscriptions() {
        McpSubscriptionsListenParams.Notifications notifications = new McpSubscriptionsListenParams.Notifications();
        if (subscribeToToolListChanges) {
            notifications.setToolsListChanged(true);
        }
        if (subscribeToPromptListChanges) {
            notifications.setPromptsListChanged(true);
        }
        if (subscribeToResourceListChanges) {
            notifications.setResourcesListChanged(true);
        }
        long operationId = idGenerator.getAndIncrement();
        McpSubscriptionsListenRequest request = new McpSubscriptionsListenRequest(operationId);
        McpSubscriptionsListenParams params = new McpSubscriptionsListenParams();
        params.setNotifications(notifications);
        request.setParams(params);
        McpCallContext context = new McpCallContext(null, request);
        applyMeta(request, context);
        CompletableFuture<JsonNode> future = executeViaTransport(context);
        listChangeSubscriptionFuture = future;
        future.whenComplete((result, error) -> {
            pendingOperations.remove(operationId);
            if (error != null && !closed) {
                log.warn("List-change subscription failed", error);
            }
        });
    }

    private static McpServerDiscoverResponse.Result multiRoundTripResult(JsonNode response) {
        return McpJson.deserialize(response, McpServerDiscoverResponse.class).getResult();
    }

    private static String resultTypeOf(McpServerDiscoverResponse.Result result) {
        if (result == null || result.getResultType() == null) {
            return "complete";
        }
        return result.getResultType();
    }

    private static String getResultType(JsonNode response) {
        return resultTypeOf(multiRoundTripResult(response));
    }

    /**
     * Mirrors JsonNode.isEmpty(), for which only a non-empty array or object is non-empty: an
     * absent value, an empty array, an empty object and any scalar all count as empty.
     */
    private static boolean isNotEmpty(Object value) {
        if (value instanceof List) {
            return !((List<?>) value).isEmpty();
        }
        if (value instanceof Map) {
            return !((Map<?, ?>) value).isEmpty();
        }
        return false;
    }

    private JsonNode handleMultiRoundTrip(
            JsonNode initialResult,
            long timeoutMillis,
            InvocationContext invocationContext,
            BiFunction<Long, Object, McpClientRequest> retryRequestFactory,
            String operationName)
            throws ExecutionException, InterruptedException, TimeoutException {
        if (!modernProtocol) {
            return initialResult;
        }
        JsonNode result = initialResult;
        int retryCount = 0;
        McpServerDiscoverResponse.Result parsed = multiRoundTripResult(result);
        while ("input_required".equals(resultTypeOf(parsed))) {
            if (retryCount >= multiRoundTripMaxRetries) {
                throw new RuntimeException("Multi round-trip retry limit exceeded for " + operationName);
            }
            if (isNotEmpty(parsed.getInputRequests())) {
                throw new RuntimeException("Server sent inputRequests that the client cannot handle");
            }
            Object requestState = parsed.getRequestState();
            if (requestState == null) {
                throw new RuntimeException("Server sent input_required without requestState or inputRequests");
            }
            long retryOperationId = idGenerator.getAndIncrement();
            McpClientRequest retryOperation = retryRequestFactory.apply(retryOperationId, requestState);
            McpCallContext retryContext = new McpCallContext(invocationContext, retryOperation);
            applyMeta(retryOperation, retryContext);
            CompletableFuture<JsonNode> resultFuture = executeViaTransport(retryContext);
            try {
                result = resultFuture.get(timeoutMillis, TimeUnit.MILLISECONDS);
            } catch (TimeoutException timeout) {
                throw new McpOperationTimeoutException(retryOperationId, resultFuture, timeout);
            }
            pendingOperations.remove(retryOperationId);
            parsed = multiRoundTripResult(result);
            retryCount++;
        }
        String resultType = resultTypeOf(parsed);
        if (!"complete".equals(resultType)) {
            throw new RuntimeException("Unexpected resultType for " + operationName + ": " + resultType);
        }
        return result;
    }

    private void cancelTimedOutOperation(
            TimeoutException timeout, long operationId, CompletableFuture<JsonNode> resultFuture) {
        long timedOutOperationId = operationId;
        CompletableFuture<JsonNode> timedOutResultFuture = resultFuture;
        if (timeout instanceof McpOperationTimeoutException operationTimeout) {
            timedOutOperationId = operationTimeout.operationId;
            timedOutResultFuture = operationTimeout.resultFuture;
        }
        if (timedOutResultFuture != null) {
            timedOutResultFuture.cancel(true);
        }
        pendingOperations.remove(timedOutOperationId);
        if (shouldSendCancellationNotification()) {
            McpCancellationNotification cancellation = new McpCancellationNotification(timedOutOperationId, "Timeout");
            applyMeta(cancellation, null);
            transport.sendMessage(cancellation);
        }
    }

    private static class McpOperationTimeoutException extends TimeoutException {

        private final long operationId;
        private final CompletableFuture<JsonNode> resultFuture;

        private McpOperationTimeoutException(
                long operationId, CompletableFuture<JsonNode> resultFuture, TimeoutException cause) {
            super(cause.getMessage());
            this.operationId = operationId;
            this.resultFuture = resultFuture;
            initCause(cause);
        }
    }

    @Override
    public String key() {
        return key;
    }

    @Override
    public @Nullable String instructions() {
        McpInitializeResult currentInitializeResult = initializeResult;
        if (currentInitializeResult == null || currentInitializeResult.getResult() == null) {
            return null;
        }
        return currentInitializeResult.getResult().getInstructions();
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
        Map<String, Object> arguments = parseToolArguments(executionRequest);
        long operationId = idGenerator.getAndIncrement();
        String progressToken = progressHandler != null ? String.valueOf(operationId) : null;
        McpCallToolRequest operation =
                new McpCallToolRequest(operationId, executionRequest.name(), arguments, progressToken);
        long timeoutMillis = toolExecutionTimeout.toMillis() == 0 ? Integer.MAX_VALUE : toolExecutionTimeout.toMillis();
        CompletableFuture<JsonNode> resultFuture = null;
        JsonNode result = null;
        Map<String, String> paramHeaders =
                modernProtocol ? buildMcpParamHeaders(executionRequest.name(), arguments) : null;
        McpCallContext context = new McpCallContext(invocationContext, operation, paramHeaders);
        try {
            notifyListeners(l -> l.beforeExecuteTool(context));
            applyMeta(operation, context);
            resultFuture = executeViaTransport(context);
            result = resultFuture.get(timeoutMillis, TimeUnit.MILLISECONDS);

            final Map<String, Object> finalArguments = arguments;
            result = handleMultiRoundTrip(
                    result,
                    timeoutMillis,
                    invocationContext,
                    (retryId, requestState) -> {
                        McpCallToolRequest retryOp =
                                new McpCallToolRequest(retryId, executionRequest.name(), finalArguments, progressToken);
                        ((McpCallToolParams) retryOp.getParams()).setRequestState(requestState);
                        return retryOp;
                    },
                    "tools/call");
        } catch (TimeoutException timeout) {
            return handleToolTimeout(context, operationId, resultFuture, timeout);
        } catch (ExecutionException e) {
            notifyListeners(l -> l.onExecuteToolError(context, e));
            throw new ToolExecutionException(e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } finally {
            pendingOperations.remove(operationId);
        }
        return extractResultAndNotifyListeners(context, result);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Non-blocking: the MCP transport is asynchronous, so this composes the transport's response
     * future — no thread is held while the tool executes on the server. Timeout (including the cancellation
     * notification sent to the server), error mapping and listener notifications mirror
     * {@link #executeTool(ToolExecutionRequest, InvocationContext)}.
     */
    @Override
    public CompletableFuture<ToolExecutionResult> executeToolAsync(
            ToolExecutionRequest executionRequest, InvocationContext invocationContext) {
        assertNotClosed();
        Map<String, Object> arguments;
        try {
            arguments = parseToolArguments(executionRequest);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
        long operationId = idGenerator.getAndIncrement();
        String progressToken = progressHandler != null ? String.valueOf(operationId) : null;
        McpCallToolRequest operation =
                new McpCallToolRequest(operationId, executionRequest.name(), arguments, progressToken);
        long timeoutMillis = toolExecutionTimeout.toMillis() == 0 ? Integer.MAX_VALUE : toolExecutionTimeout.toMillis();
        McpCallContext context = new McpCallContext(invocationContext, operation);

        CompletableFuture<JsonNode> resultFuture;
        try {
            notifyListeners(l -> l.beforeExecuteTool(context));
            applyMeta(operation, context);
            resultFuture = transport.executeOperationWithResponse(context);
        } catch (Exception e) {
            pendingOperations.remove(operationId);
            return CompletableFuture.failedFuture(e);
        }

        return resultFuture.orTimeout(timeoutMillis, TimeUnit.MILLISECONDS).handle((result, error) -> {
            pendingOperations.remove(operationId);
            if (error != null) {
                Throwable cause = unwrapCompletionException(error);
                if (cause instanceof TimeoutException timeout) {
                    return handleToolTimeout(context, operationId, resultFuture, timeout);
                }
                notifyListeners(l -> l.onExecuteToolError(context, cause));
                throw new ToolExecutionException(cause);
            }
            return extractResultAndNotifyListeners(context, result);
        });
    }

    private static Map<String, Object> parseToolArguments(ToolExecutionRequest executionRequest) {
        try {
            String args = executionRequest.arguments();
            if (isNullOrBlank(args)) {
                args = "{}";
            }
            return McpJson.toMap(args);
        } catch (IllegalArgumentException e) {
            throw new ToolArgumentsException(e.getCause() != null ? e.getCause() : e);
        }
    }

    private ToolExecutionResult handleToolTimeout(
            McpCallContext context,
            long operationId,
            CompletableFuture<JsonNode> resultFuture,
            TimeoutException timeout) {
        notifyListeners(l -> l.onExecuteToolError(context, timeout));
        cancelTimedOutOperation(timeout, operationId, resultFuture);
        // built on demand, not once at construction: a custom converter must not be invoked
        // for a tool call that never happened
        return toolResultConverter.convert(
                List.of(Map.of("type", "text", "text", toolExecutionTimeoutErrorMessage)), false);
    }

    private ToolExecutionResult extractResultAndNotifyListeners(McpCallContext context, JsonNode finalResult) {
        try {
            ToolExecutionResult toolResult = ToolExecutionHelper.extractResult(finalResult, false, toolResultConverter);
            notifyListeners(l -> l.afterExecuteTool(context, toolResult, McpJsonConversions.toMap(finalResult)));
            return toolResult;
        } catch (ToolExecutionException e) {
            if (e.errorCode() != null) {
                // protocol error
                notifyListeners(l -> l.onExecuteToolError(context, e));
            } else {
                // application-level error (called "Tool Execution Error" in MCP spec)
                // -> we notify the listener with afterExecuteTool
                notifyListeners(l -> l.afterExecuteTool(
                        context,
                        ToolExecutionHelper.extractResult(finalResult, true, toolResultConverter),
                        McpJsonConversions.toMap(finalResult)));
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
                cacheResourceList,
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
        notifyListeners(l -> l.beforeResourceGet(context));
        applyMeta(operation, context);
        try {
            resultFuture = executeViaTransport(context);
            result = resultFuture.get(timeoutMillis, TimeUnit.MILLISECONDS);

            result = handleMultiRoundTrip(
                    result,
                    timeoutMillis,
                    invocationContext,
                    (retryId, requestState) -> {
                        McpReadResourceRequest retryOp = new McpReadResourceRequest(retryId, uri);
                        ((McpReadResourceParams) retryOp.getParams()).setRequestState(requestState);
                        return retryOp;
                    },
                    "resources/read");
            McpReadResourceResult resourceResult = ResourcesHelper.parseResourceContents(result);
            final JsonNode finalResult = result;
            notifyListeners(l -> l.afterResourceGet(context, resourceResult, McpJsonConversions.toMap(finalResult)));
            return resourceResult;
        } catch (TimeoutException timeout) {
            notifyListeners(l -> l.onResourceGetError(context, timeout));
            cancelTimedOutOperation(timeout, operationId, resultFuture);
            throw new RuntimeException(timeout);
        } catch (ExecutionException e) {
            notifyListeners(l -> l.onResourceGetError(context, e));
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (McpException e) {
            notifyListeners(l -> l.onResourceGetError(context, e));
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
        notifyListeners(l -> l.beforePromptGet(context));
        applyMeta(operation, context);
        try {
            resultFuture = executeViaTransport(context);
            result = resultFuture.get(timeoutMillis, TimeUnit.MILLISECONDS);

            final Map<String, Object> finalArguments = arguments == null ? Map.of() : arguments;
            result = handleMultiRoundTrip(
                    result,
                    timeoutMillis,
                    null,
                    (retryId, requestState) -> {
                        McpGetPromptRequest retryOp = new McpGetPromptRequest(retryId, name, finalArguments);
                        ((McpGetPromptParams) retryOp.getParams()).setRequestState(requestState);
                        return retryOp;
                    },
                    "prompts/get");
            McpGetPromptResult promptResult = PromptsHelper.parsePromptContents(result);
            final JsonNode finalResult = result;
            notifyListeners(l -> l.afterPromptGet(context, promptResult, McpJsonConversions.toMap(finalResult)));
            return promptResult;
        } catch (TimeoutException timeout) {
            notifyListeners(l -> l.onPromptGetError(context, timeout));
            cancelTimedOutOperation(timeout, operationId, resultFuture);
            throw new RuntimeException(timeout);
        } catch (ExecutionException e) {
            notifyListeners(l -> l.onPromptGetError(context, e));
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (McpException e) {
            notifyListeners(l -> l.onPromptGetError(context, e));
            throw e;
        } finally {
            pendingOperations.remove(operationId);
        }
    }

    @Override
    public void checkHealth() {
        assertNotClosed();
        transport.checkHealth();
        if (modernProtocol) {
            checkHealthModern();
        } else {
            checkHealthLegacy();
        }
    }

    private void checkHealthModern() {
        long operationId = idGenerator.getAndIncrement();
        McpServerDiscoverRequest request = new McpServerDiscoverRequest(operationId);
        McpServerDiscoverParams params = new McpServerDiscoverParams();
        request.setParams(params);
        McpCallContext context = new McpCallContext(null, request);
        notifyListeners(l -> l.beforePing(context));
        applyMeta(request, context);
        try {
            CompletableFuture<JsonNode> resultFuture = executeViaTransport(context);
            resultFuture.get(pingTimeout.toMillis(), TimeUnit.MILLISECONDS);
            notifyListeners(l -> l.afterPing(context));
        } catch (ExecutionException | InterruptedException | TimeoutException e) {
            RuntimeException re = new RuntimeException(e);
            notifyListeners(l -> l.onPingError(context, re));
            throw re;
        } finally {
            pendingOperations.remove(operationId);
        }
    }

    private void checkHealthLegacy() {
        long operationId = idGenerator.getAndIncrement();
        McpPingRequest ping = new McpPingRequest(operationId);
        McpCallContext context = new McpCallContext(null, ping);
        notifyListeners(l -> l.beforePing(context));
        applyMeta(ping, context);
        try {
            CompletableFuture<JsonNode> resultFuture = executeViaTransport(context);
            resultFuture.get(pingTimeout.toMillis(), TimeUnit.MILLISECONDS);
            notifyListeners(l -> l.afterPing(context));
        } catch (ExecutionException | InterruptedException | TimeoutException e) {
            RuntimeException re = new RuntimeException(e);
            notifyListeners(l -> l.onPingError(context, re));
            throw re;
        } finally {
            pendingOperations.remove(operationId);
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public void setRoots(final List<McpRoot> roots) {
        if (modernProtocol) {
            throw new UnsupportedOperationException("setRoots is not supported with MCP protocol 2026-07-28 or later");
        }
        this.mcpRoots.set(roots);
        McpRootsListChangedNotification notification = new McpRootsListChangedNotification();
        McpCallContext context = new McpCallContext(null, notification);
        applyMeta(notification, context);
        transport.sendMessage(context);
        notifyListeners(l -> l.onRootsListChanged(context));
    }

    @Override
    public void subscribeToResource(String uri) {
        assertNotClosed();
        if (modernProtocol) {
            throw new UnsupportedOperationException(
                    "subscribeToResource is not supported with MCP 2026-07-28. Use subscribeToResources(List) instead.");
        }
        if (onResourceUpdated == null) {
            log.warn(
                    "Subscribing to MCP resource '{}' but no onResourceUpdated callback was registered. The client will"
                            + "not react to resource update notifications in any way.",
                    uri);
        }
        long operationId = idGenerator.getAndIncrement();
        McpSubscribeResourceRequest operation = new McpSubscribeResourceRequest(operationId, uri);
        McpCallContext context = new McpCallContext(null, operation);
        notifyListeners(l -> l.beforeResourceSubscribe(context));
        applyMeta(operation, context);
        long timeoutMillis = resourcesTimeout.toMillis() == 0 ? Integer.MAX_VALUE : resourcesTimeout.toMillis();
        CompletableFuture<JsonNode> resultFuture = null;
        try {
            resultFuture = executeViaTransport(context);
            JsonNode result = resultFuture.get(timeoutMillis, TimeUnit.MILLISECONDS);
            McpErrorHelper.checkForErrors(result);
            notifyListeners(l -> l.afterResourceSubscribe(context));
        } catch (TimeoutException timeout) {
            if (resultFuture != null) {
                resultFuture.cancel(true);
            }
            if (shouldSendCancellationNotification()) {
                McpCancellationNotification cancellation = new McpCancellationNotification(operationId, "Timeout");
                applyMeta(cancellation, null);
                transport.sendMessage(cancellation);
            }
            RuntimeException re = new RuntimeException(timeout);
            notifyListeners(l -> l.onResourceSubscribeError(context, re));
            throw re;
        } catch (ExecutionException e) {
            RuntimeException re = new RuntimeException(e);
            notifyListeners(l -> l.onResourceSubscribeError(context, re));
            throw re;
        } catch (McpException e) {
            notifyListeners(l -> l.onResourceSubscribeError(context, e));
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } finally {
            pendingOperations.remove(operationId);
        }
    }

    @Override
    public void unsubscribeFromResource(String uri) {
        assertNotClosed();
        if (modernProtocol) {
            throw new UnsupportedOperationException(
                    "unsubscribeFromResource is not supported with MCP 2026-07-28. Use unsubscribeFromResources(long) instead.");
        }
        long operationId = idGenerator.getAndIncrement();
        McpUnsubscribeResourceRequest operation = new McpUnsubscribeResourceRequest(operationId, uri);
        McpCallContext context = new McpCallContext(null, operation);
        notifyListeners(l -> l.beforeResourceUnsubscribe(context));
        applyMeta(operation, context);
        long timeoutMillis = resourcesTimeout.toMillis() == 0 ? Integer.MAX_VALUE : resourcesTimeout.toMillis();
        CompletableFuture<JsonNode> resultFuture = null;
        try {
            resultFuture = executeViaTransport(context);
            JsonNode result = resultFuture.get(timeoutMillis, TimeUnit.MILLISECONDS);
            McpErrorHelper.checkForErrors(result);
            notifyListeners(l -> l.afterResourceUnsubscribe(context));
        } catch (TimeoutException timeout) {
            if (resultFuture != null) {
                resultFuture.cancel(true);
            }
            if (shouldSendCancellationNotification()) {
                McpCancellationNotification cancellation = new McpCancellationNotification(operationId, "Timeout");
                applyMeta(cancellation, null);
                transport.sendMessage(cancellation);
            }
            RuntimeException re = new RuntimeException(timeout);
            notifyListeners(l -> l.onResourceUnsubscribeError(context, re));
            throw re;
        } catch (ExecutionException e) {
            RuntimeException re = new RuntimeException(e);
            notifyListeners(l -> l.onResourceUnsubscribeError(context, re));
            throw re;
        } catch (McpException e) {
            notifyListeners(l -> l.onResourceUnsubscribeError(context, e));
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } finally {
            pendingOperations.remove(operationId);
        }
    }

    @Override
    public long subscribeToResources(List<String> uris) {
        assertNotClosed();
        if (!modernProtocol) {
            throw new UnsupportedOperationException("subscribeToResources requires MCP protocol 2026-07-28 or later");
        }

        McpSubscriptionsListenParams.Notifications notifications = new McpSubscriptionsListenParams.Notifications();
        notifications.setResourceSubscriptions(uris);

        long subscriptionId = idGenerator.getAndIncrement();
        McpSubscriptionsListenRequest request = new McpSubscriptionsListenRequest(subscriptionId);
        McpSubscriptionsListenParams params = new McpSubscriptionsListenParams();
        params.setNotifications(notifications);
        request.setParams(params);
        McpCallContext context = new McpCallContext(null, request);
        notifyListeners(l -> l.beforeResourcesSubscribe(context, uris));
        applyMeta(request, context);

        // The server acknowledges the subscription with a separate notification
        // (notifications/subscriptions/acknowledged), correlated by subscription ID.
        // Register a future for it before sending the request, so the ack can never
        // race ahead of us starting to listen for it.
        CompletableFuture<Map<String, Object>> ackFuture = new CompletableFuture<>();
        pendingSubscriptionAcks.put(subscriptionId, ackFuture);

        CompletableFuture<JsonNode> streamFuture;
        try {
            streamFuture = executeViaTransport(context);
        } catch (RuntimeException e) {
            pendingSubscriptionAcks.remove(subscriptionId);
            notifyListeners(l -> l.onResourcesSubscribeError(context, e));
            throw e;
        }
        activeSubscriptions.put(subscriptionId, streamFuture);
        streamFuture.whenComplete((result, error) -> {
            pendingOperations.remove(subscriptionId);
            if (error != null) {
                ackFuture.completeExceptionally(error);
                if (!closed) {
                    if (error instanceof CancellationException) {
                        // Expected when the client itself gave up (timeout, unsubscribe, close)
                        log.debug("Resource subscription {} was cancelled", subscriptionId);
                    } else {
                        log.warn("Resource subscription {} failed", subscriptionId, error);
                    }
                    activeSubscriptions.remove(subscriptionId);
                }
            } else if (result != null && result.has("error")) {
                try {
                    McpErrorHelper.checkForErrors(result);
                } catch (RuntimeException e) {
                    // Usually an McpException, but a malformed error object yields something else,
                    // and the caller must be unblocked either way
                    ackFuture.completeExceptionally(e);
                }
                if (!closed) {
                    log.warn("Resource subscription {} rejected by server: {}", subscriptionId, errorMessageOf(result));
                    activeSubscriptions.remove(subscriptionId);
                }
            } else {
                if (!ackFuture.isDone()) {
                    // A successful, non-error result means the server ended the subscription gracefully
                    // (see the spec's Graceful Closure). That normally happens long after the
                    // acknowledgement; arriving before it means the server never acknowledged at all.
                    ackFuture.completeExceptionally(
                            new IllegalStateException("The MCP server ended resource subscription " + subscriptionId
                                    + " without ever acknowledging it"));
                }
                if (!closed) {
                    activeSubscriptions.remove(subscriptionId);
                }
            }
        });

        long timeoutMillis = resourcesTimeout.toMillis() == 0 ? Integer.MAX_VALUE : resourcesTimeout.toMillis();
        try {
            Map<String, Object> acknowledgement = ackFuture.get(timeoutMillis, TimeUnit.MILLISECONDS);
            if (!honoursResourceSubscriptions(acknowledgement)) {
                abandonSubscription(subscriptionId, streamFuture, "The server declined the resource subscriptions");
                IllegalStateException e = new IllegalStateException("The MCP server acknowledged subscription "
                        + subscriptionId + " but declined to honour the requested resource subscriptions: " + uris);
                notifyListeners(l -> l.onResourcesSubscribeError(context, e));
                throw e;
            }
            notifyListeners(l -> l.afterResourcesSubscribe(context, subscriptionId, uris));
            return subscriptionId;
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            RuntimeException re = cause instanceof RuntimeException runtime ? runtime : new RuntimeException(cause);
            notifyListeners(l -> l.onResourcesSubscribeError(context, re));
            throw re;
        } catch (CancellationException e) {
            // The stream was cancelled before the acknowledgement arrived, e.g. by a concurrent
            // unsubscribeFromResources() or a server-sent notifications/cancelled. CompletableFuture
            // reports this one directly rather than wrapping it in an ExecutionException.
            notifyListeners(l -> l.onResourcesSubscribeError(context, e));
            throw e;
        } catch (TimeoutException e) {
            abandonSubscription(
                    subscriptionId, streamFuture, "Timed out waiting for the server to acknowledge the subscription");
            RuntimeException re = new RuntimeException(e);
            notifyListeners(l -> l.onResourcesSubscribeError(context, re));
            throw re;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } finally {
            pendingSubscriptionAcks.remove(subscriptionId);
        }
    }

    private static boolean honoursResourceSubscriptions(Map<String, Object> acknowledgement) {
        Object params = acknowledgement.get("params");
        Object notifications = params instanceof Map ? ((Map<?, ?>) params).get("notifications") : null;
        Object honoured =
                notifications instanceof Map ? ((Map<?, ?>) notifications).get("resourceSubscriptions") : null;
        return honoured instanceof List<?> list && !list.isEmpty();
    }

    private void abandonSubscription(long subscriptionId, CompletableFuture<JsonNode> streamFuture, String reason) {
        activeSubscriptions.remove(subscriptionId);
        streamFuture.cancel(true);
        if (transport.requiresCancellationNotification()) {
            McpCancellationNotification cancellation = new McpCancellationNotification(subscriptionId, reason);
            McpCallContext cancellationContext = new McpCallContext(null, cancellation);
            applyMeta(cancellationContext.message(), cancellationContext);
            transport.sendMessage(cancellationContext.message());
        }
    }

    @Override
    public void unsubscribeFromResources(long subscriptionId) {
        assertNotClosed();
        if (!modernProtocol) {
            throw new UnsupportedOperationException(
                    "unsubscribeFromResources requires MCP protocol 2026-07-28 or later");
        }
        McpCallContext context = null;
        if (transport.requiresCancellationNotification()) {
            McpCancellationNotification cancellation =
                    new McpCancellationNotification(subscriptionId, "Client unsubscribed");
            context = new McpCallContext(null, cancellation);
        }
        final McpCallContext finalContext = context;
        notifyListeners(l -> l.beforeResourcesUnsubscribe(finalContext, subscriptionId));

        CompletableFuture<JsonNode> streamFuture = activeSubscriptions.remove(subscriptionId);
        if (streamFuture != null) {
            // Cancel the future, which for HTTP transport propagates to
            // Flow.Subscription.cancel() and closes the SSE stream
            streamFuture.cancel(true);
            // Send notifications/cancelled for transports that need it (e.g. stdio)
            if (context != null) {
                applyMeta(context.message(), context);
                transport.sendMessage(context.message());
            }
        }

        notifyListeners(l -> l.afterResourcesUnsubscribe(finalContext, subscriptionId));
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
        McpCallContext listenerContext =
                new McpCallContext(invocationContext, new McpListToolsRequest(idGenerator.getAndIncrement(), null));
        notifyListeners(l -> l.beforeToolsList(listenerContext));
        try {
            List<ToolSpecification> list = fetchPaginatedList(
                    (id, cursor) -> new McpListToolsRequest(id, cursor),
                    toolExecutionTimeout,
                    invocationContext,
                    result -> ToolSpecificationHelper.toolSpecificationListFromMcpResponse(
                            McpJson.deserialize(result, McpListToolsResult.class)
                                    .getResult()
                                    .getTools()));
            toolListRefs.set(list);
            notifyListeners(l -> l.afterToolsList(listenerContext, list));
            return list;
        } catch (RuntimeException e) {
            notifyListeners(l -> l.onToolsListError(listenerContext, e));
            throw e;
        }
    }

    private List<McpResource> obtainResourceList(InvocationContext invocationContext) {
        McpCallContext listenerContext =
                new McpCallContext(invocationContext, new McpListResourcesRequest(idGenerator.getAndIncrement(), null));
        notifyListeners(l -> l.beforeResourcesList(listenerContext));
        try {
            List<McpResource> list = fetchPaginatedList(
                    (id, cursor) -> new McpListResourcesRequest(id, cursor),
                    resourcesTimeout,
                    invocationContext,
                    ResourcesHelper::parseResourceRefs);
            resourceRefs.set(list);
            notifyListeners(l -> l.afterResourcesList(listenerContext, list));
            return list;
        } catch (RuntimeException e) {
            notifyListeners(l -> l.onResourcesListError(listenerContext, e));
            throw e;
        }
    }

    private List<McpResourceTemplate> obtainResourceTemplateList(InvocationContext invocationContext) {
        McpCallContext listenerContext = new McpCallContext(
                invocationContext, new McpListResourceTemplatesRequest(idGenerator.getAndIncrement(), null));
        notifyListeners(l -> l.beforeResourceTemplatesList(listenerContext));
        try {
            List<McpResourceTemplate> list = fetchPaginatedList(
                    (id, cursor) -> new McpListResourceTemplatesRequest(id, cursor),
                    resourcesTimeout,
                    invocationContext,
                    ResourcesHelper::parseResourceTemplateRefs);
            resourceTemplateRefs.set(list);
            notifyListeners(l -> l.afterResourceTemplatesList(listenerContext, list));
            return list;
        } catch (RuntimeException e) {
            notifyListeners(l -> l.onResourceTemplatesListError(listenerContext, e));
            throw e;
        }
    }

    private List<McpPrompt> obtainPromptList(InvocationContext invocationContext) {
        McpCallContext listenerContext =
                new McpCallContext(invocationContext, new McpListPromptsRequest(idGenerator.getAndIncrement(), null));
        notifyListeners(l -> l.beforePromptsList(listenerContext));
        try {
            List<McpPrompt> list = fetchPaginatedList(
                    (id, cursor) -> new McpListPromptsRequest(id, cursor),
                    promptsTimeout,
                    invocationContext,
                    PromptsHelper::parsePromptRefs);
            promptRefs.set(list);
            notifyListeners(l -> l.afterPromptsList(listenerContext, list));
            return list;
        } catch (RuntimeException e) {
            notifyListeners(l -> l.onPromptsListError(listenerContext, e));
            throw e;
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
                CompletableFuture<JsonNode> resultFuture = executeViaTransport(context);
                result = resultFuture.get(timeoutMillis, TimeUnit.MILLISECONDS);
            } catch (ExecutionException | InterruptedException | TimeoutException e) {
                throw new RuntimeException(e);
            } finally {
                pendingOperations.remove(operation.getId());
            }
            // An error response carries no "result", so it has to be raised before anything reads that field.
            McpErrorHelper.checkForErrors(result);
            if (modernProtocol) {
                String resultType = getResultType(result);
                // servers may only send an InputRequiredResult in response to prompts/get, resources/read and
                // tools/call,
                // not in response to a list operation
                if ("input_required".equals(resultType)) {
                    throw new RuntimeException(
                            "Server returned input_required for a list operation, which the client cannot handle");
                }
                if (!"complete".equals(resultType)) {
                    throw new RuntimeException("Unexpected resultType: " + resultType);
                }
            }
            allItems.addAll(resultParser.apply(result));
            cursor = getNextCursor(result);
        } while (cursor != null);
        return allItems;
    }

    private static String errorMessageOf(JsonNode response) {
        McpErrorResponse.Error error =
                McpJson.deserialize(response, McpErrorResponse.class).getError();
        return error == null ? "" : error.getMessage();
    }

    private static String getNextCursor(JsonNode response) {
        McpPaginatedResult.Result result =
                McpJson.deserialize(response, McpPaginatedResult.class).getResult();
        if (result == null
                || result.getNextCursor() == null
                || result.getNextCursor().isEmpty()) {
            return null;
        }
        return result.getNextCursor();
    }

    @Override
    public void close() {
        closed = true;
        // Cancel list-change subscription
        CompletableFuture<JsonNode> listChangeFuture = listChangeSubscriptionFuture;
        if (listChangeFuture != null) {
            listChangeFuture.cancel(true);
        }
        // Unblock any subscribeToResources() call still waiting for a server acknowledgement.
        // This has to happen before the streams are cancelled below, otherwise the cancellation
        // completes those futures first and the caller sees a bare CancellationException instead.
        pendingSubscriptionAcks
                .values()
                .forEach(f -> f.completeExceptionally(new IllegalStateException("MCP client was closed")));
        pendingSubscriptionAcks.clear();
        // Cancel all active resource subscriptions
        activeSubscriptions.values().forEach(f -> f.cancel(true));
        activeSubscriptions.clear();
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
        // For modern protocol, inject required _meta fields on every request and notification
        if (modernProtocol) {
            String versionToAdvertise = protocolVersion != null ? protocolVersion : PROTOCOL_VERSION_MODERN;
            if (message instanceof McpClientRequest request) {
                if (request.getParams() == null) {
                    request.setParams(new McpClientParams());
                }
                applyModernMeta(request.getParams(), versionToAdvertise);
            } else if (message instanceof McpClientNotification notification) {
                if (notification.getParams() == null) {
                    notification.setParams(new McpClientParams());
                }
                applyModernMeta(notification.getParams(), versionToAdvertise);
            }
        }

        // Then merge user-supplied meta (existing logic)
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
            request.getParams().setMeta(mergeMeta(request.getParams().getMeta(), meta));
        } else if (message instanceof McpClientNotification notification) {
            if (notification.getParams() == null) {
                notification.setParams(new McpClientParams());
            }
            notification.getParams().setMeta(mergeMeta(notification.getParams().getMeta(), meta));
        }
    }

    /**
     * Merges the user-supplied {@code _meta} entries into the {@code _meta} already present on the
     * message. Entries already set by the client (such as the framework-managed
     * {@code progressToken}) take precedence, so that protocol metadata is never overwritten by the
     * user-supplied values.
     */
    private static Map<String, Object> mergeMeta(Map<String, Object> existing, Map<String, Object> supplied) {
        if (existing == null || existing.isEmpty()) {
            return supplied;
        }
        Map<String, Object> merged = new LinkedHashMap<>(supplied);
        merged.putAll(existing);
        return merged;
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> buildMcpParamHeaders(String toolName, Map<String, Object> arguments) {
        List<ToolSpecification> tools = toolListRefs.get();
        if (tools == null) {
            log.warn(
                    "Executing tool '{}' before the tool list is known, so no Mcp-Param headers can be sent."
                            + " Call listTools() first if the server declares x-mcp-header parameters.",
                    toolName);
            return null;
        }
        ToolSpecification spec = null;
        for (ToolSpecification t : tools) {
            if (t.name().equals(toolName)) {
                spec = t;
                break;
            }
        }
        if (spec == null || spec.metadata() == null) {
            return null;
        }
        Map<String, String> headerMappings =
                (Map<String, String>) spec.metadata().get(McpToolMetadataKeys.MCP_PARAM_HEADERS);
        if (headerMappings == null || headerMappings.isEmpty()) {
            return null;
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : headerMappings.entrySet()) {
            String propertyPath = entry.getKey();
            String headerName = entry.getValue();
            Object value = resolvePropertyPath(arguments, propertyPath);
            String stringValue;
            if (value instanceof String text) {
                stringValue = text;
            } else if (value instanceof Integer || value instanceof Long) {
                stringValue = String.valueOf(((Number) value).longValue());
            } else if (value instanceof Boolean bool) {
                stringValue = bool ? "true" : "false";
            } else {
                // nulls, floating-point and oversized numbers, objects and arrays are not sent as headers
                continue;
            }
            result.put(headerName, McpHeaderEncoding.encode(stringValue));
        }
        return result.isEmpty() ? null : result;
    }

    private static @Nullable Object resolvePropertyPath(Map<String, Object> root, String path) {
        String[] segments = path.split("\\.");
        Object current = root;
        for (String segment : segments) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(segment);
        }
        return current;
    }

    private void notifyListeners(Consumer<McpClientListener> action) {
        for (McpClientListener listener : listeners) {
            try {
                action.accept(listener);
            } catch (Exception e) {
                log.warn("MCP client listener threw an exception", e);
            }
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
        private Duration protocolDetectionTimeout;
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
        private final List<McpClientListener> listeners = new ArrayList<>();
        private McpProgressHandler progressHandler;
        private McpMetaSupplier metaSupplier;
        private BiConsumer<McpClient, String> onResourceUpdated;
        private McpToolResultConverter toolResultConverter;

        @Deprecated(since = "1.20.0", forRemoval = true)
        private McpToolResultExtractor toolResultExtractor;

        private Integer multiRoundTripMaxRetries;
        private Boolean subscribeToToolListChanges;
        private Boolean subscribeToPromptListChanges;
        private Boolean subscribeToResourceListChanges;

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
         * Sets the protocol version. If null or empty (the default), the client detects the
         * server's protocol version, preferring 2026-07-28 over 2025-11-25. If explicitly set
         * to "2026-07-28", modern protocol is forced. If set to "2025-11-25", legacy protocol
         * is forced. Any other value triggers detection but uses the given string when
         * advertising the version to the server.
         * <p>
         * Detection costs one extra round trip at startup: the client sends {@code server/discover}
         * and falls back to the legacy {@code initialize} handshake if the server answers with an
         * error or does not answer within {@link #protocolDetectionTimeout(Duration)}. Setting the
         * version explicitly skips the detection round trip entirely, which is also the way out if
         * a server reacts badly to being sent a method it does not know.
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
         * Sets how long the client waits for the server to answer the {@code server/discover}
         * request that detects which protocol version the server speaks. A server that does not
         * answer within this time is assumed to speak the legacy protocol, and the client falls
         * back to the {@code initialize} handshake.
         * <p>
         * This only applies while the protocol version is being detected, which is the case when
         * no explicit {@link #protocolVersion(String)} is set. It defaults to
         * {@link #initializationTimeout(Duration)}, because a server that is started as a
         * subprocess needs time to boot before it can answer anything, and a detection request
         * that gives up too early makes a modern server look like a legacy one.
         * <p>
         * Lower it if your servers answer quickly and you would rather not wait on one that
         * ignores the request instead of rejecting it.
         */
        public Builder protocolDetectionTimeout(Duration protocolDetectionTimeout) {
            this.protocolDetectionTimeout = protocolDetectionTimeout;
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
         *
         * @deprecated Use {@link #addListener(McpClientListener)} instead.
         */
        @Deprecated
        public Builder listener(McpClientListener listener) {
            this.listeners.add(listener);
            return this;
        }

        /**
         * Adds a listener to receive MCP client events.
         * Multiple listeners can be added; they will all be invoked
         * before and after each call to the MCP server.
         * Currently, this applies to tool calls, resource retrievals, and prompt retrievals.
         */
        public Builder addListener(McpClientListener listener) {
            this.listeners.add(listener);
            return this;
        }

        /**
         * Adds multiple listeners to receive MCP client events.
         * All listeners will be invoked before and after each call to the MCP server.
         * Currently, this applies to tool calls, resource retrievals, and prompt retrievals.
         */
        public Builder addListeners(List<McpClientListener> listeners) {
            this.listeners.addAll(listeners);
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

        /**
         * Sets the extractor used for MCP tool responses backed by {@code content[]}.
         * Takes precedence over {@link #toolResultExtractor(McpToolResultExtractor)}.
         */
        public Builder toolResultConverter(McpToolResultConverter toolResultConverter) {
            this.toolResultConverter = ensureNotNull(toolResultConverter, "toolResultConverter");
            return this;
        }

        /**
         * Sets the extractor used for MCP tool responses that return ordinary
         * {@code CallToolResult.result.content[]} items. Responses with
         * {@code structuredContent} are handled separately and are not affected by this setting.
         *
         * @deprecated use {@link #toolResultConverter(McpToolResultConverter)}, which does not expose
         * Jackson types. Setting both is rejected with an {@link IllegalArgumentException}.
         */
        @Deprecated(since = "1.20.0", forRemoval = true)
        public Builder toolResultExtractor(McpToolResultExtractor toolResultExtractor) {
            this.toolResultExtractor = ensureNotNull(toolResultExtractor, "toolResultExtractor");
            return this;
        }

        /**
         * Sets a callback to be invoked when the server sends a
         * {@code notifications/resources/updated} notification for a
         * subscribed resource. The callback receives the instance
         * of the affected MCP client and the URI of the
         * updated resource.
         */
        public Builder onResourceUpdated(BiConsumer<McpClient, String> onResourceUpdated) {
            this.onResourceUpdated = onResourceUpdated;
            return this;
        }

        /**
         * Sets whether to automatically subscribe to tool list change notifications
         * when using MCP protocol 2026-07-28 or later. Default is true.
         */
        public Builder subscribeToToolListChanges(boolean subscribe) {
            this.subscribeToToolListChanges = subscribe;
            return this;
        }

        /**
         * Sets whether to automatically subscribe to prompt list change notifications
         * when using MCP protocol 2026-07-28 or later. Default is true.
         */
        public Builder subscribeToPromptListChanges(boolean subscribe) {
            this.subscribeToPromptListChanges = subscribe;
            return this;
        }

        /**
         * Sets whether to automatically subscribe to resource list change notifications
         * when using MCP protocol 2026-07-28 or later. Default is true.
         */
        public Builder subscribeToResourceListChanges(boolean subscribe) {
            this.subscribeToResourceListChanges = subscribe;
            return this;
        }

        /**
         * Sets the maximum number of multi round-trip retries for operations
         * that return {@code input_required} (MCP protocol 2026-07-28 or later).
         * The default is 3.
         */
        public Builder multiRoundTripMaxRetries(int multiRoundTripMaxRetries) {
            this.multiRoundTripMaxRetries = multiRoundTripMaxRetries;
            return this;
        }

        public DefaultMcpClient build() {
            return new DefaultMcpClient(this);
        }
    }

    private CompletableFuture<JsonNode> executeViaTransport(McpCallContext context) {
        return McpJson.map(transport.sendRequest(context), McpJson::parse);
    }

    private CompletableFuture<JsonNode> executeViaTransport(McpClientMessage message) {
        return McpJson.map(transport.sendRequest(message), McpJson::parse);
    }

    private CompletableFuture<JsonNode> initializeViaTransport(McpInitializeRequest request) {
        return McpJson.map(transport.sendInitializeRequest(request), McpJson::parse);
    }
}
