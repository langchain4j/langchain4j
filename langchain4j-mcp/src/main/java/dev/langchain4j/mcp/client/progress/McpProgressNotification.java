package dev.langchain4j.mcp.client.progress;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Objects;

/**
 * Represents a progress notification received from an MCP server,
 * sent in response to a request that included a progress token.
 */
public class McpProgressNotification {

    private final String progressToken;
    private final double progress;
    private final Double total;
    private final String message;

    public McpProgressNotification(String progressToken, double progress, Double total, String message) {
        this.progressToken = progressToken;
        this.progress = progress;
        this.total = total;
        this.message = message;
    }

    /**
     * Parses a McpProgressNotification from the contents of the 'params' object
     * inside a 'notifications/progress' message.
     */
    public static McpProgressNotification fromJson(JsonNode params) {
        String progressToken = params.path("progressToken").asText(null);
        double progress = params.path("progress").asDouble();
        Double total = params.has("total") ? params.get("total").asDouble() : null;
        String message = params.has("message") ? params.get("message").asText() : null;
        return new McpProgressNotification(progressToken, progress, total, message);
    }

    public String progressToken() {
        return progressToken;
    }

    public double progress() {
        return progress;
    }

    public Double total() {
        return total;
    }

    public String message() {
        return message;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        McpProgressNotification that = (McpProgressNotification) obj;
        return Double.compare(this.progress, that.progress) == 0
                && Objects.equals(this.progressToken, that.progressToken)
                && Objects.equals(this.total, that.total)
                && Objects.equals(this.message, that.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(progressToken, progress, total, message);
    }

    @Override
    public String toString() {
        return "McpProgressNotification["
                + "progressToken=" + progressToken
                + ", progress=" + progress
                + ", total=" + total
                + ", message=" + message
                + ']';
    }
}
