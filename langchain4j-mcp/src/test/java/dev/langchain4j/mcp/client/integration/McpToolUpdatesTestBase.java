package dev.langchain4j.mcp.client.integration;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.McpClient;
import java.util.List;
import org.junit.jupiter.api.Test;

public abstract class McpToolUpdatesTestBase {

    static McpClient mcpClient;

    @Test
    public void verifyToolSpecifications() throws InterruptedException {
        List<ToolSpecification> tools = mcpClient.listTools();
        assertThat(tools).hasSize(1);
        assertThat(tools.get(0).name()).isEqualTo("registerNewTool");

        // now trigger the registration of a new tool on the server
        mcpClient.executeTool(ToolExecutionRequest.builder()
                .name("registerNewTool")
                .arguments("{}")
                .build());

        // check that the client has received a tool list notification and updated its tool list
        List<ToolSpecification> toolsAfterAddingANewTool = mcpClient.listTools();
        assertThat(toolsAfterAddingANewTool).hasSize(2);

        assertThat(findToolSpecificationByName(toolsAfterAddingANewTool, "registerNewTool"))
                .isNotNull();
        assertThat(findToolSpecificationByName(toolsAfterAddingANewTool, "toLowerCase"))
                .isNotNull();
    }

    ToolSpecification findToolSpecificationByName(List<ToolSpecification> toolSpecifications, String name) {
        return toolSpecifications.stream()
                .filter(t -> t.name().equals(name))
                .findFirst()
                .orElse(null);
    }
}
