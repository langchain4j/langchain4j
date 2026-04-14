package dev.langchain4j.mcp.client;

import com.fasterxml.jackson.databind.JsonNode;
import dev.langchain4j.service.tool.ToolExecutionResult;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Default extractor for MCP tool responses backed by {@code content[]}.
 * <p>
 * This implementation preserves the existing client behavior: it only supports
 * {@code content} items of type {@code text}, joins multiple text fragments with
 * newline characters, and stores the result in {@link ToolExecutionResult#resultText()}.
 * It does not apply to responses that contain {@code structuredContent}.
 */
public class DefaultMcpToolResultExtractor implements McpToolResultExtractor {

    @Override
    public ToolExecutionResult extract(JsonNode content, boolean isError) {
        String resultText = StreamSupport.stream(content.spliterator(), false)
                .map(this::extractText)
                .collect(Collectors.joining("\n"));

        return ToolExecutionResult.builder()
                .isError(isError)
                .resultText(resultText)
                .build();
    }

    private String extractText(JsonNode contentItem) {
        if (!contentItem.get("type").asText().equals("text")) {
            throw new RuntimeException(
                    "Unsupported content type: " + contentItem.get("type").asText());
        }
        return contentItem.get("text").asText();
    }
}
