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
import dev.langchain4j.mcp.client.protocol.InitializeParams;
import dev.langchain4j.mcp.client.protocol.McpCallToolRequest;
import dev.langchain4j.mcp.client.protocol.McpInitializeRequest;
import dev.langchain4j.mcp.client.protocol.McpListToolsRequest;
import dev.langchain4j.mcp.client.transport.McpTransport;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// TODO: currently we request a new list of tools every time, so we should
// add support for the `ToolListChangedNotification` message, and then we can
// cache the list
public class DefaultMcpClient implements McpClient {

    private static final Logger log = LoggerFactory.getLogger(DefaultMcpClient.class);
    private final AtomicLong idGenerator = new AtomicLong(0);
    private final McpTransport transport;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final String clientName;
    private final String clientVersion;
    private final String protocolVersion;
    private final Duration toolExecutionTimeout;
    private final JsonNode RESULT_TIMEOUT;
    private final String toolExecutionTimeoutErrorMessage;

    public DefaultMcpClient(Builder builder) {
        transport = ensureNotNull(builder.transport, "transport");
        clientName = getOrDefault(builder.clientName, "langchain4j");
        clientVersion = getOrDefault(builder.clientVersion, "1.0");
        protocolVersion = getOrDefault(builder.protocolVersion, "2024-11-05");
        toolExecutionTimeout = getOrDefault(builder.toolExecutionTimeout, Duration.ofSeconds(60));
        toolExecutionTimeoutErrorMessage =
                getOrDefault(builder.toolExecutionTimeoutErrorMessage, "There was a timeout executing the tool");
        RESULT_TIMEOUT = JsonNodeFactory.instance.objectNode();
        ((ObjectNode) RESULT_TIMEOUT)
                .putObject("result")
                .putArray("content")
                .addObject()
                .put("type", "text")
                .put("text", toolExecutionTimeoutErrorMessage);

        // Initialize the client...
        transport.start();
        McpInitializeRequest request = new McpInitializeRequest(idGenerator.getAndIncrement());
        InitializeParams params = createInitializeParams();
        request.setParams(params);
        JsonNode capabilities = transport.initialize(request);
        log.debug("MCP server capabilities: {}", capabilities.get("result"));
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
        JsonNode jsonNode = transport.listTools(operation);
        return ToolSpecificationHelper.toolSpecificationListFromMcpResponse(
                (ArrayNode) jsonNode.get("result").get("tools"));
    }

    @Override
    public String executeTool(ToolExecutionRequest executionRequest) {
        ObjectNode arguments = null;
        try {
            arguments = OBJECT_MAPPER.readValue(executionRequest.arguments(), ObjectNode.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        McpCallToolRequest operation =
                new McpCallToolRequest(idGenerator.getAndIncrement(), executionRequest.name(), arguments);
        JsonNode executionResult = null;
        try {
            executionResult = transport.executeTool(operation, toolExecutionTimeout);
        } catch (TimeoutException timeout) {
            executionResult = RESULT_TIMEOUT;
        }
        return ToolExecutionHelper.extractResult(
                (ArrayNode) executionResult.get("result").get("content"));
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
         */
        public Builder toolExecutionTimeout(Duration toolExecutionTimeout) {
            this.toolExecutionTimeout = toolExecutionTimeout;
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

        public DefaultMcpClient build() {
            return new DefaultMcpClient(this);
        }
    }
}
