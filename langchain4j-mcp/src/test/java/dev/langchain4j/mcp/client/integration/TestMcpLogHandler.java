package dev.langchain4j.mcp.client.integration;

import dev.langchain4j.mcp.client.logging.McpLogMessage;
import dev.langchain4j.mcp.client.logging.McpLogMessageHandler;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeoutException;

public class TestMcpLogHandler implements McpLogMessageHandler {

    private final List<McpLogMessage> receivedMessages = new CopyOnWriteArrayList<>();

    @Override
    public void handleLogMessage(McpLogMessage message) {
        receivedMessages.add(message);
    }

    public List<McpLogMessage> waitForAtLeastOneMessageAndGet(Duration timeout) throws TimeoutException {
        long start = System.currentTimeMillis();
        while (receivedMessages.isEmpty()) {
            if (System.currentTimeMillis() - start > timeout.toMillis()) {
                throw new TimeoutException("No message received within " + timeout);
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }
        return receivedMessages;
    }

    public void clearMessages() {
        receivedMessages.clear();
    }
}
