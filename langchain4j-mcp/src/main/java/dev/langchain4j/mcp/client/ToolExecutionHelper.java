package dev.langchain4j.mcp.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ToolExecutionHelper {

    private static final Logger log = LoggerFactory.getLogger(ToolExecutionHelper.class);
    private static final String EXECUTION_ERROR_MESSAGE = "There was an error executing the tool";

    /**
     * Extracts a response from a CallToolResult message. This may be an error response.
     */
    static String extractResult(JsonNode result) {
        if (result.has("result")) {
            JsonNode resultNode = result.get("result");
            if (resultNode.has("content")) {
                String content = extractSuccessfulResult((ArrayNode) resultNode.get("content"));
                boolean isError = false;
                if (resultNode.has("isError")) {
                    isError = resultNode.get("isError").asBoolean();
                }
                if (isError) {
                    content = String.format(EXECUTION_ERROR_MESSAGE + ". The tool returned: %s", content);
                }
                return content;
            } else {
                log.warn("Result does not contain 'content' element: {}", result);
                return EXECUTION_ERROR_MESSAGE;
            }
        } else {
            if (result.has("error")) {
                return extractError(result.get("error"));
            }
            log.warn("Result contains neither 'result' nor 'error' element: {}", result);
            return EXECUTION_ERROR_MESSAGE;
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

    private static String extractError(JsonNode errorNode) {
        String errorMessage = "";
        if (errorNode.get("message") != null) {
            errorMessage = errorNode.get("message").asText("");
        }
        Integer errorCode = null;
        if (errorNode.get("code") != null) {
            errorCode = errorNode.get("code").asInt();
        }
        log.warn("Result contains an error: {}, code: {}", errorMessage, errorCode);
        return String.format(EXECUTION_ERROR_MESSAGE + ". Message: %s. Code: %s", errorMessage, errorCode);
    }
}
