package dev.langchain4j.mcp.protocol;

import dev.langchain4j.Internal;
import java.util.Map;

/**
 * Corresponds to the {@code CallToolRequest} type from the MCP schema.
 */
@Internal
public class McpCallToolRequest extends McpClientRequest {

    public McpCallToolRequest(Long id, String toolName, Map<String, Object> arguments) {
        this(id, toolName, arguments, null);
    }

    public McpCallToolRequest(Long id, String toolName, Map<String, Object> arguments, String progressToken) {
        super(id, McpClientMethod.TOOLS_CALL);
        McpCallToolParams params = new McpCallToolParams(toolName, arguments);
        if (progressToken != null) {
            params.setMeta(Map.of("progressToken", progressToken));
        }
        setParams(params);
    }
}
