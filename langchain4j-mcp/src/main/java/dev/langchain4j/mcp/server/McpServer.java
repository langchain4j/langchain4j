package dev.langchain4j.mcp.server;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.mcp.protocol.McpCallToolRequest;
import dev.langchain4j.mcp.protocol.McpCallToolResult;
import dev.langchain4j.mcp.protocol.McpErrorResponse;
import dev.langchain4j.mcp.protocol.McpInitializeParams;
import dev.langchain4j.mcp.protocol.McpInitializeRequest;
import dev.langchain4j.mcp.protocol.McpInitializeResult;
import dev.langchain4j.mcp.protocol.McpJsonRpcMessage;
import dev.langchain4j.mcp.protocol.McpListToolsRequest;
import dev.langchain4j.mcp.protocol.McpListToolsResult;
import dev.langchain4j.service.tool.DefaultToolExecutor;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolExecutor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class McpServer {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    private static final String DEFAULT_PROTOCOL_VERSION = "2025-06-18";
    private static final int ERROR_CODE_METHOD_NOT_FOUND = -32601;
    private static final int ERROR_CODE_INVALID_PARAMS = -32602;
    private static final String PARAMS_FIELD = "params";
    private static final String ARGUMENTS_FIELD = "arguments";

    private final List<ToolSpecification> toolSpecifications;
    private final Map<String, ToolExecutor> toolExecutors;
    private final List<Map<String, Object>> mcpTools;
    private final McpToolSchemaMapper toolSchemaMapper = new McpToolSchemaMapper();

    public McpServer(List<Object> tools) {
        ensureNotNull(tools, "tools");

        List<ToolSpecification> specs = new ArrayList<>();
        Map<String, ToolExecutor> executors = new ConcurrentHashMap<>();
        for (Object tool : tools) {
            ensureNotNull(tool, "tool");
            specs.addAll(ToolSpecifications.toolSpecificationsFrom(tool));
            addExecutors(tool, executors);
        }

        ToolSpecifications.validateSpecifications(specs);

        this.toolSpecifications = List.copyOf(specs);
        this.toolExecutors = Map.copyOf(executors);
        this.mcpTools = List.copyOf(toolSchemaMapper.toMcpTools(this.toolSpecifications));
    }

    public McpJsonRpcMessage handle(JsonNode message) {
        if (message == null || !message.has("method")) {
            return null;
        }

        Long id = extractId(message);
        if (id == null) {
            return null;
        }

        String method = message.get("method").asText();
        return switch (method) {
            case "initialize" -> handleInitialize(parseInitializeRequest(id, message));
            case "tools/list" -> handleListTools(parseListToolsRequest(id, message));
            case "tools/call" -> handleCallTool(parseCallToolRequest(id, message));
            default -> new McpErrorResponse(id, new McpErrorResponse.Error(
                    ERROR_CODE_METHOD_NOT_FOUND,
                    "Method not found: " + method,
                    null));
        };
    }

    private McpInitializeResult handleInitialize(McpInitializeRequest request) {
        String protocolVersion = DEFAULT_PROTOCOL_VERSION;
        if (request.getParams() != null && request.getParams().getProtocolVersion() != null) {
            protocolVersion = request.getParams().getProtocolVersion();
        }
        McpInitializeResult.Capabilities capabilities = new McpInitializeResult.Capabilities(true);
        McpInitializeResult.Result result = new McpInitializeResult.Result(
                protocolVersion,
                capabilities,
                null);
        return new McpInitializeResult(request.getId(), result);
    }

    private McpListToolsResult handleListTools(McpListToolsRequest request) {
        McpListToolsResult.Result result = new McpListToolsResult.Result(mcpTools, null);
        return new McpListToolsResult(request.getId(), result);
    }

    private McpJsonRpcMessage handleCallTool(McpCallToolRequest request) {
        Map<String, Object> params = request.getParams();
        if (params == null || !params.containsKey("name") || params.get("name") == null) {
            return new McpErrorResponse(request.getId(), new McpErrorResponse.Error(
                    ERROR_CODE_INVALID_PARAMS,
                    "Missing tool name",
                    null));
        }

        String toolName = String.valueOf(params.get("name"));
        ToolExecutor toolExecutor = toolExecutors.get(toolName);
        if (toolExecutor == null) {
            return toCallToolError(request.getId(), "Unknown tool: " + toolName);
        }

        String arguments = null;
        Object args = params.get(ARGUMENTS_FIELD);
        if (args != null) {
            try {
                arguments = OBJECT_MAPPER.writeValueAsString(args);
            } catch (JsonProcessingException e) {
                return toCallToolError(request.getId(), "Failed to serialize tool arguments");
            }
        }

        ToolExecutionRequest toolRequest = ToolExecutionRequest.builder()
                .id(String.valueOf(request.getId()))
                .name(toolName)
                .arguments(arguments)
                .build();

        try {
            ToolExecutionResult result = toolExecutor.executeWithContext(toolRequest, null);
            return toCallToolResult(request.getId(), result);
        } catch (Exception e) {
            return toCallToolError(request.getId(), safeMessage(e));
        }
    }

    private McpCallToolResult toCallToolResult(Long id, ToolExecutionResult result) {
        String text = result.resultText();
        McpCallToolResult.Content content = new McpCallToolResult.Content("text", text);
        Boolean isError = result.isError() ? Boolean.TRUE : null;
        McpCallToolResult.Result response = new McpCallToolResult.Result(
                List.of(content),
                null,
                isError);
        return new McpCallToolResult(id, response);
    }

    private McpCallToolResult toCallToolError(Long id, String message) {
        McpCallToolResult.Content content = new McpCallToolResult.Content("text", message);
        McpCallToolResult.Result response = new McpCallToolResult.Result(
                List.of(content),
                null,
                true);
        return new McpCallToolResult(id, response);
    }

    private void addExecutors(Object tool, Map<String, ToolExecutor> executors) {
        for (Method method : tool.getClass().getDeclaredMethods()) {
            if (!method.isAnnotationPresent(Tool.class)) {
                continue;
            }
            ToolSpecification specification = ToolSpecifications.toolSpecificationFrom(method);
            String toolName = specification.name();
            if (executors.containsKey(toolName)) {
                throw new IllegalArgumentException("Duplicated tool name: " + toolName);
            }
            ToolExecutor executor = DefaultToolExecutor.builder()
                    .object(tool)
                    .originalMethod(method)
                    .methodToInvoke(method)
                    .wrapToolArgumentsExceptions(true)
                    .propagateToolExecutionExceptions(true)
                    .build();
            executors.put(toolName, executor);
        }
    }

    private McpInitializeRequest parseInitializeRequest(Long id, JsonNode message) {
        McpInitializeRequest request = new McpInitializeRequest(id);
        if (message.has(PARAMS_FIELD)) {
            McpInitializeParams params =
                    OBJECT_MAPPER.convertValue(message.get(PARAMS_FIELD), McpInitializeParams.class);
            request.setParams(params);
        }
        return request;
    }

    private McpListToolsRequest parseListToolsRequest(Long id, JsonNode message) {
        McpListToolsRequest request = new McpListToolsRequest(id);
        if (message.has(PARAMS_FIELD) && message.get(PARAMS_FIELD).has("cursor")) {
            request.setCursor(message.get(PARAMS_FIELD).get("cursor").asText());
        }
        return request;
    }

    private McpCallToolRequest parseCallToolRequest(Long id, JsonNode message) {
        JsonNode params = message.get(PARAMS_FIELD);
        String toolName = params != null && params.has("name") ? params.get("name").asText() : null;
        ObjectNode arguments = params != null && params.has(ARGUMENTS_FIELD)
                ? OBJECT_MAPPER.convertValue(params.get(ARGUMENTS_FIELD), ObjectNode.class)
                : OBJECT_MAPPER.createObjectNode();
        return new McpCallToolRequest(id, toolName, arguments);
    }

    private Long extractId(JsonNode message) {
        JsonNode idNode = message.get("id");
        if (idNode == null || idNode.isNull()) {
            return null;
        }
        return idNode.isNumber() ? idNode.asLong() : null;
    }

    private String safeMessage(Exception e) {
        if (e.getMessage() != null && !e.getMessage().isBlank()) {
            return e.getMessage();
        }
        return e.getClass().getName();
    }
}
