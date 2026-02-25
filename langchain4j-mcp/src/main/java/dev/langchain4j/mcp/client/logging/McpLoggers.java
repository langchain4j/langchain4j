package dev.langchain4j.mcp.client.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Central place for MCP-related loggers.
 */
public final class McpLoggers {

    public static final String DEFAULT_TRAFFIC_LOGGER_NAME = "MCP";

    private static final Logger DEFAULT_TRAFFIC_LOGGER = LoggerFactory.getLogger(DEFAULT_TRAFFIC_LOGGER_NAME);

    private McpLoggers() {}

    public static Logger traffic() {
        return DEFAULT_TRAFFIC_LOGGER;
    }
}
