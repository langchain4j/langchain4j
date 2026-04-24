package dev.langchain4j.agentic.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentRequest;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import dev.langchain4j.service.V;
import dev.langchain4j.service.tool.ToolExecutionResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class McpAgentTest {

    private static McpClient mockMcpClient(String toolName, String description, String... inputKeys) {
        McpClient mcpClient = mock(McpClient.class);

        JsonObjectSchema.Builder schemaBuilder = JsonObjectSchema.builder();
        for (String key : inputKeys) {
            schemaBuilder.addProperty(key, new JsonStringSchema());
        }

        ToolSpecification toolSpec = ToolSpecification.builder()
                .name(toolName)
                .description(description)
                .parameters(schemaBuilder.build())
                .build();

        when(mcpClient.listTools()).thenReturn(List.of(toolSpec));
        return mcpClient;
    }

    private static void mockToolResult(McpClient mcpClient, String resultText) {
        ToolExecutionResult result = ToolExecutionResult.builder()
                .resultText(resultText)
                .isError(false)
                .build();
        when(mcpClient.executeTool(any())).thenReturn(result);
    }

    private static void mockToolError(McpClient mcpClient, String errorText) {
        ToolExecutionResult result = ToolExecutionResult.builder()
                .resultText(errorText)
                .isError(true)
                .build();
        when(mcpClient.executeTool(any())).thenReturn(result);
    }

    @Test
    void untyped_mcp_agent_derives_input_keys_from_schema() {
        McpClient mcpClient = mockMcpClient("translate", "Translate text to a target language", "text", "language");
        mockToolResult(mcpClient, "Bonjour le monde");

        UntypedAgent translator = McpAgent.builder(mcpClient)
                .outputKey("translation")
                .build();

        Object result = translator.invoke(Map.of("text", "Hello world", "language", "French"));

        assertThat(result).isEqualTo("Bonjour le monde");

        ArgumentCaptor<ToolExecutionRequest> captor = ArgumentCaptor.forClass(ToolExecutionRequest.class);
        verify(mcpClient).executeTool(captor.capture());

        ToolExecutionRequest request = captor.getValue();
        assertThat(request.name()).isEqualTo("translate");
        assertThat(request.arguments()).contains("Hello world");
        assertThat(request.arguments()).contains("French");
    }

    @Test
    void untyped_mcp_agent_with_explicit_input_keys() {
        McpClient mcpClient = mockMcpClient("greet", "Generate a greeting", "name", "language");
        mockToolResult(mcpClient, "Hello, Mario!");

        UntypedAgent greeter = McpAgent.builder(mcpClient)
                .inputKeys("name")
                .outputKey("greeting")
                .build();

        Object result = greeter.invoke(Map.of("name", "Mario"));

        assertThat(result).isEqualTo("Hello, Mario!");
    }

    public interface TypedTranslator {

        @Agent(description = "Translate text")
        String translate(@V("text") String text, @V("language") String language);
    }

    @Test
    void typed_mcp_agent() {
        McpClient mcpClient = mockMcpClient("translate", "Translate text to a target language", "text", "language");
        mockToolResult(mcpClient, "Hola mundo");

        TypedTranslator translator = McpAgent.builder(mcpClient, TypedTranslator.class)
                .outputKey("translation")
                .build();

        String result = translator.translate("Hello world", "Spanish");

        assertThat(result).isEqualTo("Hola mundo");

        ArgumentCaptor<ToolExecutionRequest> captor = ArgumentCaptor.forClass(ToolExecutionRequest.class);
        verify(mcpClient).executeTool(captor.capture());

        ToolExecutionRequest request = captor.getValue();
        assertThat(request.name()).isEqualTo("translate");
        assertThat(request.arguments()).contains("Hello world");
        assertThat(request.arguments()).contains("Spanish");
    }

    @Test
    void mcp_agent_tool_not_found_throws_exception() {
        McpClient mcpClient = mock(McpClient.class);

        ToolSpecification otherTool = ToolSpecification.builder()
                .name("other_tool")
                .description("Some other tool")
                .build();
        when(mcpClient.listTools()).thenReturn(List.of(otherTool));

        assertThatThrownBy(() -> McpAgent.builder(mcpClient)
                .toolName("nonexistent_tool")
                .build())
                .isInstanceOf(dev.langchain4j.agentic.planner.AgenticSystemConfigurationException.class)
                .hasMessageContaining("nonexistent_tool")
                .hasMessageContaining("not found");
    }

    @Test
    void mcp_agent_tool_execution_error_throws_exception() {
        McpClient mcpClient = mockMcpClient("fail_tool", "A tool that fails", "input");
        mockToolError(mcpClient, "Something went wrong");

        UntypedAgent agent = McpAgent.builder(mcpClient)
                .outputKey("result")
                .build();

        assertThatThrownBy(() -> agent.invoke(Map.of("input", "test")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("MCP tool execution failed");
    }

    @Test
    void mcp_agent_selects_tool_by_name() {
        McpClient mcpClient = mock(McpClient.class);

        ToolSpecification tool1 = ToolSpecification.builder()
                .name("tool_one")
                .description("First tool")
                .parameters(JsonObjectSchema.builder()
                        .addProperty("a", new JsonStringSchema())
                        .build())
                .build();

        ToolSpecification tool2 = ToolSpecification.builder()
                .name("tool_two")
                .description("Second tool")
                .parameters(JsonObjectSchema.builder()
                        .addProperty("b", new JsonStringSchema())
                        .build())
                .build();

        when(mcpClient.listTools()).thenReturn(List.of(tool1, tool2));
        mockToolResult(mcpClient, "result from tool two");

        UntypedAgent agent = McpAgent.builder(mcpClient)
                .toolName("tool_two")
                .outputKey("result")
                .build();

        Object result = agent.invoke(Map.of("b", "value"));
        assertThat(result).isEqualTo("result from tool two");
    }

    @Test
    void mcp_agent_with_listener() {
        McpClient mcpClient = mockMcpClient("greet", "Generate a greeting", "name");
        mockToolResult(mcpClient, "Hello, World!");

        class TestListener implements AgentListener {
            String requestedName;

            @Override
            public void beforeAgentInvocation(AgentRequest request) {
                requestedName = (String) request.inputs().get("name");
            }
        }

        TestListener listener = new TestListener();

        UntypedAgent greeter = McpAgent.builder(mcpClient)
                .listener(listener)
                .inputKeys("name")
                .outputKey("greeting")
                .build();

        UntypedAgent sequence = AgenticServices.sequenceBuilder()
                .subAgents(greeter)
                .outputKey("greeting")
                .build();

        ResultWithAgenticScope<String> result = sequence.invokeWithAgenticScope(Map.of("name", "World"));
        assertThat(result.result()).isEqualTo("Hello, World!");
        assertThat(listener.requestedName).isEqualTo("World");
    }

    @Test
    void mcp_agent_topology_is_non_ai_agent() {
        McpClient mcpClient = mockMcpClient("tool", "A tool", "input");

        UntypedAgent agent = McpAgent.builder(mcpClient)
                .outputKey("result")
                .build();

        assertThat(agent).isInstanceOf(McpClientInstance.class);
        McpClientInstance mcpInstance = (McpClientInstance) agent;
        assertThat(mcpInstance.topology()).isEqualTo(
                dev.langchain4j.agentic.planner.AgenticSystemTopology.NON_AI_AGENT);
    }

    @Test
    void mcp_agent_in_sequence_workflow() {
        McpClient mcpClient1 = mockMcpClient("generate", "Generate content", "topic");
        mockToolResult(mcpClient1, "A story about dragons");

        McpClient mcpClient2 = mockMcpClient("uppercase", "Uppercase text", "story");
        mockToolResult(mcpClient2, "A STORY ABOUT DRAGONS");

        UntypedAgent generator = McpAgent.builder(mcpClient1)
                .inputKeys("topic")
                .outputKey("story")
                .build();

        UntypedAgent uppercaser = McpAgent.builder(mcpClient2)
                .inputKeys("story")
                .outputKey("story")
                .build();

        UntypedAgent pipeline = AgenticServices.sequenceBuilder()
                .subAgents(generator, uppercaser)
                .outputKey("story")
                .build();

        ResultWithAgenticScope<String> result = pipeline.invokeWithAgenticScope(Map.of("topic", "dragons"));
        assertThat(result.result()).isEqualTo("A STORY ABOUT DRAGONS");

        AgenticScope scope = result.agenticScope();
        assertThat(scope.readState("story")).isEqualTo("A STORY ABOUT DRAGONS");
    }

    @Test
    void mcp_agent_with_no_parameters() {
        McpClient mcpClient = mock(McpClient.class);

        ToolSpecification toolSpec = ToolSpecification.builder()
                .name("get_time")
                .description("Get current time")
                .build();

        when(mcpClient.listTools()).thenReturn(List.of(toolSpec));
        mockToolResult(mcpClient, "2024-01-01T00:00:00Z");

        UntypedAgent agent = McpAgent.builder(mcpClient)
                .outputKey("time")
                .build();

        Object result = agent.invoke(Map.of());
        assertThat(result).isEqualTo("2024-01-01T00:00:00Z");
    }

    public interface Calculator {

        @Agent
        double calculate(@V("expression") String expression);
    }

    @Test
    void mcp_agent_returns_numeric_result() {
        McpClient mcpClient = mockMcpClient("calculate", "Calculate something", "expression");
        mockToolResult(mcpClient, "42.5");

        Calculator calculator = McpAgent.builder(mcpClient, Calculator.class)
                .outputKey("result")
                .build();

        double result = calculator.calculate("21 * 2 + 0.5");
        assertThat(result).isEqualTo(42.5);
    }
}
