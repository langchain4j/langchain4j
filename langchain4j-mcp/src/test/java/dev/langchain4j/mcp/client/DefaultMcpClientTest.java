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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.exception.ToolExecutionException;
import dev.langchain4j.mcp.client.transport.McpOperationHandler;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.protocol.McpClientMessage;
import dev.langchain4j.mcp.protocol.McpClientRequest;
import dev.langchain4j.mcp.protocol.McpListToolsParams;
import dev.langchain4j.mcp.protocol.McpListToolsRequest;
import dev.langchain4j.service.tool.ToolExecutionResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    public void should_expose_server_instructions_from_initialize_result() throws Exception {
        // given
        final McpTransport transport = getMinimalMcpTransportMock();
        ObjectNode initializeResult = JsonNodeFactory.instance.objectNode();
        initializeResult.putObject("result").put("instructions", "Use this server for file operations.");
        when(transport.initialize(any())).thenReturn(CompletableFuture.completedFuture(initializeResult));

        // when
        final DefaultMcpClient client =
                new DefaultMcpClient.Builder().transport(transport).build();

        // then
        assertThat(client.instructions()).isEqualTo("Use this server for file operations.");
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
    public void should_throw_mcp_exception_when_tool_list_is_refused() throws Exception {
        // given
        final McpTransport transport = getMinimalMcpTransportMock();
        final DefaultMcpClient client =
                new DefaultMcpClient.Builder().transport(transport).build();
        final ObjectNode errorResponse = JsonNodeFactory.instance.objectNode();
        errorResponse.put("jsonrpc", "2.0").put("id", 1);
        errorResponse.putObject("error").put("code", -32600).put("message", "You are not allowed to use these tools");
        when(transport.executeOperationWithResponse(any(McpCallContext.class)))
                .thenReturn(CompletableFuture.completedFuture(errorResponse));

        // when + then: the server's reason reaches the caller instead of a NullPointerException
        assertThatThrownBy(client::listTools)
                .isInstanceOf(McpException.class)
                .hasMessageContaining("You are not allowed to use these tools");
    }

    @Test
    public void should_cache_tool_list() throws Exception {
        // given
        final McpTransport transport = getMinimalMcpTransportMock();
        final DefaultMcpClient client = new DefaultMcpClient.Builder()
                .transport(transport)
                .protocolVersion("2025-11-25")
                .build();
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
        final DefaultMcpClient client = new DefaultMcpClient.Builder()
                .transport(transport)
                .protocolVersion("2025-11-25")
                .build();
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
                .protocolVersion("2025-11-25")
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

    @Test
    public void listener_should_run_before_meta_supplier() throws Exception {
        // given
        final McpTransport transport = getMinimalMcpTransportMock();
        ObjectNode toolResult = JsonNodeFactory.instance.objectNode();
        toolResult
                .putObject("result")
                .putArray("content")
                .addObject()
                .put("type", "text")
                .put("text", "ok");
        when(transport.executeOperationWithResponse(any(McpCallContext.class)))
                .thenReturn(CompletableFuture.completedFuture(toolResult));

        List<String> callOrder = new ArrayList<>();
        McpClientListener listener = new McpClientListener() {
            @Override
            public void beforeExecuteTool(McpCallContext context) {
                callOrder.add("listener");
            }
        };
        McpMetaSupplier metaSupplier = ctx -> {
            callOrder.add("meta");
            return Map.of("key", "value");
        };

        DefaultMcpClient client = new DefaultMcpClient.Builder()
                .transport(transport)
                .listener(listener)
                .metaSupplier(metaSupplier)
                .build();

        // when
        callOrder.clear();
        client.executeTool(
                ToolExecutionRequest.builder().name("test").arguments("{}").build());

        // then
        assertThat(callOrder)
                .as("Listener must run before meta supplier so that listeners can set up context "
                        + "(e.g. a tracing span) that the meta supplier can then reference")
                .containsExactly("listener", "meta");
    }

    @Test
    public void should_use_custom_tool_result_extractor_for_content_responses() throws Exception {
        final McpTransport transport = getMinimalMcpTransportMock();
        ObjectNode toolResult = JsonNodeFactory.instance.objectNode();
        toolResult
                .putObject("result")
                .putArray("content")
                .addObject()
                .put("type", "text")
                .put("text", "ok");
        when(transport.executeOperationWithResponse(any(McpCallContext.class)))
                .thenReturn(CompletableFuture.completedFuture(toolResult));

        McpToolResultExtractor extractor = (content, isError) -> ToolExecutionResult.builder()
                .result(Map.of("value", content.get(0).get("text").asText()))
                .resultText("custom:" + content.get(0).get("text").asText())
                .isError(isError)
                .build();

        DefaultMcpClient client = new DefaultMcpClient.Builder()
                .transport(transport)
                .toolResultExtractor(extractor)
                .build();

        ToolExecutionResult result = client.executeTool(
                ToolExecutionRequest.builder().name("test").arguments("{}").build());

        assertThat(result.resultText()).isEqualTo("custom:ok");
        assertThat(result.result()).isEqualTo(Map.of("value", "ok"));
    }

    @Test
    public void should_use_custom_tool_result_extractor_for_timeout_fallback() {
        final McpTransport transport = getMinimalMcpTransportMock();
        when(transport.executeOperationWithResponse(any(McpCallContext.class)))
                .thenAnswer(invocation -> new CompletableFuture<>());

        McpToolResultExtractor extractor = (content, isError) -> ToolExecutionResult.builder()
                .resultText("custom-timeout:" + content.get(0).get("text").asText())
                .isError(isError)
                .build();

        DefaultMcpClient client = new DefaultMcpClient.Builder()
                .transport(transport)
                // this transport never answers anything, so leaving the protocol version to be
                // detected would spend the whole detection timeout before the test even starts
                .protocolVersion("2025-11-25")
                .toolExecutionTimeout(java.time.Duration.ofMillis(1))
                .toolResultExtractor(extractor)
                .build();

        ToolExecutionResult result = client.executeTool(
                ToolExecutionRequest.builder().name("test").arguments("{}").build());

        assertThat(result.resultText()).isEqualTo("custom-timeout:There was a timeout executing the tool");
    }

    @Test
    public void should_use_custom_tool_result_extractor_for_listener_application_level_error_path() throws Exception {
        final McpTransport transport = getMinimalMcpTransportMock();
        ObjectNode toolResult = JsonNodeFactory.instance.objectNode();
        ObjectNode resultNode = toolResult.putObject("result");
        resultNode.put("isError", true);
        resultNode.putArray("content").addObject().put("type", "text").put("text", "bad");
        when(transport.executeOperationWithResponse(any(McpCallContext.class)))
                .thenReturn(CompletableFuture.completedFuture(toolResult));

        class CapturingListener implements McpClientListener {
            ToolExecutionResult toolResult;

            @Override
            public void afterExecuteTool(McpCallContext context, ToolExecutionResult result, Map<String, Object> raw) {
                this.toolResult = result;
            }
        }

        CapturingListener listener = new CapturingListener();
        McpToolResultExtractor extractor = (content, isError) -> ToolExecutionResult.builder()
                .result(Map.of("message", content.get(0).get("text").asText()))
                .resultText("custom-error")
                .isError(isError)
                .build();

        DefaultMcpClient client = new DefaultMcpClient.Builder()
                .transport(transport)
                .listener(listener)
                .toolResultExtractor(extractor)
                .build();

        assertThatThrownBy(() -> client.executeTool(ToolExecutionRequest.builder()
                        .name("test")
                        .arguments("{}")
                        .build()))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessage("custom-error");

        assertThat(listener.toolResult).isNotNull();
        assertThat(listener.toolResult.resultText()).isEqualTo("custom-error");
        assertThat(listener.toolResult.result()).isEqualTo(Map.of("message", "bad"));
        assertThat(listener.toolResult.isError()).isTrue();
    }

    @Test
    public void should_throw_for_application_level_error_even_if_custom_extractor_does_not_propagate_is_error()
            throws Exception {
        final McpTransport transport = getMinimalMcpTransportMock();
        ObjectNode toolResult = JsonNodeFactory.instance.objectNode();
        ObjectNode resultNode = toolResult.putObject("result");
        resultNode.put("isError", true);
        resultNode.putArray("content").addObject().put("type", "text").put("text", "bad");
        when(transport.executeOperationWithResponse(any(McpCallContext.class)))
                .thenReturn(CompletableFuture.completedFuture(toolResult));

        McpToolResultExtractor extractor = (content, isError) -> ToolExecutionResult.builder()
                .resultText(content.get(0).get("text").asText())
                .isError(false)
                .build();

        DefaultMcpClient client = new DefaultMcpClient.Builder()
                .transport(transport)
                .toolResultExtractor(extractor)
                .build();

        assertThatThrownBy(() -> client.executeTool(ToolExecutionRequest.builder()
                        .name("test")
                        .arguments("{}")
                        .build()))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessage("bad");
    }

    @Test
    public void should_throw_tool_execution_exception_for_application_level_error_with_multiple_result_contents()
            throws Exception {
        final McpTransport transport = getMinimalMcpTransportMock();
        ObjectNode toolResult = JsonNodeFactory.instance.objectNode();
        ObjectNode resultNode = toolResult.putObject("result");
        resultNode.put("isError", true);
        resultNode.putArray("content").addObject().put("type", "text").put("text", "bad");
        when(transport.executeOperationWithResponse(any(McpCallContext.class)))
                .thenReturn(CompletableFuture.completedFuture(toolResult));

        McpToolResultExtractor extractor = (content, isError) -> ToolExecutionResult.builder()
                .resultContents(List.of(TextContent.from("bad"), TextContent.from("details")))
                .isError(isError)
                .build();

        DefaultMcpClient client = new DefaultMcpClient.Builder()
                .transport(transport)
                .toolResultExtractor(extractor)
                .build();

        assertThatThrownBy(() -> client.executeTool(ToolExecutionRequest.builder()
                        .name("test")
                        .arguments("{}")
                        .build()))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessage("bad\ndetails");
    }

    /**
     * Verify that when a tool list operation returns a cursor, the client
     * proceeds to another request using that cursor.
     */
    @Test
    public void should_paginate_tool_list_using_cursor() throws Exception {
        // given
        final McpTransport transport = getMinimalMcpTransportMock();
        final DefaultMcpClient client = new DefaultMcpClient.Builder()
                .transport(transport)
                .protocolVersion("2025-11-25")
                .build();

        // first page: 2 tools + nextCursor
        final ObjectNode firstPage = getToolResultJson(
                new ToolDefinition("tool1", "First tool"), new ToolDefinition("tool2", "Second tool"));
        ((ObjectNode) firstPage.get("result")).put("nextCursor", "cursor-page2");

        // second page: 1 tool, no nextCursor
        final ObjectNode secondPage = getToolResultJson(new ToolDefinition("tool3", "Third tool"));

        final ArgumentCaptor<McpCallContext> callCaptor = ArgumentCaptor.forClass(McpCallContext.class);
        when(transport.executeOperationWithResponse(any(McpCallContext.class)))
                .thenReturn(CompletableFuture.completedFuture(firstPage))
                .thenReturn(CompletableFuture.completedFuture(secondPage));

        // when
        final List<ToolSpecification> tools = client.listTools();

        // then: all tools from both pages are returned
        assertThat(tools).hasSize(3);
        assertThat(tools.stream().map(ToolSpecification::name).collect(Collectors.toList()))
                .containsExactly("tool1", "tool2", "tool3");

        // and: the transport was called exactly twice
        verify(transport, times(2)).executeOperationWithResponse(callCaptor.capture());

        // and: the first request has no cursor
        McpListToolsRequest firstRequest =
                (McpListToolsRequest) callCaptor.getAllValues().get(0).message();
        assertThat(firstRequest.getParams()).isNull();

        // and: the second request carries the cursor from the first response
        McpListToolsRequest secondRequest =
                (McpListToolsRequest) callCaptor.getAllValues().get(1).message();
        assertThat(secondRequest.getParams()).isInstanceOf(McpListToolsParams.class);
        assertThat(((McpListToolsParams) secondRequest.getParams()).getCursor()).isEqualTo("cursor-page2");
    }

    @Test
    public void meta_supplier_should_not_drop_progress_token() throws Exception {
        final McpTransport transport = getMinimalMcpTransportMock();

        ObjectNode toolResult = JsonNodeFactory.instance.objectNode();
        toolResult
                .putObject("result")
                .putArray("content")
                .addObject()
                .put("type", "text")
                .put("text", "ok");
        when(transport.executeOperationWithResponse(any(McpCallContext.class)))
                .thenReturn(CompletableFuture.completedFuture(toolResult));

        DefaultMcpClient client = new DefaultMcpClient.Builder()
                .transport(transport)
                .protocolVersion("2025-11-25")
                .progressHandler(notification -> {})
                .metaSupplier(context -> Map.of("tenant", "acme"))
                .build();

        client.executeTool(
                ToolExecutionRequest.builder().name("test").arguments("{}").build());

        ArgumentCaptor<McpCallContext> captor = ArgumentCaptor.forClass(McpCallContext.class);
        verify(transport).executeOperationWithResponse(captor.capture());
        McpClientRequest request = (McpClientRequest) captor.getValue().message();
        Map<String, Object> meta = request.getParams().getMeta();

        // The user-supplied _meta must not overwrite the framework-managed progressToken.
        assertThat(meta).containsKey("tenant");
        assertThat(meta).containsKey("progressToken");
    }

    @Test
    public void modern_protocol_should_inject_required_meta_fields() throws Exception {
        final McpTransport transport = getModernMcpTransportMock();

        ObjectNode toolResult = JsonNodeFactory.instance.objectNode();
        toolResult
                .putObject("result")
                .putArray("content")
                .addObject()
                .put("type", "text")
                .put("text", "ok");
        // First call is server/discover, second is the tool call
        when(transport.executeOperationWithResponse(any(McpCallContext.class)))
                .thenReturn(CompletableFuture.completedFuture(getDiscoverResult()))
                .thenReturn(CompletableFuture.completedFuture(toolResult));

        DefaultMcpClient client = createMcpClient(transport);

        client.executeTool(
                ToolExecutionRequest.builder().name("test").arguments("{}").build());

        ArgumentCaptor<McpCallContext> captor = ArgumentCaptor.forClass(McpCallContext.class);
        verify(transport, times(2)).executeOperationWithResponse(captor.capture());
        // The second call is the tool execution (first is discover)
        McpClientRequest toolRequest =
                (McpClientRequest) captor.getAllValues().get(1).message();
        Map<String, Object> meta = toolRequest.getParams().getMeta();

        assertThat(meta).containsKey("io.modelcontextprotocol/protocolVersion");
        assertThat(meta.get("io.modelcontextprotocol/protocolVersion")).isEqualTo("2026-07-28");
        assertThat(meta).containsKey("io.modelcontextprotocol/clientInfo");
        assertThat(meta).containsKey("io.modelcontextprotocol/clientCapabilities");
    }

    @Test
    public void modern_protocol_meta_should_not_be_overwritten_by_user_supplier() throws Exception {
        final McpTransport transport = getModernMcpTransportMock();

        ObjectNode toolResult = JsonNodeFactory.instance.objectNode();
        toolResult
                .putObject("result")
                .putArray("content")
                .addObject()
                .put("type", "text")
                .put("text", "ok");
        when(transport.executeOperationWithResponse(any(McpCallContext.class)))
                .thenReturn(CompletableFuture.completedFuture(getDiscoverResult()))
                .thenReturn(CompletableFuture.completedFuture(toolResult))
                .thenReturn(CompletableFuture.completedFuture(toolResult));

        DefaultMcpClient client = new DefaultMcpClient.Builder()
                .transport(transport)
                .protocolVersion("2026-07-28")
                .metaSupplier(context -> Map.of(
                        "io.modelcontextprotocol/protocolVersion", "SHOULD-NOT-APPEAR",
                        "custom-key", "custom-value"))
                .build();

        client.executeTool(
                ToolExecutionRequest.builder().name("test").arguments("{}").build());

        ArgumentCaptor<McpCallContext> captor = ArgumentCaptor.forClass(McpCallContext.class);
        verify(transport, times(3)).executeOperationWithResponse(captor.capture());
        McpClientRequest toolRequest =
                (McpClientRequest) captor.getAllValues().get(2).message();
        Map<String, Object> meta = toolRequest.getParams().getMeta();

        // Protocol-level fields must take precedence over user-supplied values
        assertThat(meta.get("io.modelcontextprotocol/protocolVersion")).isEqualTo("2026-07-28");
        // User-supplied custom fields should still be present
        assertThat(meta.get("custom-key")).isEqualTo("custom-value");
    }

    // ========== MRTR tests ==========

    @Test
    public void mrtr_requestState_only_should_retry_and_succeed() throws Exception {
        final McpTransport transport = getModernMcpTransportMock();

        ObjectNode inputRequired = buildInputRequiredResponse(true, false);
        ObjectNode complete = buildToolCompleteResponse("done");

        when(transport.executeOperationWithResponse(any(McpCallContext.class)))
                .thenReturn(CompletableFuture.completedFuture(getDiscoverResult())) // discover
                .thenReturn(CompletableFuture.completedFuture(inputRequired)) // first tool call -> input_required
                .thenReturn(CompletableFuture.completedFuture(complete)); // retry -> complete

        DefaultMcpClient client = createMcpClient(transport);

        ToolExecutionResult result = client.executeTool(
                ToolExecutionRequest.builder().name("test").arguments("{}").build());
        assertThat(result.resultText()).isEqualTo("done");

        // 4 calls: discover, first tool call, retry
        verify(transport, times(3)).executeOperationWithResponse(any(McpCallContext.class));
    }

    @Test
    public void mrtr_with_inputRequests_should_throw() throws Exception {
        final McpTransport transport = getModernMcpTransportMock();

        ObjectNode inputRequired = buildInputRequiredResponse(true, true);

        when(transport.executeOperationWithResponse(any(McpCallContext.class)))
                .thenReturn(CompletableFuture.completedFuture(getDiscoverResult()))
                .thenReturn(CompletableFuture.completedFuture(inputRequired));

        DefaultMcpClient client = createMcpClient(transport);

        assertThatThrownBy(() -> client.executeTool(ToolExecutionRequest.builder()
                        .name("test")
                        .arguments("{}")
                        .build()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("inputRequests");
    }

    @Test
    public void mrtr_max_retries_exceeded_should_throw() throws Exception {
        final McpTransport transport = getModernMcpTransportMock();

        ObjectNode inputRequired = buildInputRequiredResponse(true, false);

        when(transport.executeOperationWithResponse(any(McpCallContext.class)))
                .thenReturn(CompletableFuture.completedFuture(getDiscoverResult()))
                .thenReturn(CompletableFuture.completedFuture(inputRequired)) // attempt 1
                .thenReturn(CompletableFuture.completedFuture(inputRequired)) // retry 1
                .thenReturn(CompletableFuture.completedFuture(inputRequired)) // retry 2
                .thenReturn(CompletableFuture.completedFuture(inputRequired)); // retry 3 -> exceeds limit

        DefaultMcpClient client = createMcpClient(transport);

        assertThatThrownBy(() -> client.executeTool(ToolExecutionRequest.builder()
                        .name("test")
                        .arguments("{}")
                        .build()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("retry limit");
    }

    private static DefaultMcpClient createMcpClient(McpTransport transport) {
        DefaultMcpClient client = new DefaultMcpClient.Builder()
                .transport(transport)
                .protocolVersion("2026-07-28")
                .subscribeToPromptListChanges(false)
                .subscribeToResourceListChanges(false)
                .subscribeToToolListChanges(false)
                .build();
        return client;
    }

    @Test
    public void mrtr_without_requestState_or_inputRequests_should_throw() throws Exception {
        final McpTransport transport = getModernMcpTransportMock();

        ObjectNode inputRequired = buildInputRequiredResponse(false, false);

        when(transport.executeOperationWithResponse(any(McpCallContext.class)))
                .thenReturn(CompletableFuture.completedFuture(getDiscoverResult()))
                .thenReturn(CompletableFuture.completedFuture(inputRequired));

        DefaultMcpClient client = createMcpClient(transport);

        assertThatThrownBy(() -> client.executeTool(ToolExecutionRequest.builder()
                        .name("test")
                        .arguments("{}")
                        .build()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("without requestState");
    }

    @Test
    public void mrtr_unknown_resultType_should_throw() throws Exception {
        final McpTransport transport = getModernMcpTransportMock();

        ObjectNode unknownType = JsonNodeFactory.instance.objectNode();
        ObjectNode result = unknownType.putObject("result");
        result.put("resultType", "partial");
        result.putArray("content").addObject().put("type", "text").put("text", "ok");

        when(transport.executeOperationWithResponse(any(McpCallContext.class)))
                .thenReturn(CompletableFuture.completedFuture(getDiscoverResult()))
                .thenReturn(CompletableFuture.completedFuture(unknownType));

        DefaultMcpClient client = createMcpClient(transport);

        assertThatThrownBy(() -> client.executeTool(ToolExecutionRequest.builder()
                        .name("test")
                        .arguments("{}")
                        .build()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Unexpected resultType");
    }

    private static ObjectNode buildInputRequiredResponse(boolean withRequestState, boolean withInputRequests) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        ObjectNode result = node.putObject("result");
        result.put("resultType", "input_required");
        if (withRequestState) {
            result.put("requestState", "opaque-state-token");
        }
        if (withInputRequests) {
            ObjectNode inputRequests = result.putObject("inputRequests");
            ObjectNode req = inputRequests.putObject("req1");
            req.put("method", "sampling/createMessage");
            req.putObject("params");
        }
        return node;
    }

    private static ObjectNode buildToolCompleteResponse(String text) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        ObjectNode result = node.putObject("result");
        result.put("resultType", "complete");
        result.putArray("content").addObject().put("type", "text").put("text", text);
        return node;
    }

    @Test
    public void silent_server_falls_back_to_legacy_within_the_protocol_detection_timeout() {
        // a server that simply ignores the unknown server/discover request
        McpTransport transport = getMinimalMcpTransportMock();
        when(transport.executeOperationWithResponse(any(McpCallContext.class)))
                .thenAnswer(invocation -> new CompletableFuture<>());

        long start = System.currentTimeMillis();
        DefaultMcpClient client = new DefaultMcpClient.Builder()
                .transport(transport)
                // the two timeouts are kept far apart so that the assertion below distinguishes
                // them by a wide margin instead of by a few hundred milliseconds, which a loaded
                // machine cannot be trusted to honour
                .protocolDetectionTimeout(java.time.Duration.ofMillis(200))
                .initializationTimeout(java.time.Duration.ofMinutes(2))
                .build();
        long elapsed = System.currentTimeMillis() - start;

        assertThat(client.isModernProtocol()).isFalse();
        verify(transport).initialize(any());
        // waiting anywhere near the initialization timeout means detection ignored its own
        assertThat(elapsed).isLessThan(30_000);
    }

    @Test
    public void protocol_detection_falls_back_to_the_initialization_timeout() throws Exception {
        McpTransport transport = getMinimalMcpTransportMock();
        when(transport.executeOperationWithResponse(any(McpCallContext.class)))
                .thenAnswer(invocation -> new CompletableFuture<>());

        DefaultMcpClient client = new DefaultMcpClient.Builder()
                .transport(transport)
                .initializationTimeout(java.time.Duration.ofMillis(200))
                .build();

        // Without an explicit protocolDetectionTimeout the detection request must not impose a
        // shorter timeout of its own, or a server that is slow to boot is misdetected as legacy.
        // This is read from the field rather than measured, because timing how long the client
        // took cannot tell the two timeouts apart reliably on a loaded machine.
        assertThat(readDuration(client, "protocolDetectionTimeout")).isEqualTo(java.time.Duration.ofMillis(200));
        assertThat(client.isModernProtocol()).isFalse();
        verify(transport).initialize(any());
    }

    private static java.time.Duration readDuration(DefaultMcpClient client, String fieldName) throws Exception {
        java.lang.reflect.Field field = DefaultMcpClient.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (java.time.Duration) field.get(client);
    }

    @Test
    public void explicit_protocol_version_skips_the_detection_request() {
        McpTransport transport = getMinimalMcpTransportMock();

        DefaultMcpClient client = new DefaultMcpClient.Builder()
                .transport(transport)
                .protocolVersion("2025-11-25")
                .build();

        assertThat(client.isModernProtocol()).isFalse();
        verify(transport).initialize(any());
        verify(transport, never()).executeOperationWithResponse(any(McpCallContext.class));
    }

    private static McpTransport getMinimalMcpTransportMock() {
        McpTransport transport = mock(McpTransport.class);
        when(transport.requiresCancellationNotification()).thenReturn(true);
        ObjectNode emptyJsonNode = JsonNodeFactory.instance.objectNode();
        when(transport.initialize(any())).thenReturn(CompletableFuture.completedFuture(emptyJsonNode));
        return transport;
    }

    private static ObjectNode getDiscoverResult() {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        ObjectNode result = node.putObject("result");
        result.putArray("supportedVersions").add("2026-07-28");
        result.putObject("capabilities");
        result.put("resultType", "complete");
        return node;
    }

    private static McpTransport getModernStdioTransportMock() {
        McpTransport transport = mock(McpTransport.class);
        when(transport.requiresCancellationNotification()).thenReturn(true);
        ObjectNode discoverResult = getDiscoverResult();
        when(transport.executeOperationWithResponse(any(McpCallContext.class)))
                .thenReturn(CompletableFuture.completedFuture(discoverResult));
        return transport;
    }

    private static McpTransport getModernHttpTransportMock() {
        McpTransport transport = mock(McpTransport.class);
        when(transport.requiresCancellationNotification()).thenReturn(false);
        ObjectNode discoverResult = getDiscoverResult();
        when(transport.executeOperationWithResponse(any(McpCallContext.class)))
                .thenReturn(CompletableFuture.completedFuture(discoverResult));
        return transport;
    }

    private static McpTransport getLegacyHttpTransportMock() {
        McpTransport transport = mock(McpTransport.class);
        when(transport.requiresCancellationNotification()).thenReturn(false);
        ObjectNode emptyJsonNode = JsonNodeFactory.instance.objectNode();
        when(transport.initialize(any())).thenReturn(CompletableFuture.completedFuture(emptyJsonNode));
        return transport;
    }

    private static McpTransport getModernMcpTransportMock() {
        return getModernStdioTransportMock();
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

    @Test
    public void activeSubscriptions_cleared_after_unsubscribe() throws Exception {
        McpTransport transport = getModernMcpTransportMock();

        // The subscription request gets a future that never completes (simulating an open SSE stream)
        CompletableFuture<JsonNode> sseStream = new CompletableFuture<>();
        when(transport.executeOperationWithResponse(any(McpCallContext.class)))
                .thenReturn(CompletableFuture.completedFuture(getDiscoverResult()))
                .thenReturn(sseStream);

        DefaultMcpClient client = createMcpClient(transport);
        long subscriptionId = client.subscribeToResources(List.of("file:///test"));

        Map<Long, ?> activeSubscriptions = getActiveSubscriptions(client);
        assertThat(activeSubscriptions).containsKey(subscriptionId);

        client.unsubscribeFromResources(subscriptionId);
        assertThat(activeSubscriptions).doesNotContainKey(subscriptionId);
    }

    @Test
    public void unsubscribe_over_http_does_not_send_cancellation_notification() throws Exception {
        McpTransport transport = getModernHttpTransportMock();

        CompletableFuture<JsonNode> sseStream = new CompletableFuture<>();
        when(transport.executeOperationWithResponse(any(McpCallContext.class)))
                .thenReturn(CompletableFuture.completedFuture(getDiscoverResult()))
                .thenReturn(sseStream);

        DefaultMcpClient client = new DefaultMcpClient.Builder()
                .transport(transport)
                .protocolVersion("2026-07-28")
                .subscribeToToolListChanges(false)
                .subscribeToPromptListChanges(false)
                .subscribeToResourceListChanges(false)
                .build();

        long subscriptionId = client.subscribeToResources(List.of("file:///test"));
        client.unsubscribeFromResources(subscriptionId);

        assertThat(sseStream.isCancelled()).isTrue();
        verify(transport, never()).executeOperationWithoutResponse(any(McpClientMessage.class));
    }

    @Test
    public void unsubscribe_over_stdio_sends_cancellation_notification() throws Exception {
        McpTransport transport = getModernStdioTransportMock();

        CompletableFuture<JsonNode> sseStream = new CompletableFuture<>();
        when(transport.executeOperationWithResponse(any(McpCallContext.class)))
                .thenReturn(CompletableFuture.completedFuture(getDiscoverResult()))
                .thenReturn(sseStream);

        DefaultMcpClient client = new DefaultMcpClient.Builder()
                .transport(transport)
                .protocolVersion("2026-07-28")
                .subscribeToToolListChanges(false)
                .subscribeToPromptListChanges(false)
                .subscribeToResourceListChanges(false)
                .build();

        long subscriptionId = client.subscribeToResources(List.of("file:///test"));
        client.unsubscribeFromResources(subscriptionId);

        assertThat(sseStream.isCancelled()).isTrue();
        verify(transport, times(1)).executeOperationWithoutResponse(any(McpClientMessage.class));
    }

    @Test
    public void activeSubscriptions_cleared_after_server_rejection() throws Exception {
        McpTransport transport = getModernMcpTransportMock();

        // The subscription request completes immediately with a JSON-RPC error
        ObjectNode errorResponse = JsonNodeFactory.instance.objectNode();
        ObjectNode error = errorResponse.putObject("error");
        error.put("code", -32602);
        error.put("message", "Unknown resource URI");
        when(transport.executeOperationWithResponse(any(McpCallContext.class)))
                .thenReturn(CompletableFuture.completedFuture(getDiscoverResult()))
                .thenReturn(CompletableFuture.completedFuture(errorResponse));

        DefaultMcpClient client = createMcpClient(transport);
        long subscriptionId = client.subscribeToResources(List.of("file:///nonexistent"));

        // The whenComplete handler runs asynchronously — wait for it
        Map<Long, ?> activeSubscriptions = getActiveSubscriptions(client);
        org.awaitility.Awaitility.await()
                .atMost(java.time.Duration.ofSeconds(2))
                .untilAsserted(() -> assertThat(activeSubscriptions).doesNotContainKey(subscriptionId));
    }

    @Test
    public void activeSubscriptions_cleared_after_transport_error() throws Exception {
        McpTransport transport = getModernMcpTransportMock();

        // The subscription request fails with a transport error
        CompletableFuture<JsonNode> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("Connection refused"));
        when(transport.executeOperationWithResponse(any(McpCallContext.class)))
                .thenReturn(CompletableFuture.completedFuture(getDiscoverResult()))
                .thenReturn(failed);

        DefaultMcpClient client = createMcpClient(transport);
        long subscriptionId = client.subscribeToResources(List.of("file:///test"));

        Map<Long, ?> activeSubscriptions = getActiveSubscriptions(client);
        org.awaitility.Awaitility.await()
                .atMost(java.time.Duration.ofSeconds(2))
                .untilAsserted(() -> assertThat(activeSubscriptions).doesNotContainKey(subscriptionId));
    }

    @Test
    public void timeout_over_http_does_not_send_cancellation_notification_modern() throws Exception {
        McpTransport transport = getModernHttpTransportMock();

        CompletableFuture<JsonNode> neverCompletes = new CompletableFuture<>();
        when(transport.executeOperationWithResponse(any(McpCallContext.class)))
                .thenReturn(CompletableFuture.completedFuture(getDiscoverResult()))
                .thenReturn(neverCompletes);

        DefaultMcpClient client = new DefaultMcpClient.Builder()
                .transport(transport)
                .protocolVersion("2026-07-28")
                .toolExecutionTimeout(java.time.Duration.ofMillis(100))
                .subscribeToToolListChanges(false)
                .subscribeToPromptListChanges(false)
                .subscribeToResourceListChanges(false)
                .build();

        client.executeTool(
                ToolExecutionRequest.builder().name("slowTool").arguments("{}").build());

        assertThat(neverCompletes.isCancelled()).isTrue();
        verify(transport, never()).executeOperationWithoutResponse(any(McpClientMessage.class));
    }

    @Test
    public void timeout_over_http_sends_cancellation_notification_legacy() throws Exception {
        McpTransport transport = getLegacyHttpTransportMock();

        CompletableFuture<JsonNode> neverCompletes = new CompletableFuture<>();
        when(transport.executeOperationWithResponse(any(McpCallContext.class))).thenReturn(neverCompletes);

        DefaultMcpClient client = new DefaultMcpClient.Builder()
                .transport(transport)
                .protocolVersion("2025-11-25")
                .toolExecutionTimeout(java.time.Duration.ofMillis(100))
                .build();

        client.executeTool(
                ToolExecutionRequest.builder().name("slowTool").arguments("{}").build());

        assertThat(neverCompletes.isCancelled()).isTrue();
        verify(transport, times(1)).executeOperationWithoutResponse(any(McpClientMessage.class));
    }

    @Test
    public void timeout_over_stdio_sends_cancellation_notification_modern() throws Exception {
        McpTransport transport = getModernStdioTransportMock();

        CompletableFuture<JsonNode> neverCompletes = new CompletableFuture<>();
        when(transport.executeOperationWithResponse(any(McpCallContext.class)))
                .thenReturn(CompletableFuture.completedFuture(getDiscoverResult()))
                .thenReturn(neverCompletes);

        DefaultMcpClient client = new DefaultMcpClient.Builder()
                .transport(transport)
                .protocolVersion("2026-07-28")
                .toolExecutionTimeout(java.time.Duration.ofMillis(100))
                .subscribeToToolListChanges(false)
                .subscribeToPromptListChanges(false)
                .subscribeToResourceListChanges(false)
                .build();

        client.executeTool(
                ToolExecutionRequest.builder().name("slowTool").arguments("{}").build());

        assertThat(neverCompletes.isCancelled()).isTrue();
        verify(transport, times(1)).executeOperationWithoutResponse(any(McpClientMessage.class));
    }

    @Test
    public void timeout_over_stdio_sends_cancellation_notification_legacy() throws Exception {
        McpTransport transport = getMinimalMcpTransportMock();

        CompletableFuture<JsonNode> neverCompletes = new CompletableFuture<>();
        when(transport.executeOperationWithResponse(any(McpCallContext.class))).thenReturn(neverCompletes);

        DefaultMcpClient client = new DefaultMcpClient.Builder()
                .transport(transport)
                .protocolVersion("2025-11-25")
                .toolExecutionTimeout(java.time.Duration.ofMillis(100))
                .build();

        client.executeTool(
                ToolExecutionRequest.builder().name("slowTool").arguments("{}").build());

        assertThat(neverCompletes.isCancelled()).isTrue();
        verify(transport, times(1)).executeOperationWithoutResponse(any(McpClientMessage.class));
    }

    @SuppressWarnings("unchecked")
    private static Map<Long, ?> getActiveSubscriptions(DefaultMcpClient client) throws Exception {
        java.lang.reflect.Field field = DefaultMcpClient.class.getDeclaredField("activeSubscriptions");
        field.setAccessible(true);
        return (Map<Long, ?>) field.get(client);
    }

    private static record ToolDefinition(String name, String description, ToolArg... args) {}

    private static record ToolArg(String name, String type, String description) {}
}
