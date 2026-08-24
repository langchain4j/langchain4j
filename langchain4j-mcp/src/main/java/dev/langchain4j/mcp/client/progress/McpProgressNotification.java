package dev.langchain4j.mcp.client.progress;

import com.fasterxml.jackson.databind.JsonNode;
import dev.langchain4j.mcp.client.McpJsonConversions;
import java.util.Map;
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
     * inside a 'notifications/progress' message, presented as plain values.
     */
    public static McpProgressNotification fromMap(Map<String, Object> params) {
        Object progressToken = params.get("progressToken");
        Object progress = params.get("progress");
        Object total = params.get("total");
        Object message = params.get("message");
        Double totalValue = toDouble(total);
        Double progressValue = toDouble(progress);
        return new McpProgressNotification(
                progressToken == null ? null : String.valueOf(progressToken),
                progressValue == null ? 0d : progressValue,
                totalValue,
                message == null ? null : String.valueOf(message));
    }

    /**
     * Mirrors JsonNode.asDouble(), which also parses numeric strings.
     */
    private static Double toDouble(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof String) {
            try {
                return Double.parseDouble((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * @deprecated use {@link #fromMap(Map)}, which does not expose Jackson types.
     */
    @Deprecated(since = "1.20.0", forRemoval = true)
    public static McpProgressNotification fromJson(JsonNode params) {
        return fromMap(McpJsonConversions.toMap(params));
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
