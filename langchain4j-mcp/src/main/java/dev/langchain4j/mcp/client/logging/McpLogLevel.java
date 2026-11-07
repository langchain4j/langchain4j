package dev.langchain4j.mcp.client.logging;

import java.util.Locale;

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
            return valueOf(val.toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return null;
        }
    }
}
