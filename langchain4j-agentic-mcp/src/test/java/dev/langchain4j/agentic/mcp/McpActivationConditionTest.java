package dev.langchain4j.agentic.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.declarative.ActivationCondition;
import dev.langchain4j.agentic.declarative.McpClientAgent;
import dev.langchain4j.agentic.declarative.McpClientSupplier;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import dev.langchain4j.service.V;
import dev.langchain4j.service.tool.ToolExecutionResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class McpActivationConditionTest {

    static McpClient mcpClient;

    @BeforeEach
    void mockMcpClient() {
        mcpClient = mock(McpClient.class);
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
    }

    private static UntypedAgent sequenceOf(Object agent) {
        return AgenticServices.sequenceBuilder()
                .subAgents(agent)
                .outputKey("greeting")
                .build();
    }

    @Test
    void builder_sets_the_condition_and_the_tool_is_not_called_when_it_is_false() {
        UntypedAgent greeter = McpAgent.builder(mcpClient)
                .inputKeys("name")
                .outputKey("greeting")
                .activationCondition("greetings are enabled", scope -> scope.readState("enabled", false))
                .build();
        UntypedAgent sequence = sequenceOf(greeter);

        assertThat(((AgentInstance) sequence)
                        .subagents()
                        .get(0)
                        .activationCondition()
                        .description())
                .isEqualTo("greetings are enabled");
        assertThat(sequence.invoke(Map.of("name", "Mario", "enabled", false))).isNull();
        verify(mcpClient, never()).executeTool(any());

        assertThat(sequence.invoke(Map.of("name", "Mario", "enabled", true))).isEqualTo("Hello, Mario!");
    }

    public interface DeclarativeGreeter {

        @McpClientAgent(toolName = "greet", outputKey = "greeting")
        String greet(@V("name") String name);

        @McpClientSupplier
        static Object mcpClient() {
            return mcpClient;
        }

        @ActivationCondition(description = "greetings are enabled")
        static boolean enabled(@V("enabled") boolean enabled) {
            return enabled;
        }
    }

    @Test
    void declarative_mcp_agent_declares_its_condition() {
        UntypedAgent sequence = sequenceOf(DeclarativeGreeter.class);

        assertThat(((AgentInstance) sequence)
                        .subagents()
                        .get(0)
                        .activationCondition()
                        .description())
                .isEqualTo("greetings are enabled");
        assertThat(sequence.invoke(Map.of("name", "Mario", "enabled", false))).isNull();
        verify(mcpClient, never()).executeTool(any());

        assertThat(sequence.invoke(Map.of("name", "Mario", "enabled", true))).isEqualTo("Hello, Mario!");
    }
}
