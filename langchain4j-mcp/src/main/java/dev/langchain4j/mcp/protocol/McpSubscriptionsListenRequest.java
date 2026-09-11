package dev.langchain4j.mcp.protocol;

import dev.langchain4j.Internal;

/**
 * Corresponds to the {@code SubscriptionsListenRequest} type from the MCP schema.
 */
@Internal
public class McpSubscriptionsListenRequest extends McpClientRequest {
    public McpSubscriptionsListenRequest(Long id) {
        super(id, McpClientMethod.SUBSCRIPTIONS_LISTEN);
    }
}
