package dev.langchain4j.mcp.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.transport.McpOperationHandler;
import dev.langchain4j.mcp.client.transport.McpTransport;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

public class DefaultMcpClientTest {
    @Test
    public void should_construct_valid_client_with_minimally_mocked_transport() throws Exception {
        // given
        final McpTransport transport = getMinimalMcpTransportMock();

        // when
        final DefaultMcpClient client =
                new DefaultMcpClient.Builder().transport(transport).build();

        // then: client is properly constructed
        assertThat(client).isNotNull();
        // and: transport failure callback is set
        verify(transport).onFailure(any());
        // and: transport is started
        verify(transport).start(any());
        // and: transport is left open
        verify(transport, never()).close();
    }

    @Test
    public void should_close_transport_when_client_is_closed() throws Exception {
        // given
        final McpTransport transport = getMinimalMcpTransportMock();
        DefaultMcpClient client =
                new DefaultMcpClient.Builder().transport(transport).build();

        // when
        client.close();

        // then: the transport is closed
        verify(transport).close();
    }

    @Test
    public void should_reinitialize_when_transport_onFailure() throws Exception {
        // given: a client that will have its onFailure callback run
        final McpTransport transport = getMinimalMcpTransportMock();
        final ArgumentCaptor<Runnable> onFailureCaptor = ArgumentCaptor.forClass(Runnable.class);
        doNothing().when(transport).onFailure(onFailureCaptor.capture());

        new DefaultMcpClient.Builder().transport(transport).build();

        // sanity check: transport was started
        verify(transport).start(any(McpOperationHandler.class));

        // when: onFailure callback is triggered
        onFailureCaptor.getValue().run();

        // then: a second transport start occurred
        verify(transport, times(2)).start(any(McpOperationHandler.class));
    }

    @Test
    public void should_throw_from_build_when_there_is_no_transport() {
        // given
        final DefaultMcpClient.Builder clientBuilder = new DefaultMcpClient.Builder();

        // when
        assertThatThrownBy(() -> clientBuilder.build())
                // then
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("transport")
                .hasMessageContaining("null");
    }

    @Test
    public void should_throw_from_build_when_transport_initialize_throws() throws Exception {
        // given
        final McpTransport transport = getMinimalMcpTransportMock();
        final var exception = new RuntimeException("apples");
        doThrow(exception).when(transport).initialize(any());
        final DefaultMcpClient.Builder clientBuilder = new DefaultMcpClient.Builder().transport(transport);

        // when
        assertThatThrownBy(() -> clientBuilder.build())
                // then: DefaultMcpClient's initializer wraps all exceptions
                // in its own RuntimeException at the moment
                .isInstanceOf(Throwable.class)
                .hasMessageContaining("apples");

        // and: transport should be started
        verify(transport).start(any(McpOperationHandler.class));
        // and: transport should *not* be closed
        verify(transport, never()).close();
    }

    @Test
    public void should_not_react_to_transport_callbacks_if_there_is_no_object() throws Exception {
        // given
        final McpTransport transport = getMinimalMcpTransportMock();
        final var exception = new RuntimeException("apples");
        final ArgumentCaptor<Runnable> onFailureCaptor = ArgumentCaptor.forClass(Runnable.class);
        doNothing().when(transport).onFailure(onFailureCaptor.capture());
        doThrow(exception).when(transport).initialize(any());
        final DefaultMcpClient.Builder clientBuilder = new DefaultMcpClient.Builder().transport(transport);

        // and: exception was thrown during initialization (so there is no object)
        assertThatThrownBy(() -> clientBuilder.build()).isInstanceOf(Throwable.class);
        // but: some actions have occurred
        verify(transport).start(any(McpOperationHandler.class));
        verify(transport).initialize(any());

        // when: onFailure callback is triggered
        assertThatCode(() -> {
                    onFailureCaptor.getValue().run();
                })
                .doesNotThrowAnyException();

        // then: no further actions should be taken
        verify(transport, times(1)).start(any(McpOperationHandler.class));
        verify(transport, times(1)).initialize(any());
        // and: transport should *not* be closed
        verify(transport, never()).close();
    }

    @Test
    public void should_list_tools() throws Exception {
        // given
        final McpTransport transport = getMinimalMcpTransportMock();
        final DefaultMcpClient client =
                new DefaultMcpClient.Builder().transport(transport).build();
        final ObjectNode toolsJsonResult = getToolResultJson(
                new ToolDefinition("testTool", "A test tool", new ToolArg("argument1", "string", "An argument")));
        when(transport.executeOperationWithResponse(any(McpCallContext.class)))
                .thenReturn(CompletableFuture.completedFuture(toolsJsonResult));

        // when
        final List<ToolSpecification> tools = client.listTools();

        // then
        assertThat(tools).isNotNull().hasSize(1);

        final ToolSpecification tool = tools.get(0);
        assertThat(tool.name()).isEqualTo("testTool");
        assertThat(tool.description()).isEqualTo("A test tool");
    }

    @Test
    public void should_cache_tool_list() throws Exception {
        // given
        final McpTransport transport = getMinimalMcpTransportMock();
        final DefaultMcpClient client =
                new DefaultMcpClient.Builder().transport(transport).build();
        final ObjectNode toolsJsonResult = getToolResultJson(
                new ToolDefinition("testTool", "A test tool", new ToolArg("argument1", "string", "An argument")));
        when(transport.executeOperationWithResponse(any(McpCallContext.class)))
                .thenReturn(CompletableFuture.completedFuture(toolsJsonResult));

        // when: asking for tools twice
        final List<ToolSpecification> tools1 = client.listTools();
        final List<ToolSpecification> tools2 = client.listTools();

        // then: the tools are cached
        assertThat(tools2).isSameAs(tools1);
        // and: also do a sanity check
        assertThat(tools1).isNotNull().isNotEmpty();
        // and: the transport operation was executed only once
        verify(transport, times(1)).executeOperationWithResponse(any(McpCallContext.class));
    }

