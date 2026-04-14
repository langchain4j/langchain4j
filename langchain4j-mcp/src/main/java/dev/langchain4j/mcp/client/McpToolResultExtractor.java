package dev.langchain4j.mcp.client;

import com.fasterxml.jackson.databind.node.ArrayNode;
import dev.langchain4j.service.tool.ToolExecutionResult;

/**
 * Extracts a {@link ToolExecutionResult} from a tool response {@code content[]} array.
 * <p>
 * This extension point is only used for ordinary MCP tool responses that return
 * {@code CallToolResult.result.content[]}. It is not invoked when the MCP server
 * returns {@code structuredContent}, which is handled separately.
 * <p>
 * This interface is not a general-purpose MCP content parsing framework. The default
 * client only supports {@code structuredContent} and text content out of the box.
 * More specialized extraction strategies can be provided through
 * {@link DefaultMcpClient.Builder#toolResultExtractor(McpToolResultExtractor)}.
 */
public interface McpToolResultExtractor {

    /**
     * Extracts a {@link ToolExecutionResult} from {@code CallToolResult.result.content[]}.
     *
     * @param content the MCP tool result content array.
     * @param isError whether the tool response is marked as an application-level error.
     * @return the extracted {@link ToolExecutionResult}.
     */
    ToolExecutionResult extract(ArrayNode content, boolean isError);
}
