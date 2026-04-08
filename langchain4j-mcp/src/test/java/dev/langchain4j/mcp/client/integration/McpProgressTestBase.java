package dev.langchain4j.mcp.client.integration;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.progress.McpProgressHandler;
import dev.langchain4j.mcp.client.progress.McpProgressNotification;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public abstract class McpProgressTestBase {

    static McpClient mcpClient;
    static TestProgressHandler progressHandler;

    @BeforeEach
    public void clearMessages() {
        progressHandler.clear();
    }

    @Test
    public void receiveProgressNotifications() throws TimeoutException {
        String result = mcpClient
                .executeTool(ToolExecutionRequest.builder()
                        .arguments("{}")
                        .name("progressOperation")
                        .build())
                .resultText();
        assertThat(result).isEqualTo("done");

        List<TestProgressHandler.ProgressEvent> events = progressHandler.waitForMessages(3, Duration.ofSeconds(10));
        assertThat(events).hasSize(3);

        assertThat(events.get(0).progress()).isEqualTo(1);
        assertThat(events.get(0).total()).isEqualTo(3);
        assertThat(events.get(0).message()).isEqualTo("Step 1 of 3");

        assertThat(events.get(2).progress()).isEqualTo(3);
    }

    public static class TestProgressHandler implements McpProgressHandler {

        public record ProgressEvent(double progress, double total, String message) {}

        private final List<ProgressEvent> events = new CopyOnWriteArrayList<>();

        @Override
        public void onProgress(McpProgressNotification notification) {
            events.add(new ProgressEvent(
                    notification.progress(),
                    notification.total() != null ? notification.total() : 0,
                    notification.message()));
        }

        public List<ProgressEvent> waitForMessages(int count, Duration timeout) throws TimeoutException {
            long start = System.currentTimeMillis();
            while (events.size() < count) {
                if (System.currentTimeMillis() - start > timeout.toMillis()) {
                    throw new TimeoutException("Expected " + count + " events but got " + events.size());
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
            }
            return events;
        }

        public void clear() {
            events.clear();
        }
    }
}
