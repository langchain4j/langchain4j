package dev.langchain4j.mcp;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.ToolArgumentsException;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.McpReadResourceResult;
import dev.langchain4j.mcp.resourcesastools.DefaultMcpResourcesAsToolsPresenter;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static dev.langchain4j.agent.tool.SearchBehavior.ALWAYS_VISIBLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class McpToolProviderTest {

    McpClient mcpClient = mock(McpClient.class);

    @BeforeEach
    void setUp() {
        when(mcpClient.listTools()).thenReturn(List.of(
                ToolSpecification.builder()
                        .name("tool_1")
                        .metadata(Map.of("one", 1))
                        .build(),
                ToolSpecification.builder()
                        .name("tool_2")
                        .metadata(Map.of("two", 2))
                        .build()
        ));
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
                                .build()
                );
    }

    @Test
    void should_list_resources_only_from_considered_clients() {
        McpClient excluded = mock(McpClient.class);
        McpToolProvider provider = resourceToolProvider(excluded);
        var request = toolProviderRequest();
        var tools = provider.provideTools(request, List.of(mcpClient));

        executeResourceTool(tools, request, "list_resources", "{}");
        verify(mcpClient).listResources(request.invocationContext());
        verifyNoInteractions(excluded);
    }

    @Test
    void should_reject_resource_reads_from_excluded_clients() {
        McpClient excluded = mock(McpClient.class);
        var request = toolProviderRequest();
        when(mcpClient.key()).thenReturn("selected");
        when(excluded.key()).thenReturn("excluded");
        when(excluded.readResource("file:///info", request.invocationContext()))
                .thenReturn(new McpReadResourceResult(List.of()));
        var tools = resourceToolProvider(excluded).provideTools(request, List.of(mcpClient));

        assertThatThrownBy(() -> executeResourceTool(
                tools, request, "get_resource", "{\"mcpServer\":\"excluded\",\"uri\":\"file:///info\"}"))
                .isInstanceOf(ToolArgumentsException.class)
                .hasMessageContaining("unknown MCP server: excluded");
        verifyNoInteractions(excluded);
    }

    @Test
    void should_list_resources_from_all_clients_by_default() {
        McpClient second = mock(McpClient.class);
        var request = toolProviderRequest();
        var tools = resourceToolProvider(second).provideTools(request);

        executeResourceTool(tools, request, "list_resources", "{}");

        verify(mcpClient).listResources(request.invocationContext());
        verify(second).listResources(request.invocationContext());
    }

    private McpToolProvider resourceToolProvider(McpClient second) {
        return McpToolProvider.builder()
                .mcpClients(mcpClient, second)
                .resourcesAsToolsPresenter(DefaultMcpResourcesAsToolsPresenter.builder().build())
                .build();
    }

    private static void executeResourceTool(
            ToolProviderResult tools, ToolProviderRequest request, String name, String arguments) {
        var executor = tools.tools().entrySet().stream()
                .filter(entry -> entry.getKey().name().equals(name))
                .findFirst().orElseThrow().getValue();
        var call = ToolExecutionRequest.builder().name(name).arguments(arguments).build();
        executor.executeWithContext(call, request.invocationContext());
    }

    private static ToolProviderRequest toolProviderRequest() {
        return ToolProviderRequest.builder()
                .invocationContext(InvocationContext.builder().build())
                .userMessage(UserMessage.from("does not matter"))
                .build();
    }
}