    @Test
    public void should_evict_tool_list_cache() throws Exception {
        // given
        final McpTransport transport = getMinimalMcpTransportMock();
        final DefaultMcpClient client =
                new DefaultMcpClient.Builder().transport(transport).build();
        final ObjectNode toolsJsonResult = getToolResultJson(
                new ToolDefinition("testTool", "A test tool", new ToolArg("argument1", "string", "An argument")));
        when(transport.executeOperationWithResponse(any(McpCallContext.class)))
                .thenReturn(CompletableFuture.completedFuture(toolsJsonResult));

        // and: the tools are cached
        final List<ToolSpecification> tools = client.listTools();
        // and: the tool list is changed
        final ObjectNode newToolsJsonResult = getToolResultJson(new ToolDefinition(
                "testToolAnother",
                "Another test tool",
                new ToolArg("argumentAnother1", "integer", "Another argument")));
        when(transport.executeOperationWithResponse(any(McpCallContext.class)))
                .thenReturn(CompletableFuture.completedFuture(newToolsJsonResult));

        // when
        client.evictToolListCache();
        final List<ToolSpecification> toolsAfterEviction = client.listTools();

        // then: the tools were retrieved again
        assertThat(tools).isNotNull().isNotEmpty();
        assertThat(toolsAfterEviction).isNotNull().isNotEmpty();
        assertThat(toolsAfterEviction).isNotSameAs(tools);
        // and: the tool lists are different
        assertThat(tools.get(0).name()).isEqualTo("testTool");
        assertThat(toolsAfterEviction.get(0).name()).isEqualTo("testToolAnother");
        // and: the transport operation was executed once more after the eviction
        verify(transport, times(2)).executeOperationWithResponse(any(McpCallContext.class));
    }

    @Test
    public void should_allow_to_disable_tool_list_caching() {
        // given: a client built with caching disabled
        final McpTransport transport = getMinimalMcpTransportMock();
        final DefaultMcpClient client = new DefaultMcpClient.Builder()
                .transport(transport)
                .cacheToolList(false)
                .build();
        final ObjectNode toolsJsonResult = getToolResultJson(
                new ToolDefinition("testTool", "A test tool", new ToolArg("argument1", "string", "An argument")));
        when(transport.executeOperationWithResponse(any(McpCallContext.class)))
                .thenReturn(CompletableFuture.completedFuture(toolsJsonResult));

        // and: an initial tool list is retrieved
        final List<ToolSpecification> initialTools = client.listTools();
        // and: the tool list is changed
        final ObjectNode newToolsJsonResult = getToolResultJson(new ToolDefinition(
                "testToolAnother",
                "Another test tool",
                new ToolArg("argumentAnother1", "integer", "Another argument")));
        when(transport.executeOperationWithResponse(any(McpCallContext.class)))
                .thenReturn(CompletableFuture.completedFuture(newToolsJsonResult));

        // when
        final List<ToolSpecification> subsequentTools = client.listTools();

        // then: the tools were retrieved again
        assertThat(initialTools).isNotNull().isNotEmpty();
        assertThat(subsequentTools).isNotNull().isNotEmpty();
        assertThat(subsequentTools).isNotSameAs(initialTools);
        // and: the tool lists are different
        assertThat(initialTools.get(0).name()).isEqualTo("testTool");
        assertThat(subsequentTools.get(0).name()).isEqualTo("testToolAnother");
        // and: the transport operation was executed as many times as tools were retrieved
        verify(transport, times(2)).executeOperationWithResponse(any(McpCallContext.class));
    }

    private static McpTransport getMinimalMcpTransportMock() {
        McpTransport transport = mock(McpTransport.class);
        ObjectNode emptyJsonNode = JsonNodeFactory.instance.objectNode();
        when(transport.initialize(any())).thenReturn(CompletableFuture.completedFuture(emptyJsonNode));
        return transport;
    }

    private static ObjectNode getToolResultJson(ToolDefinition... tools) {
        final ArrayNode toolsArray = JsonNodeFactory.instance.arrayNode();
        toolsArray.addAll(Stream.of(tools)
                .map(tool -> {
                    final ObjectNode toolNode = JsonNodeFactory.instance.objectNode();
                    toolNode.put("name", tool.name());
                    toolNode.put("description", tool.description());
                    final ObjectNode inputSchema = toolNode.putObject("inputSchema");
                    inputSchema.put("type", "object");
                    final ObjectNode properties = inputSchema.putObject("properties");
                    for (ToolArg arg : tool.args) {
                        final ObjectNode argNode = properties.putObject(arg.name);
                        argNode.put("type", arg.type);
                        argNode.put("description", arg.description);
                    }
                    return toolNode;
                })
                .collect(Collectors.toList()));

        final ObjectNode rootNode = JsonNodeFactory.instance.objectNode();
        rootNode.putObject("result").set("tools", toolsArray);
        return rootNode;
    }

    private static record ToolDefinition(String name, String description, ToolArg... args) {}

    private static record ToolArg(String name, String type, String description) {}
}
