package dev.langchain4j.mcp.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import dev.langchain4j.exception.ToolArgumentsException;
import dev.langchain4j.exception.ToolExecutionException;
import dev.langchain4j.service.tool.ToolExecutionResult;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

class ToolExecutionHelper {

    private static final int ERROR_CODE_INVALID_PARAMETERS = -32602;

    /**
     * Extracts a response from a CallToolResult message. This may be an error response.
     * If the response contains both 'content' and 'structuredContent' elements, the
     * structured content is given precedence.
     */
    static ToolExecutionResult extractResult(JsonNode result, boolean ignoreApplicationLevelErrors) {
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
                String content = extractSuccessfulResult((ArrayNode) resultNode.get("content"));
                if (isError(resultNode) && !ignoreApplicationLevelErrors) {
                    throw new ToolExecutionException(content);
                }
                return ToolExecutionResult.builder()
                        .isError(isError(resultNode))
                        .resultText(content)
                        .build();
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

    private static String extractSuccessfulResult(ArrayNode contents) {
        Stream<JsonNode> contentStream = StreamSupport.stream(contents.spliterator(), false);
        return contentStream
                .map(content -> {
                    if (!content.get("type").asText().equals("text")) {
                        throw new RuntimeException("Unsupported content type: " + content.get("type"));
                    }
                    return content.get("text").asText();
                })
                .collect(Collectors.joining("\n"));
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
