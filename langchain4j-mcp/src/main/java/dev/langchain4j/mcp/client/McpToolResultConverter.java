package dev.langchain4j.mcp.client;

import dev.langchain4j.service.tool.ToolExecutionResult;
import java.util.List;
import java.util.Map;

/**
 * Converts the content blocks of an MCP {@code CallToolResult} into the
 * {@link ToolExecutionResult} the model sees. It is not invoked when the MCP server
 * returns {@code structuredContent}, which is handled separately.
 * <p>
 * Content items are presented as plain maps, so implementations do not need a JSON library.
 * A text item, for example, looks like {@code {"type": "text", "text": "..."}}.
 * <p>
 * The default client only supports {@code structuredContent} and text content out of the box.
 * More specialized conversion strategies can be provided through
 * {@link DefaultMcpClient.Builder#toolResultConverter(McpToolResultConverter)}.
 */
@FunctionalInterface
public interface McpToolResultConverter {

    /**
     * @param content the content blocks of {@code CallToolResult}.
     * @param isError whether the tool response is marked as an application-level error.
     * @return the result to hand back to the caller, whose text is what the model sees.
     */
    ToolExecutionResult convert(List<Map<String, Object>> content, boolean isError);
}
