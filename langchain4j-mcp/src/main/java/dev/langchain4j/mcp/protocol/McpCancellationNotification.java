package dev.langchain4j.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.langchain4j.Internal;
import java.util.HashMap;
import java.util.Map;
import org.jspecify.annotations.NonNull;

@Internal
public class McpCancellationNotification extends McpClientMessage {

    @JsonInclude
    private Map<String, Object> params;

    public McpCancellationNotification(@NonNull Long requestId, String reason) {
        super(null, McpClientMethod.NOTIFICATION_CANCELLED);
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
