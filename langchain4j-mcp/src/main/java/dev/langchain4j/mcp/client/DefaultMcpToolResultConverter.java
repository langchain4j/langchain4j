package dev.langchain4j.mcp.client;

import dev.langchain4j.service.tool.ToolExecutionResult;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Default converter for MCP tool responses backed by {@code content[]}.
 * <p>
 * Supports text, images and embedded resources. Images and binary resources are
 * represented as base64 data URIs, while text resources retain their text. The
 * representations are joined with newline characters in server order and stored
 * in {@link ToolExecutionResult#resultText()}, not as multimodal message content.
 */
public class DefaultMcpToolResultConverter implements McpToolResultConverter {

    @Override
    public ToolExecutionResult convert(List<Map<String, Object>> content, boolean isError) {
        String resultText = content.stream().map(this::extractText).collect(Collectors.joining("\n"));

        return ToolExecutionResult.builder()
                .isError(isError)
                .resultText(resultText)
                .build();
    }

    private String extractText(Map<String, Object> contentItem) {
        Object type = contentItem.get("type");
        if ("text".equals(type)) {
            Object text = contentItem.get("text");
            return text == null ? "" : String.valueOf(text);
        } else if ("image".equals(type)) {
            Object data = contentItem.get("data");
            Object mimeType = contentItem.get("mimeType");
            if (data != null && mimeType != null) {
                return "data:" + mimeType + ";base64," + data;
            }
            return "[image: missing data or mimeType]";
        } else if ("resource".equals(type)) {
            Object resource = contentItem.get("resource");
            if (resource instanceof Map<?, ?> resourceContents) {
                Object text = resourceContents.get("text");
                if (text != null) {
                    return String.valueOf(text);
                }
                Object blob = resourceContents.get("blob");
                if (blob != null) {
                    Object mimeType = resourceContents.get("mimeType");
                    return "data:" + (mimeType == null ? "application/octet-stream" : mimeType) + ";base64," + blob;
                }
            }
            throw new IllegalArgumentException("Embedded resource must contain text or blob");
        } else {
            throw new RuntimeException("Unsupported content type: \"" + type + "\"");
        }
    }
}
