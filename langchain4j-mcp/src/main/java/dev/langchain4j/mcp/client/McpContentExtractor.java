package dev.langchain4j.mcp.client;

import dev.langchain4j.service.tool.ToolExecutionResult;
import java.util.List;
import java.util.Map;

/**
 * Extracts a {@link ToolExecutionResult} from an MCP tool response backed by
 * {@code CallToolResult.result.content[]}. It is not invoked when the MCP server
 * returns {@code structuredContent}, which is handled separately.
 * <p>
 * Content items are presented as plain maps, so implementations do not need a JSON library.
 * A text item, for example, looks like {@code {"type": "text", "text": "..."}}.
 * <p>
 * The default client only supports {@code structuredContent} and text content out of the box.
 * More specialized extraction strategies can be provided through
 * {@link DefaultMcpClient.Builder#contentExtractor(McpContentExtractor)}.
 */
@FunctionalInterface
public interface McpContentExtractor {

    /**
     * Extracts a {@link ToolExecutionResult} from {@code CallToolResult.result.content[]}.
     *
     * @param content the MCP tool result content array.
     * @param isError whether the tool response is marked as an application-level error.
     * @return the extracted {@link ToolExecutionResult}.
     */
    ToolExecutionResult extract(List<Map<String, Object>> content, boolean isError);
}
