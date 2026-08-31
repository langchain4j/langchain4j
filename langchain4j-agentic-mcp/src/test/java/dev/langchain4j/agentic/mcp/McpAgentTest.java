package dev.langchain4j.agentic.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.agent.MissingArgumentException;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentRequest;
import dev.langchain4j.agentic.planner.AgentArgument;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.scope.AgentInvocation;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.agentic.supervisor.SupervisorPlanner;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.model.chat.request.json.JsonAnyOfSchema;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonBooleanSchema;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonNullSchema;
import dev.langchain4j.model.chat.request.json.JsonNumberSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonRawSchema;
import dev.langchain4j.model.chat.request.json.JsonReferenceSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import dev.langchain4j.service.V;
import dev.langchain4j.service.tool.ToolExecutionResult;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
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

        UntypedAgent translator =
                McpAgent.builder(mcpClient).outputKey("translation").build();

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
    void untyped_mcp_agent_preserves_argument_types_from_schema() {
        McpClient mcpClient = mock(McpClient.class);
        ToolSpecification toolSpec = ToolSpecification.builder()
                .name("search_records")
                .description("Search records")
                .parameters(JsonObjectSchema.builder()
                        .addProperty("recordType", new JsonStringSchema())
                        .addProperty(
                                "fields",
                                JsonArraySchema.builder()
                                        .description("Fields to include in the search")
                                        .items(new JsonStringSchema())
                                        .build())
                        .addProperty("limit", new JsonIntegerSchema())
                        .addProperty(
                                "filters",
                                JsonObjectSchema.builder()
                                        .addStringProperty("status")
                                        .build())
                        .build())
                .build();
        when(mcpClient.listTools()).thenReturn(List.of(toolSpec));

        UntypedAgent agent = McpAgent.builder(mcpClient).build();

        List<AgentArgument> arguments = ((McpClientInstance) agent).arguments();
        assertThat(arguments)
                .extracting(AgentArgument::name)
                .containsExactly("recordType", "fields", "limit", "filters");
        assertThat(arguments.get(0).type()).isEqualTo(String.class);
        assertThat(arguments.get(2).type()).isEqualTo(Integer.class);
        assertThat(arguments.get(1).description()).isEqualTo("Fields to include in the search");
        assertThat(((McpClientInstance) agent).inputDescriptions())
                .containsEntry("fields", "Fields to include in the search");

        ParameterizedType fieldsType = (ParameterizedType) arguments.get(1).type();
        assertThat(fieldsType.getRawType()).isEqualTo(List.class);
        assertThat(fieldsType.getActualTypeArguments()).containsExactly(String.class);

        ParameterizedType filtersType = (ParameterizedType) arguments.get(3).type();
        assertThat(filtersType.getRawType()).isEqualTo(Map.class);
        assertThat(filtersType.getActualTypeArguments()).containsExactly(String.class, Object.class);
    }

    @Test
    void untyped_mcp_agent_maps_scalar_schema_types() {
        McpClient mcpClient = mock(McpClient.class);
        ToolSpecification toolSpec = ToolSpecification.builder()
                .name("typed_search")
                .parameters(JsonObjectSchema.builder()
                        .addProperty("text", new JsonStringSchema())
                        .addProperty("limit", new JsonIntegerSchema())
                        .addProperty("score", new JsonNumberSchema())
                        .addProperty("exact", new JsonBooleanSchema())
                        .addProperty(
                                "order",
                                JsonEnumSchema.builder()
                                        .enumValues("asc", "desc")
                                        .build())
                        .build())
                .build();
        when(mcpClient.listTools()).thenReturn(List.of(toolSpec));

        UntypedAgent agent = McpAgent.builder(mcpClient).build();

        assertThat(((McpClientInstance) agent).arguments())
                .extracting(AgentArgument::rawType)
                .containsExactly(String.class, Integer.class, Double.class, Boolean.class, String.class);
    }

    @Test
    void untyped_mcp_agent_preserves_types_for_explicit_input_keys() {
        McpClient mcpClient = mock(McpClient.class);
        ToolSpecification toolSpec = ToolSpecification.builder()
                .name("search_records")
                .parameters(JsonObjectSchema.builder()
                        .addProperty(
                                "fields",
                                JsonArraySchema.builder()
                                        .items(new JsonStringSchema())
                                        .build())
                        .addProperty("query", new JsonStringSchema())
                        .addProperty("unused", new JsonBooleanSchema())
                        .build())
                .build();
        when(mcpClient.listTools()).thenReturn(List.of(toolSpec));

        UntypedAgent agent =
                McpAgent.builder(mcpClient).inputKeys("fields", "query").build();
        McpClientInstance mcpAgent = (McpClientInstance) agent;
        List<AgentArgument> arguments = mcpAgent.arguments();

        assertThat(arguments).extracting(AgentArgument::name).containsExactly("fields", "query");
        ParameterizedType fieldsType = (ParameterizedType) arguments.get(0).type();
        assertThat(fieldsType.getRawType()).isEqualTo(List.class);
        assertThat(fieldsType.getActualTypeArguments()).containsExactly(String.class);
        assertThat(arguments.get(1).type()).isEqualTo(String.class);
    }

    @Test
    void untyped_mcp_agent_falls_back_to_object_for_unknown_schema_properties() {
        McpClient mcpClient = mock(McpClient.class);
        ToolSpecification toolSpec = ToolSpecification.builder()
                .name("search_records")
                .parameters(JsonObjectSchema.builder()
                        .addProperty("known", new JsonStringSchema())
                        .build())
                .build();
        when(mcpClient.listTools()).thenReturn(List.of(toolSpec));

        UntypedAgent agent =
                McpAgent.builder(mcpClient).inputKeys("known", "missing").build();
        List<AgentArgument> arguments = ((McpClientInstance) agent).arguments();

        assertThat(arguments.get(0).type()).isEqualTo(String.class);
        assertThat(arguments.get(1).type()).isEqualTo(Object.class);
    }

    @Test
    void untyped_mcp_agent_falls_back_to_object_for_unresolved_references() {
        McpClient mcpClient = mock(McpClient.class);
        ToolSpecification toolSpec = ToolSpecification.builder()
                .name("reference_tool")
                .parameters(JsonObjectSchema.builder()
                        .addProperty(
                                "value",
                                JsonReferenceSchema.builder()
                                        .reference("MissingDefinition")
                                        .build())
                        .build())
                .build();
        when(mcpClient.listTools()).thenReturn(List.of(toolSpec));

        UntypedAgent agent = McpAgent.builder(mcpClient).build();

        assertThat(((McpClientInstance) agent).arguments().get(0).type()).isEqualTo(Object.class);
    }

    @Test
    void untyped_mcp_agent_resolves_dollar_defs_references() {
        JsonObjectSchema address =
                JsonObjectSchema.builder().addStringProperty("city").build();
        ToolSpecification toolSpec = ToolSpecification.builder()
                .name("dollar_defs_tool")
                .parameters(JsonObjectSchema.builder()
                        .addProperty(
                                "address",
                                JsonReferenceSchema.builder()
                                        .reference("#/$defs/Address")
                                        .build())
                        .definitions(Map.of("Address", address))
                        .build())
                .build();

        McpClient mcpClient = mock(McpClient.class);
        when(mcpClient.listTools()).thenReturn(List.of(toolSpec));

        UntypedAgent agent = McpAgent.builder(mcpClient).build();

        ParameterizedType addressType = (ParameterizedType)
                ((McpClientInstance) agent).arguments().get(0).type();
        assertThat(addressType.getRawType()).isEqualTo(Map.class);
        assertThat(addressType.getActualTypeArguments()).containsExactly(String.class, Object.class);
    }

    @Test
    void untyped_mcp_agent_maps_raw_schema_to_object() {
        McpClient mcpClient = mock(McpClient.class);
        ToolSpecification toolSpec = ToolSpecification.builder()
                .name("raw_tool")
                .parameters(JsonObjectSchema.builder()
                        .addProperty("value", JsonRawSchema.from("{\"type\":\"string\"}"))
                        .build())
                .build();
        when(mcpClient.listTools()).thenReturn(List.of(toolSpec));

        UntypedAgent agent = McpAgent.builder(mcpClient).build();

        assertThat(((McpClientInstance) agent).arguments().get(0).type()).isEqualTo(Object.class);
    }

    @Test
    void untyped_mcp_agent_preserves_nested_array_types() {
        McpClient mcpClient = mock(McpClient.class);
        ToolSpecification toolSpec = ToolSpecification.builder()
                .name("nested_search")
                .parameters(JsonObjectSchema.builder()
                        .addProperty(
                                "values",
                                JsonArraySchema.builder()
                                        .items(JsonArraySchema.builder()
                                                .items(new JsonIntegerSchema())
                                                .build())
                                        .build())
                        .build())
                .build();
        when(mcpClient.listTools()).thenReturn(List.of(toolSpec));

        UntypedAgent agent = McpAgent.builder(mcpClient).build();
        ParameterizedType outerType = (ParameterizedType)
                ((McpClientInstance) agent).arguments().get(0).type();
        ParameterizedType innerType = (ParameterizedType) outerType.getActualTypeArguments()[0];

        assertThat(outerType.getRawType()).isEqualTo(List.class);
        assertThat(innerType.getRawType()).isEqualTo(List.class);
        assertThat(innerType.getActualTypeArguments()).containsExactly(Integer.class);
    }

    @Test
    void untyped_mcp_agent_resolves_references_and_unions() {
        JsonObjectSchema address =
                JsonObjectSchema.builder().addStringProperty("city").build();
        JsonObjectSchema parameters = JsonObjectSchema.builder()
                .addProperty(
                        "name",
                        JsonAnyOfSchema.builder()
                                .anyOf(new JsonStringSchema(), new JsonNullSchema())
                                .build())
                .addProperty(
                        "sameType",
                        JsonAnyOfSchema.builder()
                                .anyOf(
                                        new JsonStringSchema(),
                                        JsonEnumSchema.builder()
                                                .enumValues("a", "b")
                                                .build())
                                .build())
                .addProperty(
                        "differentTypes",
                        JsonAnyOfSchema.builder()
                                .anyOf(new JsonStringSchema(), new JsonIntegerSchema())
                                .build())
                .addProperty(
                        "address",
                        JsonReferenceSchema.builder()
                                .reference("#/definitions/Address")
                                .build())
                .addProperty(
                        "addresses",
                        JsonArraySchema.builder()
                                .items(JsonReferenceSchema.builder()
                                        .reference("Address")
                                        .build())
                                .build())
                .required("sameType", "differentTypes", "address", "addresses")
                .definitions(Map.of("Address", address))
                .build();

        McpClient mcpClient = mock(McpClient.class);
        when(mcpClient.listTools())
                .thenReturn(List.of(ToolSpecification.builder()
                        .name("schema_tool")
                        .parameters(parameters)
                        .build()));

        UntypedAgent agent = McpAgent.builder(mcpClient).build();
        List<AgentArgument> arguments = ((McpClientInstance) agent).arguments();

        assertThat(arguments)
                .extracting(AgentArgument::name)
                .containsExactly("name", "sameType", "differentTypes", "address", "addresses");
        assertThat(arguments.get(0).type()).isEqualTo(String.class);
        assertThat(arguments.get(0).isOptional()).isTrue();
        assertThat(arguments.get(1).type()).isEqualTo(String.class);
        assertThat(arguments.get(1).isOptional()).isFalse();
        assertThat(arguments.get(2).type()).isEqualTo(Object.class);
        assertThat(arguments.get(3).type()).isNotEqualTo(Object.class);
        assertThat(arguments.get(3).isOptional()).isFalse();

        ParameterizedType addressType = (ParameterizedType) arguments.get(3).type();
        assertThat(addressType.getRawType()).isEqualTo(Map.class);
        assertThat(addressType.getActualTypeArguments()).containsExactly(String.class, Object.class);

        ParameterizedType addressesType = (ParameterizedType) arguments.get(4).type();
        assertThat(addressesType.getRawType()).isEqualTo(List.class);
        assertThat(addressesType.getActualTypeArguments()).containsExactly(addressType);
    }

    @Test
    void untyped_mcp_agent_validates_required_schema_arguments() {
        McpClient mcpClient = mock(McpClient.class);
        ToolSpecification toolSpec = ToolSpecification.builder()
                .name("required_tool")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("required")
                        .addStringProperty("optional")
                        .required("required")
                        .build())
                .build();
        when(mcpClient.listTools()).thenReturn(List.of(toolSpec));

        UntypedAgent agent = McpAgent.builder(mcpClient).build();
        UntypedAgent pipeline =
                AgenticServices.sequenceBuilder().subAgents(agent).build();

        assertThatThrownBy(() -> pipeline.invoke(Map.of("optional", "value")))
                .isInstanceOf(MissingArgumentException.class)
                .hasMessageContaining("required");
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

    public interface TypedCollectionAgent {

        @Agent(description = "Search records")
        String search(@V("fields") List<String> fields, @V("recordIds") String[] recordIds);
    }

    public interface TypedDescribedCollectionAgent {

        @Agent(description = "Search records")
        String search(
                @V("fields") @P(name = "fields") List<String> fields,
                @V("recordIds") @P(name = "recordIds", description = "IDs selected by the caller") String[] recordIds);
    }

    @Test
    void typed_mcp_agent_preserves_generic_argument_types() throws Exception {
        McpClient mcpClient = mockMcpClient("search_records", "Search records", "fields", "recordIds");
        TypedCollectionAgent agent =
                McpAgent.builder(mcpClient, TypedCollectionAgent.class).build();

        Method method = TypedCollectionAgent.class.getMethod("search", List.class, String[].class);
        List<AgentArgument> clientArguments = ((McpClientInstance) agent).arguments();
        assertThat(clientArguments).extracting(AgentArgument::name).containsExactly("fields", "recordIds");
        assertThat(clientArguments.get(0).type()).isEqualTo(method.getGenericParameterTypes()[0]);
        assertThat(clientArguments.get(1).type()).isEqualTo(method.getGenericParameterTypes()[1]);

        List<AgentArgument> arguments = new McpClientAgentInvoker((McpClientInstance) agent, method).arguments();

        assertThat(arguments.get(0).type()).isEqualTo(method.getGenericParameterTypes()[0]);
        assertThat(arguments.get(1).type()).isEqualTo(method.getGenericParameterTypes()[1]);
    }

    @Test
    void typed_mcp_agent_combines_method_types_and_schema_descriptions() throws Exception {
        McpClient mcpClient = mock(McpClient.class);
        ToolSpecification toolSpec = ToolSpecification.builder()
                .name("search_records")
                .description("Search records")
                .parameters(JsonObjectSchema.builder()
                        .addProperty(
                                "fields",
                                JsonArraySchema.builder()
                                        .description("Fields to include in the search")
                                        .items(new JsonStringSchema())
                                        .build())
                        .addProperty(
                                "recordIds",
                                JsonArraySchema.builder()
                                        .description("MCP record identifiers")
                                        .items(new JsonStringSchema())
                                        .build())
                        .build())
                .build();
        when(mcpClient.listTools()).thenReturn(List.of(toolSpec));

        TypedDescribedCollectionAgent agent =
                McpAgent.builder(mcpClient, TypedDescribedCollectionAgent.class).build();
        Method method = TypedDescribedCollectionAgent.class.getMethod("search", List.class, String[].class);

        List<AgentArgument> clientArguments = ((McpClientInstance) agent).arguments();
        assertThat(clientArguments).extracting(AgentArgument::type).containsExactly(
                method.getGenericParameterTypes()[0], method.getGenericParameterTypes()[1]);
        assertThat(clientArguments)
                .extracting(AgentArgument::description)
                .containsExactly("Fields to include in the search", "IDs selected by the caller");

        List<AgentArgument> invokerArguments =
                new McpClientAgentInvoker((McpClientInstance) agent, method).arguments();
        assertThat(invokerArguments)
                .extracting(AgentArgument::description)
                .containsExactly("Fields to include in the search", "IDs selected by the caller");

        Method cardBuilder = SupervisorPlanner.class.getDeclaredMethod("toCard", AgentInstance.class);
        cardBuilder.setAccessible(true);
        assertThat(cardBuilder.invoke(null, new McpClientAgentInvoker((McpClientInstance) agent, method)))
                .isEqualTo(
                        "{'search_records', 'Search records', [fields: List<String> - Fields to include in the search, "
                                + "recordIds: String[] - IDs selected by the caller]}");
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

        assertThatThrownBy(() ->
                        McpAgent.builder(mcpClient).toolName("nonexistent_tool").build())
                .isInstanceOf(dev.langchain4j.agentic.planner.AgenticSystemConfigurationException.class)
                .hasMessageContaining("nonexistent_tool")
                .hasMessageContaining("not found");
    }

    @Test
    void mcp_agent_tool_execution_error_throws_exception() {
        McpClient mcpClient = mockMcpClient("fail_tool", "A tool that fails", "input");
        mockToolError(mcpClient, "Something went wrong");

        UntypedAgent agent = McpAgent.builder(mcpClient).outputKey("result").build();

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

        UntypedAgent agent = McpAgent.builder(mcpClient).outputKey("result").build();

        assertThat(agent).isInstanceOf(McpClientInstance.class);
        McpClientInstance mcpInstance = (McpClientInstance) agent;
        assertThat(mcpInstance.topology())
                .isEqualTo(dev.langchain4j.agentic.planner.AgenticSystemTopology.NON_AI_AGENT);
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
    void untyped_mcp_agent_records_a_snapshot_of_the_state_as_its_input() {
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
                .outputKey("upperCaseStory")
                .build();

        UntypedAgent pipeline = AgenticServices.sequenceBuilder()
                .subAgents(generator, uppercaser)
                .outputKey("upperCaseStory")
                .build();

        ResultWithAgenticScope<String> result = pipeline.invokeWithAgenticScope(Map.of("topic", "dragons"));
        assertThat(result.result()).isEqualTo("A STORY ABOUT DRAGONS");

        AgenticScope scope = result.agenticScope();
        List<AgentInvocation> invocations = scope.agentInvocations();
        assertThat(invocations).hasSize(2);

        AgentInvocation generatorInvocation = invocations.get(0);
        assertThat(generatorInvocation.agentName()).isEqualTo("generate");
        // The input must be the state as it was when the agent was invoked, not a live view of it.
        assertThat(generatorInvocation.input()).isNotSameAs(scope.state());
        assertThat(generatorInvocation.input()).isEqualTo(Map.of("topic", "dragons"));

        AgentInvocation uppercaserInvocation = invocations.get(1);
        assertThat(uppercaserInvocation.agentName()).isEqualTo("uppercase");
        assertThat(uppercaserInvocation.input())
                .containsEntry("topic", "dragons")
                .containsEntry("story", "A story about dragons")
                .doesNotContainKey("upperCaseStory");
    }

    @Test
    void untyped_mcp_agent_does_not_receive_non_serializable_state_values() {
        McpClient mcpClient = mockMcpClient("generate", "Generate content", "topic");
        mockToolResult(mcpClient, "A story about dragons");

        UntypedAgent generator = McpAgent.builder(mcpClient)
                .inputKeys("topic")
                .outputKey("story")
                .build();

        UntypedAgent pipeline = AgenticServices.sequenceBuilder()
                .subAgents(generator)
                .outputKey("story")
                .build();

        ResultWithAgenticScope<String> result =
                pipeline.invokeWithAgenticScope(Map.of("topic", "dragons", "pendingResult", new CompletableFuture<>()));
        assertThat(result.result()).isEqualTo("A story about dragons");

        AgenticScope scope = result.agenticScope();
        assertThat(scope.state()).containsKey("pendingResult");
        assertThat(scope.agentInvocations().get(0).input()).isEqualTo(Map.of("topic", "dragons"));
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

        UntypedAgent agent = McpAgent.builder(mcpClient).outputKey("time").build();

        assertThat(((McpClientInstance) agent).arguments()).isEmpty();
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

    @Test
    void typed_mcp_agent_in_workflow_throws_on_missing_required_input() {
        McpClient mcpClient = mockMcpClient("translate", "Translate text to a target language", "text", "language");

        TypedTranslator translator = McpAgent.builder(mcpClient, TypedTranslator.class)
                .outputKey("translation")
                .build();

        UntypedAgent pipeline = AgenticServices.sequenceBuilder()
                .subAgents(translator)
                .outputKey("translation")
                .build();

        // "language" is a required input of the typed agent but is absent from the AgenticScope state.
        assertThatThrownBy(() -> pipeline.invoke(Map.of("text", "Hello world")))
                .isInstanceOf(MissingArgumentException.class)
                .hasMessageContaining("language");
    }
}
