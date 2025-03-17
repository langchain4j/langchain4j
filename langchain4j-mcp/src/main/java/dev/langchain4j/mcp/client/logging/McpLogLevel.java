package dev.langchain4j.mcp.client.logging;

/**
 * Log level of an MCP log message.
 */
public enum McpLogLevel {
    DEBUG,
    INFO,
    NOTICE,
    WARNING,
    ERROR,
    CRITICAL,
    ALERT,
    EMERGENCY;

    public static McpLogLevel from(String val) {
        if (val == null || val.isBlank()) {
            return null;
        }
        try {
            return valueOf(val.toUpperCase());
        } catch (Exception e) {
            return null;
        }
    }
}
