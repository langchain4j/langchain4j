package dev.langchain4j.mcp.client.transport.docker;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.exception.ToolExecutionException;
import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProviderResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@EnabledIf("dockerSocketExists")
class DockerMcpTransportIT {
    private static McpClient mcpClient;

    @BeforeAll
    static void setup() {
        McpTransport transport = new DockerMcpTransport.Builder()
                .image("mcp/time")
                .dockerHost("unix:///var/run/docker.sock")
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

    static boolean dockerSocketExists() {
        return Files.exists(Path.of("/var/run/docker.sock"));
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
                .arguments("{\"timezone\": \"Europe/Paris\"}")
                .build();
        String toolExecutionResultString = executor.execute(toolExecutionRequest, null);
        assertThat(toolExecutionResultString).isNotNull();
        assertThat(toolExecutionResultString).contains("timezone", "Europe/Paris", "datetime");
        String currentDate = LocalDate.now(ZoneId.of("Europe/Paris")).toString();
        assertThat(toolExecutionResultString).contains(currentDate);
    }

    @Test
    public void executeABiggerTool() {
        McpTransport transport = new DockerMcpTransport.Builder()
                .image("mcp/everything")
                .dockerHost("unix:///var/run/docker.sock")
                .logEvents(true)
                .build();
        McpClient mcpClient = new DefaultMcpClient.Builder()
                .transport(transport)
                .toolExecutionTimeout(Duration.ofSeconds(4))
                .build();

        ToolProviderResult toolProviderResult =  McpToolProvider.builder().mcpClients(mcpClient).build().provideTools(null);
        ToolExecutor executor = toolProviderResult.toolExecutorByName("add");
        ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                .name("add")
                .arguments("{\"a\": 5, \"b\": 6}")
                .build();
        String toolExecutionResultString = executor.execute(toolExecutionRequest, null);
        assertThat(toolExecutionResultString).isNotNull();
        assertThat(toolExecutionResultString).contains("11");
    }

    @Test
    public void executeToolWithWrongArgument() {
        ToolProviderResult toolProviderResult = obtainTools();
        ToolExecutor executor = toolProviderResult.toolExecutorByName("get_current_time");
        ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                .name("get_current_time")
                .arguments("{\"timezone\": \"unknown\"}")
                .build();
        assertThrows(ToolExecutionException.class, () -> executor.execute(toolExecutionRequest, null));
    }

    ToolProviderResult obtainTools() {
        return McpToolProvider.builder().mcpClients(mcpClient).build().provideTools(null);
    }
}
