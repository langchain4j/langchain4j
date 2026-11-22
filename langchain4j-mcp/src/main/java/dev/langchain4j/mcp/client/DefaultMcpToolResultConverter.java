package dev.langchain4j.mcp.client;

import static dev.langchain4j.data.message.ImageContent.DetailLevel.AUTO;

import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.service.tool.ToolExecutionResult;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Default converter for MCP tool responses backed by {@code content[]}.
 * <p>
 * Supports text, images and embedded text resources. Images are returned as native
 * {@link ImageContent}, preserving mixed-content order. Text-only results are joined
 * with newlines so {@link ToolExecutionResult#resultText()} remains available.
 * Binary resources and other unsupported content types fail explicitly; applications
 * can provide a custom converter for them.
 */
public class DefaultMcpToolResultConverter implements McpToolResultConverter {

    @Override
    public ToolExecutionResult convert(List<Map<String, Object>> content, boolean isError) {
        List<Content> contents = content.stream().map(this::extractContent).toList();
        if (contents.stream().allMatch(TextContent.class::isInstance)) {
            String resultText =
                    contents.stream().map(item -> ((TextContent) item).text()).collect(Collectors.joining("\n"));
            return ToolExecutionResult.builder()
                    .isError(isError)
                    .resultText(resultText)
                    .build();
        }

        return ToolExecutionResult.builder()
                .isError(isError)
                .resultContents(contents)
                .build();
    }

    private Content extractContent(Map<String, Object> contentItem) {
        Object type = contentItem.get("type");
        if ("text".equals(type)) {
            Object text = contentItem.get("text");
            return TextContent.from(text == null ? "" : String.valueOf(text));
        } else if ("image".equals(type)) {
            Object data = contentItem.get("data");
            Object mimeType = contentItem.get("mimeType");
            if (!(data instanceof String base64) || !(mimeType instanceof String mime)) {
                throw new IllegalArgumentException("Image content requires string data and mimeType");
            }
            return ImageContent.from(base64, mime, AUTO);
        } else if ("resource".equals(type)) {
            Object resource = contentItem.get("resource");
            if (resource instanceof Map<?, ?> resourceContents) {
                Object text = resourceContents.get("text");
                if (text != null) {
                    return TextContent.from(String.valueOf(text));
                }
                Object blob = resourceContents.get("blob");
                if (blob != null) {
                    throw new IllegalArgumentException(
                            "Unsupported embedded binary resource: provide a custom McpToolResultConverter");
                }
            }
            throw new IllegalArgumentException("Embedded resource must contain text or blob");
        } else {
            throw new RuntimeException("Unsupported content type: \"" + type + "\"");
        }
    }
}
