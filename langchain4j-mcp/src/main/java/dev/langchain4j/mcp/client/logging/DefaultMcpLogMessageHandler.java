package dev.langchain4j.mcp.client.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The default implementation of {@link McpLogMessageHandler} that simply forwards
 * MCP log notifications to the SLF4J logger.
 */
public class DefaultMcpLogMessageHandler implements McpLogMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(DefaultMcpLogMessageHandler.class);

    @Override
    public void handleLogMessage(McpLogMessage message) {
        if (message.level() == null) {
            log.warn("Received MCP log message with unknown level: {}", message.data());
            return;
        }
        switch (message.level()) {
            case DEBUG -> log.debug("MCP logger: {}: {}", message.logger(), message.data());
            case INFO, NOTICE -> log.info("MCP logger: {}: {}", message.logger(), message.data());
            case WARNING -> log.warn("MCP logger: {}: {}", message.logger(), message.data());
            case ERROR, CRITICAL, ALERT, EMERGENCY -> log.error("MCP logger: {}: {}", message.logger(), message.data());
        }
    }
}
