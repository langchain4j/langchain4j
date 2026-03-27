package dev.langchain4j.mcp.protocol;

import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.Internal;

/**
 * Corresponds to the {@code CallToolRequest} type from the MCP schema.
 */
@Internal
public class McpCallToolRequest extends McpClientRequest {

    public McpCallToolRequest(Long id, String toolName, ObjectNode arguments) {
        super(id, McpClientMethod.TOOLS_CALL);
        setParams(new McpCallToolParams(toolName, arguments));
    }
}
