package dev.langchain4j.mcp.client.integration;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.logging.McpLogLevel;
import dev.langchain4j.mcp.client.logging.McpLogMessage;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public abstract class McpLoggingTestBase {

    static McpClient mcpClient;
    static TestMcpLogHandler logMessageHandler;

    @BeforeEach
    public void clearMessages() {
        logMessageHandler.clearMessages();
    }

    @Test
    public void receiveInfoLogMessage() throws TimeoutException {
        String result = mcpClient
                .executeTool(ToolExecutionRequest.builder()
                        .arguments("{}")
                        .name("info")
                        .build())
                .resultText();
        assertThat(result).isEqualTo("ok");
        List<McpLogMessage> receivedMessages = logMessageHandler.waitForAtLeastOneMessageAndGet(Duration.ofSeconds(10));
        assertThat(receivedMessages).hasSize(1);
        McpLogMessage message = receivedMessages.get(0);
        assertThat(message.level()).isEqualTo(McpLogLevel.INFO);
        assertThat(message.logger()).isEqualTo("tool:info");
        assertThat(message.data().asText()).isEqualTo("HELLO. data: 1234");
    }

    @Test
    public void receiveDebugLogMessage() throws TimeoutException {
        String result = mcpClient
                .executeTool(ToolExecutionRequest.builder()
                        .arguments("{}")
                        .name("debug")
                        .build())
                .resultText();
        assertThat(result).isEqualTo("ok");
        List<McpLogMessage> receivedMessages = logMessageHandler.waitForAtLeastOneMessageAndGet(Duration.ofSeconds(10));
        assertThat(receivedMessages).hasSize(1);
        McpLogMessage message = receivedMessages.get(0);
        assertThat(message.level()).isEqualTo(McpLogLevel.DEBUG);
        assertThat(message.logger()).isEqualTo("tool:debug");
        assertThat(message.data().asText()).isEqualTo("HELLO DEBUG");
    }
}
