package dev.langchain4j.mcp.client;

import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import dev.langchain4j.exception.ToolArgumentsException;
import dev.langchain4j.exception.ToolExecutionException;

class ToolExecutionHelper {

    private static final int ERROR_CODE_INVALID_PARAMETERS = -32602;

    /**
     * Extracts a response from a CallToolResult message. This may be an error response.
     */
    static String extractResult(JsonNode result) {
        if (result.has("result")) {
            JsonNode resultNode = result.get("result");
            if (resultNode.has("content")) {
                String content = extractSuccessfulResult((ArrayNode) resultNode.get("content"));
                if (isError(resultNode)) {
                    throw new ToolExecutionException(content);
                }
                return content;
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
