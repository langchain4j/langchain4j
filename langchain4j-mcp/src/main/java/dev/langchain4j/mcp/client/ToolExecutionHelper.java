package dev.langchain4j.mcp.client;

import com.fasterxml.jackson.databind.JsonNode;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.exception.ToolArgumentsException;
import dev.langchain4j.exception.ToolExecutionException;
import dev.langchain4j.service.tool.ToolExecutionResult;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

class ToolExecutionHelper {

    private static final int ERROR_CODE_INVALID_PARAMETERS = -32602;

    /**
     * Extracts a response from a CallToolResult message. This may be an error response.
     * If the response contains both 'content' and 'structuredContent' elements, the
     * structured content is given precedence.
     */
    static ToolExecutionResult extractResult(
            JsonNode result, boolean ignoreApplicationLevelErrors, McpToolResultExtractor toolResultExtractor) {
        if (result.has("result")) {
            JsonNode resultNode = result.get("result");
            if (resultNode.has("structuredContent")
                    && !resultNode.get("structuredContent").isNull()) {
                JsonNode content = resultNode.get("structuredContent");
                if (isError(resultNode) && !ignoreApplicationLevelErrors) {
                    throw new ToolExecutionException(content.toString());
                }
                return ToolExecutionResult.builder()
                        .result(toObject(content))
                        .resultText(content.toString())
                        .isError(isError(resultNode))
                        .build();
            } else if (resultNode.has("content")) {
                boolean applicationError = isError(resultNode);
                ToolExecutionResult toolExecutionResult =
                        toolResultExtractor.extract(resultNode.get("content"), applicationError);
                if (applicationError && !ignoreApplicationLevelErrors) {
                    throw new ToolExecutionException(errorMessage(toolExecutionResult, resultNode.get("content")));
                }
                return toolExecutionResult;
            } else {
                throw new RuntimeException("Result does not contain 'content' element: " + result);
            }
        } else {
            if (result.has("error")) {
                String errorMessage = extractErrorMessage(result.get("error"));
                Integer errorCode = extractErrorCode(result.get("error"));
                if (errorCode != null && errorCode == ERROR_CODE_INVALID_PARAMETERS) {
                    throw new ToolArgumentsException(errorMessage, errorCode);
                } else {
                    throw new ToolExecutionException(errorMessage, errorCode);
                }
            }
            throw new RuntimeException("Result contains neither 'result' nor 'error' element: " + result);
        }
    }

    private static String errorMessage(ToolExecutionResult toolExecutionResult, JsonNode content) {
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
        String rawContentText = StreamSupport.stream(content.spliterator(), false)
                .map(ToolExecutionHelper::textFromContentItem)
                .filter(text -> !text.isEmpty())
                .collect(Collectors.joining("\n"));
        if (!rawContentText.isEmpty()) {
            return rawContentText;
        }
        return "";
    }

    private static String textFromContentItem(JsonNode contentItem) {
        JsonNode type = contentItem.get("type");
        JsonNode text = contentItem.get("text");
        if (type != null && "text".equals(type.asText()) && text != null) {
            return text.asText();
        }
        return "";
    }

    /**
     * Converts any JsonNode into a recursive Map using basic Java types
     */
    static Object toObject(JsonNode content) {
        return switch (content.getNodeType()) {
            case BOOLEAN -> content.asBoolean();
            case NUMBER ->
                switch (content.numberType()) {
                    case INT -> content.asInt();
                    case LONG, BIG_INTEGER -> content.asLong();
                    case FLOAT, DOUBLE, BIG_DECIMAL -> content.asDouble();
                };
            case STRING -> content.asText();
            case NULL -> null;
            case ARRAY ->
                StreamSupport.stream(content.spliterator(), true)
                        .map(element -> toObject(element))
                        .collect(Collectors.toList());
            case OBJECT -> {
                Map<String, Object> map = new HashMap<>();
                for (Map.Entry<String, JsonNode> property : content.properties()) {
                    map.put(property.getKey(), toObject(property.getValue()));
                }
                yield map;
            }
            case BINARY -> {
                try {
                    yield content.binaryValue();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            case POJO -> new Object(); // shouldn't happen
            case MISSING -> new Object(); // shouldn't happen
        };
    }

    private static boolean isError(JsonNode resultNode) {
        if (resultNode.has("isError")) {
            return resultNode.get("isError").asBoolean();
        }
        return false;
    }

    private static String extractErrorMessage(JsonNode errorNode) {
        if (errorNode.has("message")) {
            return errorNode.get("message").asText("");
        }
        return "";
    }

    private static Integer extractErrorCode(JsonNode errorNode) {
        if (errorNode.has("code")) {
            return errorNode.get("code").asInt();
        }
        return null;
    }
}
