package dev.langchain4j.mcp.client;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.logging.DefaultMcpLogMessageHandler;
import dev.langchain4j.mcp.client.logging.McpLogMessageHandler;
import dev.langchain4j.mcp.client.protocol.CancellationNotification;
import dev.langchain4j.mcp.client.protocol.InitializeParams;
import dev.langchain4j.mcp.client.protocol.McpCallToolRequest;
import dev.langchain4j.mcp.client.protocol.McpGetPromptRequest;
import dev.langchain4j.mcp.client.protocol.McpInitializeRequest;
import dev.langchain4j.mcp.client.protocol.McpListPromptsRequest;
import dev.langchain4j.mcp.client.protocol.McpListResourceTemplatesRequest;
import dev.langchain4j.mcp.client.protocol.McpListResourcesRequest;
import dev.langchain4j.mcp.client.protocol.McpListToolsRequest;
import dev.langchain4j.mcp.client.protocol.McpReadResourceRequest;
import dev.langchain4j.mcp.client.transport.McpOperationHandler;
import dev.langchain4j.mcp.client.transport.McpTransport;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// TODO: currently we request a new list of tools every time, so we should
// add support for the `ToolListChangedNotification` message, and then we can
// cache the list
public class DefaultMcpClient implements McpClient {

    private static final Logger log = LoggerFactory.getLogger(DefaultMcpClient.class);
    private final AtomicLong idGenerator = new AtomicLong(0);
    private final McpTransport transport;
    static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final String clientName;
    private final String clientVersion;
    private final String protocolVersion;
    private final Duration toolExecutionTimeout;
    private final Duration resourcesTimeout;
    private final Duration promptsTimeout;
    private final JsonNode RESULT_TIMEOUT;
    private final String toolExecutionTimeoutErrorMessage;
    private final Map<Long, CompletableFuture<JsonNode>> pendingOperations = new ConcurrentHashMap<>();
    private final McpOperationHandler messageHandler;
    private final McpLogMessageHandler logHandler;
    private final AtomicReference<List<McpResource>> resourceRefs = new AtomicReference<>();
    private final AtomicReference<List<McpResourceTemplate>> resourceTemplateRefs = new AtomicReference<>();
    private final AtomicReference<List<McpPrompt>> promptRefs = new AtomicReference<>();

    public DefaultMcpClient(Builder builder) {
        transport = ensureNotNull(builder.transport, "transport");
        clientName = getOrDefault(builder.clientName, "langchain4j");
        clientVersion = getOrDefault(builder.clientVersion, "1.0");
        protocolVersion = getOrDefault(builder.protocolVersion, "2024-11-05");
        toolExecutionTimeout = getOrDefault(builder.toolExecutionTimeout, Duration.ofSeconds(60));
        resourcesTimeout = getOrDefault(builder.resourcesTimeout, Duration.ofSeconds(60));
        promptsTimeout = getOrDefault(builder.promptsTimeout, Duration.ofSeconds(60));
        logHandler = getOrDefault(builder.logHandler, new DefaultMcpLogMessageHandler());
        toolExecutionTimeoutErrorMessage =
                getOrDefault(builder.toolExecutionTimeoutErrorMessage, "There was a timeout executing the tool");
        RESULT_TIMEOUT = JsonNodeFactory.instance.objectNode();
        messageHandler = new McpOperationHandler(pendingOperations, transport, logHandler::handleLogMessage);
        ((ObjectNode) RESULT_TIMEOUT)
                .putObject("result")
                .putArray("content")
                .addObject()
                .put("type", "text")
                .put("text", toolExecutionTimeoutErrorMessage);
        initialize();
    }

    private void initialize() {
        transport.start(messageHandler);
        long operationId = idGenerator.getAndIncrement();
        McpInitializeRequest request = new McpInitializeRequest(operationId);
        InitializeParams params = createInitializeParams();
        request.setParams(params);
        try {
            JsonNode capabilities = transport.initialize(request).get();
            log.debug("MCP server capabilities: {}", capabilities.get("result"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            pendingOperations.remove(operationId);
        }
    }

    private InitializeParams createInitializeParams() {
        InitializeParams params = new InitializeParams();
        params.setProtocolVersion(protocolVersion);

        InitializeParams.ClientInfo clientInfo = new InitializeParams.ClientInfo();
        clientInfo.setName(clientName);
        clientInfo.setVersion(clientVersion);
        params.setClientInfo(clientInfo);

        InitializeParams.Capabilities capabilities = new InitializeParams.Capabilities();
        InitializeParams.Capabilities.Roots roots = new InitializeParams.Capabilities.Roots();
        roots.setListChanged(false); // TODO: listChanged is not supported yet
        capabilities.setRoots(roots);
        params.setCapabilities(capabilities);

        return params;
    }

    @Override
    public List<ToolSpecification> listTools() {
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

        return ToolSpecificationHelper.toolSpecificationListFromMcpResponse(
                (ArrayNode) result.get("result").get("tools"));
    }

    @Override
    public String executeTool(ToolExecutionRequest executionRequest) {
        ObjectNode arguments = null;
        try {
            arguments = OBJECT_MAPPER.readValue(executionRequest.arguments(), ObjectNode.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
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
            transport.executeOperationWithoutResponse(new CancellationNotification(operationId, "Timeout"));
            return ToolExecutionHelper.extractResult(RESULT_TIMEOUT);
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            pendingOperations.remove(operationId);
        }
        return ToolExecutionHelper.extractResult(result);
    }

    @Override
    public List<McpResource> listResources() {
        if (resourceRefs.get() == null) {
            obtainResourceList();
        }
        return resourceRefs.get();
    }

    @Override
    public McpReadResourceResult readResource(String uri) {
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
        if (promptRefs.get() == null) {
            obtainPromptList();
        }
        return promptRefs.get();
    }

    @Override
    public McpGetPromptResult getPrompt(String name, Map<String, Object> arguments) {
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
    public List<McpResourceTemplate> listResourceTemplates() {
        if (resourceTemplateRefs.get() == null) {
            obtainResourceTemplateList();
        }
        return resourceTemplateRefs.get();
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
        try {
            transport.close();
        } catch (Exception e) {
            log.warn("Cannot close MCP transport", e);
        }
    }

    public static class Builder {

        private String toolExecutionTimeoutErrorMessage;
        private McpTransport transport;
        private String clientName;
        private String clientVersion;
        private String protocolVersion;
        private Duration toolExecutionTimeout;
        private Duration resourcesTimeout;
        private Duration promptsTimeout;
        private McpLogMessageHandler logHandler;

        public Builder transport(McpTransport transport) {
            this.transport = transport;
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

        public DefaultMcpClient build() {
            return new DefaultMcpClient(this);
        }
    }
}
