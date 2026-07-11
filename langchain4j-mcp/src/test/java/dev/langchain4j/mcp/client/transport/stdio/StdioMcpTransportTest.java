package dev.langchain4j.mcp.client.transport.stdio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import dev.langchain4j.mcp.client.transport.McpOperationHandler;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

class StdioMcpTransportTest {

    /**
     * When {@link StdioMcpTransport#start} is called again (as happens during reconnection triggered
     * by the health check), the previous subprocess must be destroyed instead of leaked.
     */
    @Test
    @DisabledOnOs(OS.WINDOWS)
    void restarting_should_destroy_the_previous_process() throws IOException, InterruptedException {
        // 'cat' with no arguments reads stdin indefinitely, so the process stays alive until destroyed
        StdioMcpTransport transport = new StdioMcpTransport.Builder()
                .command(List.of("cat"))
                .environment(Map.of())
                .build();

        McpOperationHandler messageHandler = mock(McpOperationHandler.class);

        transport.start(messageHandler);
        Process firstProcess = transport.getProcess();
        assertThat(firstProcess.isAlive()).isTrue();

        // Restart (simulating a reconnection)
        transport.start(messageHandler);
        Process secondProcess = transport.getProcess();

        // The previous process must have been torn down, not leaked
        assertThat(secondProcess).isNotSameAs(firstProcess);
        assertThat(firstProcess.waitFor(5, java.util.concurrent.TimeUnit.SECONDS))
                .as("the previous process should have been destroyed on restart")
                .isTrue();
        assertThat(firstProcess.isAlive()).isFalse();
        assertThat(secondProcess.isAlive()).isTrue();

        transport.close();
    }
}
