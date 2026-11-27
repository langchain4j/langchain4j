package dev.langchain4j.mcp;

import static dev.langchain4j.agent.tool.SearchBehavior.ALWAYS_VISIBLE;
import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.InstanceOfAssertFactories.iterable;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSource;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.ToolArgumentsException;
import dev.langchain4j.guardrail.ToolOutputGuardrail;
import dev.langchain4j.guardrail.ToolOutputGuardrailRequest;
import dev.langchain4j.guardrail.ToolOutputGuardrailResult;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.McpReadResourceResult;
import dev.langchain4j.mcp.client.McpResource;
import dev.langchain4j.mcp.client.McpTextResourceContents;
import dev.langchain4j.mcp.resourcesastools.DefaultMcpResourcesAsToolsPresenter;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.AiServiceTool;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import dev.langchain4j.test.mock.MockChatModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class McpToolProviderTest {

    McpClient mcpClient = mock(McpClient.class);

    @BeforeEach
    void setUp() {
        when(mcpClient.key()).thenReturn("mcp-1");
        when(mcpClient.listTools())
                .thenReturn(List.of(
                        ToolSpecification.builder()
                                .name("tool_1")
                                .metadata(Map.of("one", 1))
                                .build(),
                        ToolSpecification.builder()
                                .name("tool_2")
                                .metadata(Map.of("two", 2))
                                .build()));
    }

    @Test
    void should_configure_always_visible_tools() {

        // given
        McpToolProvider toolProvider = McpToolProvider.builder()
                .mcpClients(mcpClient)
                .alwaysVisibleToolNames("tool_1")
                .build();

        // when
        ToolProviderResult toolProviderResult = toolProvider.provideTools(toolProviderRequest());

        // then
        assertThat(toolProviderResult.tools())
                .hasSize(2)
                .containsKeys(
                        ToolSpecification.builder()
                                .name("tool_1")
                                .metadata(Map.of("one", 1, "searchBehavior", ALWAYS_VISIBLE))
                                .build(),
                        ToolSpecification.builder()
                                .name("tool_2")
                                .metadata(Map.of("two", 2))
                                .build()
                );
    }

    @Test
    void should_configure_always_visible_tools_with_tool_name_mapper() {

        // given
        McpToolProvider toolProvider = McpToolProvider.builder()
                .mcpClients(mcpClient)
                .alwaysVisibleToolNames("my_tool_1")
                .toolNameMapper((client, spec) -> "my_" + spec.name())
                .build();

        // when
        ToolProviderResult toolProviderResult = toolProvider.provideTools(toolProviderRequest());

        // then
        assertThat(toolProviderResult.tools())
                .hasSize(2)
                .containsKeys(
                        ToolSpecification.builder()
                                .name("my_tool_1")
                                .metadata(Map.of("one", 1, "searchBehavior", ALWAYS_VISIBLE))
                                .build(),
                        ToolSpecification.builder()
                                .name("my_tool_2")
                                .metadata(Map.of("two", 2))
                                .build());
    }

    @Test
    void should_stamp_mcp_tool_source_for_each_client() {
        var secondClient = mock(McpClient.class);
        when(secondClient.key()).thenReturn("mcp-2");
        when(secondClient.listTools())
                .thenReturn(List.of(ToolSpecification.builder().name("tool_3").build()));

        var toolProvider =
                McpToolProvider.builder().mcpClients(mcpClient, secondClient).build();

        var result = toolProvider.provideTools(toolProviderRequest());

        assertThat(result.aiServiceTools())
                .extracting(AiServiceTool::name, AiServiceTool::toolSource)
                .containsExactly(
                        tuple("tool_1", ToolSource.mcp("mcp-1")),
                        tuple("tool_2", ToolSource.mcp("mcp-1")),
                        tuple("tool_3", ToolSource.mcp("mcp-2")));
    }

    @Test
    void should_preserve_mcp_tool_source_with_name_mapper() {
        var toolProvider = McpToolProvider.builder()
                .mcpClients(mcpClient)
                .toolNameMapper((client, spec) -> "%s_%s".formatted(client.key(), spec.name()))
                .build();

        var result = toolProvider.provideTools(toolProviderRequest());

        assertThat(result.aiServiceTools())
                .extracting(AiServiceTool::name, AiServiceTool::toolSource)
                .containsExactly(
                        tuple("mcp-1_tool_1", ToolSource.mcp("mcp-1")), tuple("mcp-1_tool_2", ToolSource.mcp("mcp-1")));
    }

    @Test
    void should_preserve_mcp_tool_source_with_specification_mapper() {
        var toolProvider = McpToolProvider.builder()
                .mcpClients(mcpClient)
                .toolSpecificationMapper((client, spec) -> spec.toBuilder()
                        .name("custom_%s".formatted(spec.name()))
                        .description("mapped")
                        .build())
                .build();

        var result = toolProvider.provideTools(toolProviderRequest());

        assertThat(result.aiServiceTools())
                .extracting(
                        AiServiceTool::name, tool -> tool.toolSpecification().description(), AiServiceTool::toolSource)
                .containsExactly(
                        tuple("custom_tool_1", "mapped", ToolSource.mcp("mcp-1")),
                        tuple("custom_tool_2", "mapped", ToolSource.mcp("mcp-1")));
    }

    @Test
    void should_apply_tool_wrapper_while_stamping_mcp_tool_source() {
        when(mcpClient.executeTool(any(), any()))
                .thenReturn(ToolExecutionResult.builder().resultText("raw").build());
        var wrapperCalls = new AtomicInteger();
        var toolProvider = McpToolProvider.builder()
                .mcpClients(mcpClient)
                .toolWrapper(delegate -> new ToolExecutor() {
                    @Override
                    public String execute(ToolExecutionRequest request, Object memoryId) {
                        wrapperCalls.incrementAndGet();
                        return "wrapped:%s".formatted(delegate.execute(request, memoryId));
                    }

                    @Override
                    public ToolExecutionResult executeWithContext(
                            ToolExecutionRequest request, InvocationContext context) {
                        wrapperCalls.incrementAndGet();
                        var delegateResult = delegate.executeWithContext(request, context);
                        return ToolExecutionResult.builder()
                                .resultText("wrapped:%s".formatted(delegateResult.resultText()))
                                .build();
                    }
                })
                .build();

        var result = toolProvider.provideTools(toolProviderRequest());
        var tool1 = toolByName(result, "tool_1");
        var executionResult = tool1.toolExecutor()
                .executeWithContext(
                        ToolExecutionRequest.builder()
                                .name("tool_1")
                                .arguments("{}")
                                .build(),
                        InvocationContext.builder().build());

        assertThat(tool1.toolSource()).isEqualTo(ToolSource.mcp("mcp-1"));
        assertThat(executionResult.resultText()).isEqualTo("wrapped:raw");
        assertThat(wrapperCalls).hasValue(1);
    }

    @Test
    void should_stamp_multi_server_tool_source_on_resources_as_tools() {
        var clientAlice = mockResourceClient("alice", "alice_tool");
        var clientBob = mockResourceClient("bob", "bob_tool");
        var clientCharlie = mockResourceClient("charlie", "charlie_tool");
        var toolProvider = resourcesToolProvider(clientAlice, clientBob, clientCharlie);

        var allResult = toolProvider.provideTools(toolProviderRequest());
        var expectedAllResourcesSource = ToolSource.mcp(List.of("alice", "bob", "charlie"));

        assertThat(allResult.aiServiceTools())
                .extracting(AiServiceTool::name, AiServiceTool::toolSource)
                .containsExactly(
                        tuple("alice_tool", ToolSource.mcp("alice")),
                        tuple("bob_tool", ToolSource.mcp("bob")),
                        tuple("charlie_tool", ToolSource.mcp("charlie")),
                        tuple("list_resources", expectedAllResourcesSource),
                        tuple("get_resource", expectedAllResourcesSource));

        var subsetResult = toolProvider.provideTools(toolProviderRequest(), List.of(clientAlice, clientCharlie));
        var expectedSubsetResourcesSource = ToolSource.mcp(List.of("alice", "charlie"));

        assertThat(subsetResult.aiServiceTools())
                .extracting(AiServiceTool::name, AiServiceTool::toolSource)
                .containsExactly(
                        tuple("alice_tool", ToolSource.mcp("alice")),
                        tuple("charlie_tool", ToolSource.mcp("charlie")),
                        tuple("list_resources", expectedSubsetResourcesSource),
                        tuple("get_resource", expectedSubsetResourcesSource));
        // ToolSource.Mcp equality ignores key order, so check the order separately
        assertThat(toolByName(subsetResult, "list_resources").toolSource())
                .asInstanceOf(type(ToolSource.Mcp.class))
                .extracting(ToolSource.Mcp::serverKeys, as(iterable(String.class)))
                .containsExactly("alice", "charlie");
    }

    @Test
    void should_list_resources_only_from_considered_clients() {
        var clientAlice = mockResourceClientWithResource("alice", "file:///alice", "alice-content");
        var clientBob = mockResourceClient("bob", "bob_tool");
        var clientCharlie = mockResourceClient("charlie", "charlie_tool");
        var toolProvider = resourcesToolProvider(clientAlice, clientBob, clientCharlie);

        var subsetResult = toolProvider.provideTools(toolProviderRequest(), List.of(clientAlice, clientCharlie));
        var listedJson =
                toolByName(subsetResult, "list_resources").toolExecutor().execute(listResourcesRequest(), null);

        assertThat(listedJson)
                .contains("\"mcpServer\":\"alice\"", "\"uri\":\"file:///alice\"")
                .doesNotContain("\"mcpServer\":\"bob\"");
        verify(clientAlice).listResources(any());
        verify(clientCharlie).listResources(any());
        verify(clientBob, never()).listResources(any());
    }

    @Test
    void should_get_resource_from_considered_client() {
        var clientAlice = mockResourceClientWithResource("alice", "file:///alice", "alice-content");
        var clientBob = mockResourceClient("bob", "bob_tool");
        var toolProvider = resourcesToolProvider(clientAlice, clientBob);

        var subsetResult = toolProvider.provideTools(toolProviderRequest(), List.of(clientAlice));
        var content = toolByName(subsetResult, "get_resource")
                .toolExecutor()
                .execute(getResourceRequest("alice", "file:///alice"), null);

        assertThat(content).isEqualTo("alice-content");
    }

    @Test
    void should_reject_get_resource_from_client_outside_considered_clients() {
        var clientAlice = mockResourceClient("alice", "alice_tool");
        var clientBob = mockResourceClient("bob", "bob_tool");
        var toolProvider = resourcesToolProvider(clientAlice, clientBob);

        var subsetResult = toolProvider.provideTools(toolProviderRequest(), List.of(clientAlice));
        var getResourceExecutor = toolByName(subsetResult, "get_resource").toolExecutor();

        assertThatThrownBy(() -> getResourceExecutor.execute(getResourceRequest("bob", "file:///bob"), null))
                .isInstanceOf(ToolArgumentsException.class)
                .hasMessageContaining("unknown MCP server: bob");
        verify(clientBob, never()).readResource(any(), any());
    }

    @Test
    void should_apply_tool_wrapper_to_resource_tools() {
        var clientAlice = mockResourceClientWithResource("alice", "file:///alice", "alice-content");
        var wrappedCount = new AtomicInteger();
        var toolProvider = McpToolProvider.builder()
                .mcpClients(clientAlice)
                .resourcesAsToolsPresenter(
                        DefaultMcpResourcesAsToolsPresenter.builder().build())
                .toolWrapper(delegate -> (request, memoryId) -> {
                    wrappedCount.incrementAndGet();
                    return delegate.execute(request, memoryId);
                })
                .build();

        var result = toolProvider.provideTools(toolProviderRequest());
        toolByName(result, "list_resources").toolExecutor().execute(listResourcesRequest(), null);
        toolByName(result, "get_resource").toolExecutor().execute(getResourceRequest("alice", "file:///alice"), null);

        assertThat(wrappedCount).hasValue(2);
    }

    @Test
    void should_isolate_emitted_resource_executors_from_later_add_or_remove_client_mutations() {
        var clientAlice = mockResourceClient("alice", "alice_tool");
        var clientBob = mockResourceClient("bob", "bob_tool");
        var toolProvider = resourcesToolProvider(clientAlice, clientBob);

        // Default provideTools(request) uses the provider's internal CopyOnWriteArrayList
        var allResult = toolProvider.provideTools(toolProviderRequest());

        var clientDave = mockResourceClient("dave", "dave_tool");
        toolProvider.addMcpClient(clientDave);
        toolProvider.removeMcpClient(clientBob);

        toolByName(allResult, "list_resources").toolExecutor().execute(listResourcesRequest(), null);

        verify(clientAlice).listResources(any());
        verify(clientBob).listResources(any());
        verify(clientDave, never()).listResources(any());
    }

    @Test
    void should_not_emit_resource_tools_when_considered_clients_are_empty() {
        var toolProvider = resourcesToolProvider(mcpClient);

        var emptyResult = toolProvider.provideTools(toolProviderRequest(), List.of());

        assertThat(emptyResult.aiServiceTools()).isEmpty();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void should_stamp_an_mcp_source_without_server_keys_for_a_null_or_blank_client_key(String key) {
        var keylessClient = mockResourceClient(key, "keyless_tool");
        var keyedClient = mockResourceClient("keyed", "keyed_tool");

        var result = resourcesToolProvider(keylessClient, keyedClient).provideTools(toolProviderRequest());

        assertThat(result.aiServiceTools())
                .extracting(AiServiceTool::name, AiServiceTool::toolSource)
                .containsExactlyInAnyOrder(
                        tuple("keyless_tool", ToolSource.mcp(List.of())),
                        tuple("keyed_tool", ToolSource.mcp("keyed")),
                        // the resource tools also reach the keyless client: their source names no server
                        tuple("list_resources", ToolSource.mcp(List.of())),
                        tuple("get_resource", ToolSource.mcp(List.of())));
    }

    @Test
    void should_skip_a_null_client_in_considered_clients_when_one_server_may_fail() {
        var toolProvider = McpToolProvider.builder()
                .mcpClients(mcpClient)
                .failIfOneServerFails(false)
                .build();
        var consideredClients = new ArrayList<McpClient>();
        consideredClients.add(null);
        consideredClients.add(mcpClient);

        var result = toolProvider.provideTools(toolProviderRequest(), consideredClients);

        assertThat(result.aiServiceTools())
                .extracting(AiServiceTool::name, AiServiceTool::toolSource)
                .containsExactly(tuple("tool_1", ToolSource.mcp("mcp-1")), tuple("tool_2", ToolSource.mcp("mcp-1")));
    }

    @Test
    void should_fail_for_a_null_client_in_considered_clients_when_no_server_may_fail() {
        var toolProvider = McpToolProvider.builder()
                .mcpClients(mcpClient)
                .failIfOneServerFails(true)
                .build();
        var consideredClients = new ArrayList<McpClient>();
        consideredClients.add(null);
        consideredClients.add(mcpClient);

        assertThatThrownBy(() -> toolProvider.provideTools(toolProviderRequest(), consideredClients))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Failed to retrieve tools from MCP server")
                .hasCauseInstanceOf(NullPointerException.class);
    }

    @Test
    void should_build_the_resource_tools_from_the_non_null_considered_clients() {
        var toolProvider = resourcesToolProvider(mcpClient);
        var consideredClients = new ArrayList<McpClient>();
        consideredClients.add(null);
        consideredClients.add(mcpClient);

        var result = toolProvider.provideTools(toolProviderRequest(), consideredClients);

        assertThat(result.aiServiceTools())
                .filteredOn(tool -> List.of("list_resources", "get_resource").contains(tool.name()))
                .extracting(AiServiceTool::toolSource)
                .containsOnly(ToolSource.mcp("mcp-1"))
                .hasSize(2);
    }

    @Test
    void should_build_the_resource_tools_and_their_source_from_one_read_of_the_considered_clients() {
        var trusted = mockResourceClient("trusted", "trusted_tool");
        var removed = mockResourceClient("removed", "removed_tool");
        var toolProvider = resourcesToolProvider(trusted, removed);
        // a list that changes while it is read: iterating it sees only the trusted client, a stream of it also the
        // removed one
        var consideredClients = new ArrayList<>(List.of(trusted)) {
            @Override
            public Stream<McpClient> stream() {
                return Stream.of(trusted, removed);
            }
        };

        var result = toolProvider.provideTools(toolProviderRequest(), consideredClients);
        toolByName(result, "list_resources").toolExecutor().execute(listResourcesRequest(), null);

        assertThat(toolByName(result, "list_resources").toolSource()).isEqualTo(ToolSource.mcp("trusted"));
        verify(trusted).listResources(any());
        verify(removed, never()).listResources(any());
    }

    @Test
    void should_still_provide_the_tools_of_a_client_whose_key_throws_and_name_no_server_for_the_resource_tools() {
        var failingKeyClient = mock(McpClient.class);
        when(failingKeyClient.key()).thenThrow(new IllegalStateException("cannot connect"));
        when(failingKeyClient.listTools())
                .thenReturn(List.of(ToolSpecification.builder().name("tool_3").build()));

        var resilientProvider = McpToolProvider.builder()
                .mcpClients(failingKeyClient, mcpClient)
                .resourcesAsToolsPresenter(
                        DefaultMcpResourcesAsToolsPresenter.builder().build())
                .failIfOneServerFails(false)
                .build();

        var result = resilientProvider.provideTools(toolProviderRequest());

        assertThat(toolByName(result, "tool_3").toolSource()).isEqualTo(ToolSource.mcp(List.of()));
        assertThat(toolByName(result, "tool_1").toolSource()).isEqualTo(ToolSource.mcp("mcp-1"));
        assertThat(toolByName(result, "list_resources").toolSource()).isEqualTo(ToolSource.mcp(List.of()));
    }

    @Test
    void should_preserve_fail_if_one_server_fails_when_list_tools_throws_illegal_argument_exception() {
        var failingListToolsClient = mock(McpClient.class);
        when(failingListToolsClient.key()).thenReturn("broken-server");
        when(failingListToolsClient.listTools()).thenThrow(new IllegalArgumentException("bad schema from server"));

        var resilientProvider = McpToolProvider.builder()
                .mcpClients(failingListToolsClient, mcpClient)
                .failIfOneServerFails(false)
                .build();

        var result = resilientProvider.provideTools(toolProviderRequest());

        assertThat(result.aiServiceTools())
                .extracting(AiServiceTool::name, AiServiceTool::toolSource)
                .containsExactly(tuple("tool_1", ToolSource.mcp("mcp-1")), tuple("tool_2", ToolSource.mcp("mcp-1")));
    }

    @Test
    void should_guard_multi_server_resource_tool_routed_via_tool_source_involves() {
        var clientAlice = mock(McpClient.class);
        when(clientAlice.key()).thenReturn("alice");
        when(clientAlice.listTools()).thenReturn(List.of());
        when(clientAlice.readResource(any(), any()))
                .thenReturn(new McpReadResourceResult(
                        List.of(new McpTextResourceContents("file:///secret", "raw-resource-body", "text/plain"))));

        var clientBob = mock(McpClient.class);
        when(clientBob.key()).thenReturn("bob");
        when(clientBob.listTools()).thenReturn(List.of());

        var toolProvider = resourcesToolProvider(clientAlice, clientBob);
        var getResourceCall = getResourceRequest("alice", "file:///secret").toBuilder()
                .id("1")
                .build();
        var chatModel = MockChatModel.thatResponds(AiMessage.from(getResourceCall), AiMessage.from("done"));
        var chatMemory = MessageWindowChatMemory.withMaxMessages(10);
        var seenSources = new ArrayList<ToolSource>();
        ToolOutputGuardrail resourceGuardrail = new ToolOutputGuardrail() {
            @Override
            public ToolOutputGuardrailResult validate(ToolOutputGuardrailRequest request) {
                seenSources.add(request.toolSource());
                if ((request.toolSource() instanceof ToolSource.Mcp mcp)
                        && mcp.involves("alice")
                        && mcp.involves("bob")) {
                    return successWith("[resource-guarded] %s".formatted(request.text()));
                }
                return success();
            }
        };

        var assistant = AiServices.builder(ResourceAssistant.class)
                .chatModel(chatModel)
                .chatMemory(chatMemory)
                .toolProvider(toolProvider)
                .toolOutputGuardrails(resourceGuardrail)
                .build();

        var answer = assistant.chat("Read file:///secret from alice");

        assertThat(answer).isEqualTo("done");
        assertThat(seenSources).containsExactly(ToolSource.mcp(List.of("alice", "bob")));
        assertThat(chatMemory.messages().stream()
                        .filter(ToolExecutionResultMessage.class::isInstance)
                        .map(ToolExecutionResultMessage.class::cast)
                        .toList())
                .extracting(ToolExecutionResultMessage::toolName, ToolExecutionResultMessage::text)
                .containsExactly(tuple("get_resource", "[resource-guarded] raw-resource-body"));
    }

    interface ResourceAssistant {
        String chat(String userMessage);
    }

    private static McpClient mockResourceClient(String key, String toolName) {
        var client = mock(McpClient.class);
        when(client.key()).thenReturn(key);
        when(client.listTools())
                .thenReturn(List.of(ToolSpecification.builder().name(toolName).build()));
        when(client.listResources(any())).thenReturn(List.of());
        when(client.listResourceTemplates()).thenReturn(List.of());
        return client;
    }

    private static McpClient mockResourceClientWithResource(String key, String uri, String content) {
        var client = mockResourceClient(key, "%s_tool".formatted(key));
        when(client.listResources(any()))
                .thenReturn(List.of(
                        new McpResource(uri, "%sInfo".formatted(key), "%s resource".formatted(key), "text/plain")));
        when(client.readResource(any(), any()))
                .thenReturn(
                        new McpReadResourceResult(List.of(new McpTextResourceContents(uri, content, "text/plain"))));
        return client;
    }

    private static McpToolProvider resourcesToolProvider(McpClient... clients) {
        return McpToolProvider.builder()
                .mcpClients(clients)
                .resourcesAsToolsPresenter(
                        DefaultMcpResourcesAsToolsPresenter.builder().build())
                .build();
    }

    private static ToolExecutionRequest listResourcesRequest() {
        return ToolExecutionRequest.builder()
                .name("list_resources")
                .arguments("{}")
                .build();
    }

    private static ToolExecutionRequest getResourceRequest(String mcpServer, String uri) {
        return ToolExecutionRequest.builder()
                .name("get_resource")
                .arguments("{\"mcpServer\":\"%s\",\"uri\":\"%s\"}".formatted(mcpServer, uri))
                .build();
    }

    private static AiServiceTool toolByName(ToolProviderResult result, String name) {
        return result.aiServiceTools().stream()
                .filter(tool -> tool.name().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private static ToolProviderRequest toolProviderRequest() {
        return ToolProviderRequest.builder()
                .invocationContext(InvocationContext.builder().build())
                .userMessage(UserMessage.from("does not matter"))
                .build();
    }
}