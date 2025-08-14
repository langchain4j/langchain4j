package dev.langchain4j.mcp.client.integration;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.docker.DockerMcpTransport;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProviderResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static dev.langchain4j.mcp.client.integration.McpServerHelper.getJBangCommand;
import static dev.langchain4j.mcp.client.integration.McpServerHelper.getPathToScript;
import static org.assertj.core.api.Assertions.assertThat;

class McpToolsDockerTransportIT {
    private static McpClient mcpClient;

    @BeforeAll
    static void setup() {
        McpTransport transport = new DockerMcpTransport.Builder()
                .image("mcp/time")
                .host("unix:///var/run/docker.sock")
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
    void verifyToolSpecifications() {
        ToolProviderResult toolProviderResult = obtainTools();

        Map<ToolSpecification, ToolExecutor> tools = toolProviderResult.tools();
        assertThat(tools).hasSize(2);

        ToolSpecification getCurrentTime = toolProviderResult.toolSpecificationByName("get_current_time");
        assertThat(getCurrentTime).isNotNull();
        assertThat(getCurrentTime.parameters().properties()).hasSize(1);
        assertThat(getCurrentTime.parameters().properties().get("timezone")).isInstanceOf(JsonStringSchema.class);

        ToolSpecification convertTime = toolProviderResult.toolSpecificationByName("convert_time");
        assertThat(convertTime).isNotNull();
        assertThat(convertTime.parameters().properties()).hasSize(3);
        assertThat(convertTime.parameters().properties().get("source_timezone")).isInstanceOf(JsonStringSchema.class);
        assertThat(convertTime.parameters().properties().get("target_timezone")).isInstanceOf(JsonStringSchema.class);
        assertThat(convertTime.parameters().properties().get("time")).isInstanceOf(JsonStringSchema.class);
    }

    @Test
    public void executeTool() {
        ToolProviderResult toolProviderResult = obtainTools();
        ToolExecutor executor = toolProviderResult.toolExecutorByName("get_current_time");
        ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                .name("get_current_time")
                .arguments("{\"timezone\": \"Paris\"}")
                .build();
        String toolExecutionResultString = executor.execute(toolExecutionRequest, null);
        assertThat(toolExecutionResultString).isNotNull();
        // TODO assert it's a date
    }

    ToolProviderResult obtainTools() {
        return McpToolProvider.builder().mcpClients(mcpClient).build().provideTools(null);
    }
}
