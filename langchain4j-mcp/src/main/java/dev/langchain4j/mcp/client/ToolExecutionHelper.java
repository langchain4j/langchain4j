package dev.langchain4j.mcp.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

class ToolExecutionHelper {

    /**
     * Extracts a single string as a result from the 'content' element of
     * a CallToolResult message. Only supports String contents, if it finds any
     * other content types, it throws an exception.
     */
    static String extractResult(ArrayNode contents) {
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
}
