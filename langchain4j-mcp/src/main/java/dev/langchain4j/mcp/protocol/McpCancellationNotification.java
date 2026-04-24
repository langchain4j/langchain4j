package dev.langchain4j.mcp.protocol;

import dev.langchain4j.Internal;
import org.jspecify.annotations.NonNull;

/**
 * Corresponds to the {@code CancelledNotification} type from the MCP schema.
 */
@Internal
public class McpCancellationNotification extends McpClientNotification {

    public McpCancellationNotification(@NonNull Long requestId, String reason) {
        super(McpClientMethod.NOTIFICATION_CANCELLED);
        setParams(new McpCancellationParams(requestId, reason));
    }
}
