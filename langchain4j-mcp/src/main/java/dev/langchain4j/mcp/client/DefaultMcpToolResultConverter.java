package dev.langchain4j.mcp.client;

import dev.langchain4j.service.tool.ToolExecutionResult;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Default converter for MCP tool responses backed by {@code content[]}.
 * <p>
 * It only supports {@code content} items of type {@code text}, joins multiple text
 * fragments with newline characters, and stores the result in
 * {@link ToolExecutionResult#resultText()}.
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
            if (resource instanceof Map) {
                Object text = ((Map<?, ?>) resource).get("text");
                return text == null ? "" : String.valueOf(text);
            }
            return "";
        } else {
            throw new RuntimeException("Unsupported content type: \"" + type + "\"");
        }
    }
}
