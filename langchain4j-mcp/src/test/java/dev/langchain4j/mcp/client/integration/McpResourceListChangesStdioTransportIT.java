package dev.langchain4j.mcp.client.integration;

import static dev.langchain4j.mcp.client.integration.McpServerHelper.getJBangCommand;
import static dev.langchain4j.mcp.client.integration.McpServerHelper.getPathToScript;
import static dev.langchain4j.mcp.client.integration.McpServerHelper.skipTestsIfJbangNotAvailable;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.McpResource;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class McpResourceListChangesStdioTransportIT {

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
                        getPathToScript("resource_list_changes_mcp_server.java")))
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
    public void resourceListChangedNotification() {
        // initially, we have 1 resource
        List<McpResource> resources = mcpClient.listResources();
        assertThat(resources).hasSize(1);

        // register a new dynamic resource on the server
        mcpClient.executeTool(ToolExecutionRequest.builder()
                .name("registerNewResource")
                .arguments("{}")
                .build());

        // after the notification, the client should see the new resource
        List<McpResource> resourcesAfterAdd = mcpClient.listResources();
        assertThat(resourcesAfterAdd).hasSize(2);

        McpResource dynamicResource = resourcesAfterAdd.stream()
                .filter(r -> r.uri().equals("file:///dynamic"))
                .findFirst()
                .orElse(null);
        assertThat(dynamicResource).isNotNull();
        assertThat(dynamicResource.description()).isEqualTo("A dynamically added resource");
    }
}
