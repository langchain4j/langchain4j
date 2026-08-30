package dev.langchain4j.mcp.client;

import com.fasterxml.jackson.databind.JsonNode;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.exception.ToolArgumentsException;
import dev.langchain4j.exception.ToolExecutionException;
import dev.langchain4j.mcp.client.transport.McpJson;
import dev.langchain4j.mcp.protocol.McpCallToolResult;
import dev.langchain4j.mcp.protocol.McpErrorResponse;
import dev.langchain4j.service.tool.ToolExecutionResult;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

class ToolExecutionHelper {

    private static final int ERROR_CODE_INVALID_PARAMETERS = -32602;

    /**
     * Extracts a response from a CallToolResult message. This may be an error response.
     * If the response contains both 'content' and 'structuredContent' elements, the
     * structured content is given precedence.
     * The entries of the '_meta' element, if present, are stored in
     * {@link ToolExecutionResult#attributes()} and are not sent to the LLM.
     */
    static ToolExecutionResult extractResult(
            JsonNode response, boolean ignoreApplicationLevelErrors, McpToolResultConverter toolResultConverter) {

        McpCallToolResult.Result result =
                McpJson.deserialize(response, McpCallToolResult.class).getResult();

        if (result != null) {
            boolean applicationError = Boolean.TRUE.equals(result.getIsError());
            Map<String, Object> attributes = toolAttributes(result.getMeta());

            if (result.getStructuredContent() != null) {
                String resultText = McpJson.serialize(result.getStructuredContent());
                if (applicationError && !ignoreApplicationLevelErrors) {
                    throw new ToolExecutionException(resultText);
                }
                return ToolExecutionResult.builder()
                        .result(result.getStructuredContent())
                        .resultText(resultText)
                        .isError(applicationError)
                        .attributes(attributes)
                        .build();
            }

            if (result.getContent() != null) {
                ToolExecutionResult toolExecutionResult =
                        toolResultConverter.convert(result.getContent(), applicationError);
                if (applicationError && !ignoreApplicationLevelErrors) {
                    throw new ToolExecutionException(errorMessage(toolExecutionResult, result.getContent()));
                }
                return withAttributes(toolExecutionResult, attributes);
            }

            throw new RuntimeException("Result does not contain 'content' element: " + response);
        }

        McpErrorResponse.Error error =
                McpJson.deserialize(response, McpErrorResponse.class).getError();
        if (error != null) {
            if (error.getCode() == ERROR_CODE_INVALID_PARAMETERS) {
                throw new ToolArgumentsException(error.getMessage(), error.getCode());
            }
            throw new ToolExecutionException(error.getMessage(), error.getCode());
        }

        throw new RuntimeException("Result contains neither 'result' nor 'error' element: " + response);
    }

    /**
     * Converts the entries of the '_meta' element of a CallToolResult into tool execution attributes.
     * Keys reserved by the MCP specification, such as 'io.modelcontextprotocol/serverInfo',
     * are skipped, as they describe the protocol interaction and not the tool result.
     */
    private static Map<String, Object> toolAttributes(Map<String, Object> meta) {
        if (meta == null) {
            return Map.of();
        }
        Map<String, Object> attributes = new HashMap<>();
        meta.forEach((key, value) -> {
            if (!isReservedByMcp(key)) {
                attributes.put(key, value);
            }
        });
        return attributes;
    }

    /**
     * A '_meta' key can start with a prefix: a series of labels separated by dots, followed by a slash.
     * Prefixes whose second label is 'modelcontextprotocol' or 'mcp' are reserved by the MCP specification,
     * for example 'io.modelcontextprotocol/serverInfo' or 'com.mcp.tools/something'.
     */
    private static boolean isReservedByMcp(String key) {
        int slashIndex = key.indexOf('/');
        if (slashIndex < 0) {
            return false;
        }
        String[] labels = key.substring(0, slashIndex).split("\\.");
        return labels.length > 1 && (labels[1].equals("modelcontextprotocol") || labels[1].equals("mcp"));
    }

    /**
     * Adds the given attributes to a {@link ToolExecutionResult} produced by a {@link McpToolResultConverter}.
     * Attributes set by the extractor take precedence.
     */
    private static ToolExecutionResult withAttributes(ToolExecutionResult result, Map<String, Object> attributes) {
        if (attributes.isEmpty()) {
            return result;
        }
        Map<String, Object> mergedAttributes = new HashMap<>(attributes);
        mergedAttributes.putAll(result.attributes());
        return result.toBuilder().attributes(mergedAttributes).build();
    }

    private static String errorMessage(ToolExecutionResult toolExecutionResult, List<Map<String, Object>> content) {
        String contentsText = toolExecutionResult.resultContents().stream()
                .filter(TextContent.class::isInstance)
                .map(TextContent.class::cast)
                .map(TextContent::text)
                .collect(Collectors.joining("\n"));
        if (!contentsText.isEmpty()) {
            return contentsText;
        }
        if (toolExecutionResult.result() != null) {
            return toolExecutionResult.result().toString();
        }
        String rawContentText = content.stream()
                .map(ToolExecutionHelper::textFromContentItem)
                .filter(text -> !text.isEmpty())
                .collect(Collectors.joining("\n"));
        if (!rawContentText.isEmpty()) {
            return rawContentText;
        }
        return "";
    }

    private static String textFromContentItem(Map<String, Object> contentItem) {
        Object type = contentItem.get("type");
        Object text = contentItem.get("text");
        if ("text".equals(type) && text != null) {
            return String.valueOf(text);
        }
        return "";
    }

}
