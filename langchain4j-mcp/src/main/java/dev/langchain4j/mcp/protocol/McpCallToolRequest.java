package dev.langchain4j.mcp.protocol;

import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.Internal;
import java.util.Map;

/**
 * Corresponds to the {@code CallToolRequest} type from the MCP schema.
 */
@Internal
public class McpCallToolRequest extends McpClientRequest {

    public McpCallToolRequest(Long id, String toolName, ObjectNode arguments) {
        this(id, toolName, arguments, null);
    }

    public McpCallToolRequest(Long id, String toolName, ObjectNode arguments, String progressToken) {
        super(id, McpClientMethod.TOOLS_CALL);
        McpCallToolParams params = new McpCallToolParams(toolName, arguments);
        if (progressToken != null) {
            params.setMeta(Map.of("progressToken", progressToken));
        }
        setParams(params);
    }
}
