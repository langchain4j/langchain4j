package dev.langchain4j.agentic.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.observability.AgentInvocation;
import dev.langchain4j.agentic.observability.AgentMonitor;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import dev.langchain4j.service.tool.ToolExecutionResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class McpInheritedListenerTest {

    @Test
    void monitor_of_the_root_records_the_mcp_subagent() {
        McpClient mcpClient = mock(McpClient.class);
        when(mcpClient.listTools())
                .thenReturn(List.of(ToolSpecification.builder()
                        .name("greet")
                        .description("Generate a greeting")
                        .parameters(JsonObjectSchema.builder()
                                .addProperty("name", new JsonStringSchema())
                                .build())
                        .build()));
        when(mcpClient.executeTool(any()))
                .thenReturn(ToolExecutionResult.builder()
                        .resultText("Hello, Mario!")
                        .isError(false)
                        .build());
        UntypedAgent greeter = McpAgent.builder(mcpClient)
                .inputKeys("name")
                .outputKey("greeting")
                .build();
        AgentMonitor monitor = new AgentMonitor();
        UntypedAgent sequence = AgenticServices.sequenceBuilder()
                .subAgents(greeter)
                .outputKey("greeting")
                .listener(monitor)
                .build();

        assertThat(sequence.invoke(Map.of("name", "Mario"))).isEqualTo("Hello, Mario!");

        AgentInvocation root = monitor.successfulExecutions().get(0).topLevelInvocations();
        assertThat(root.nestedInvocations()).hasSize(1);
        AgentInvocation invocation = root.nestedInvocations().get(0);
        assertThat(invocation.agent().name()).isEqualTo("greet");
        assertThat(invocation.output()).isEqualTo("Hello, Mario!");
    }
}
