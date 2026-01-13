package dev.langchain4j.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.langchain4j.Internal;
import java.util.HashMap;
import java.util.Map;
import org.jspecify.annotations.NonNull;

@Internal
public class McpCancellationNotification extends McpJsonRpcMessage {

    @JsonInclude
    public final McpClientMethod method = McpClientMethod.NOTIFICATION_CANCELLED;

    @JsonInclude
    private Map<String, Object> params;

    public McpCancellationNotification(@NonNull Long requestId, String reason) {
        super(null);
        this.params = new HashMap<>();
        this.params.put("requestId", requestId);
        if (reason != null) {
            this.params.put("reason", reason);
        }
    }

    public Map<String, Object> getParams() {
        return params;
    }
}
