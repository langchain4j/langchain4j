package dev.langchain4j.mcp.client.integration;

import static dev.langchain4j.mcp.client.integration.McpServerHelper.getJBangCommand;
import static dev.langchain4j.mcp.client.integration.McpServerHelper.getPathToScript;
import static dev.langchain4j.mcp.client.integration.McpServerHelper.skipTestsIfJbangNotAvailable;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.McpPrompt;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class McpPromptSubscriptionsStdioTransportIT {

    static McpClient mcpClient;

    @BeforeAll
    static void setup() {
        skipTestsIfJbangNotAvailable();
        McpTransport transport = new StdioMcpTransport.Builder()
                .command(List.of(
                        getJBangCommand(),
                        "--quiet",
                        "--fresh",
                        "run",
                        getPathToScript("prompt_list_changes_mcp_server.java")))
                .logEvents(true)
                .build();
        mcpClient = new DefaultMcpClient.Builder()
                .transport(transport)
                .toolExecutionTimeout(Duration.ofSeconds(4))
                .build();
    }

    @AfterAll
    static void teardown() throws Exception {
        if (mcpClient != null) {
            mcpClient.close();
        }
    }

    @Test
    public void promptListChangedNotification() {
        // initially, we have 1 prompt
        List<McpPrompt> prompts = mcpClient.listPrompts();
        assertThat(prompts).hasSize(1);

        // register a new dynamic prompt on the server
        mcpClient.executeTool(ToolExecutionRequest.builder()
                .name("registerNewPrompt")
                .arguments("{}")
                .build());

        // after the notification, the client should see the new prompt
        List<McpPrompt> promptsAfterAdd = mcpClient.listPrompts();
        assertThat(promptsAfterAdd).hasSize(2);

        McpPrompt dynamicPrompt = promptsAfterAdd.stream()
                .filter(p -> p.name().equals("dynamicPrompt"))
                .findFirst()
                .orElse(null);
        assertThat(dynamicPrompt).isNotNull();
        assertThat(dynamicPrompt.description()).isEqualTo("A dynamically added prompt");
    }
}
