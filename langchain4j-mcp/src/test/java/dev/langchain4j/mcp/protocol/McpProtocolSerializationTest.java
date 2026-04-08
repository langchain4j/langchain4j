package dev.langchain4j.mcp.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class McpProtocolSerializationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void should_serialize_error_response_omitting_null_data() throws Exception {
        // given
        McpErrorResponse response =
                new McpErrorResponse(1L, new McpErrorResponse.Error(-32601, "Method not found", null));

        // when
        JsonNode json = OBJECT_MAPPER.readTree(OBJECT_MAPPER.writeValueAsString(response));

        // then
        assertThat(json.get("jsonrpc").asText()).isEqualTo("2.0");
        assertThat(json.get("id").asLong()).isEqualTo(1L);
        assertThat(json.get("error").get("code").asInt()).isEqualTo(-32601);
        assertThat(json.get("error").get("message").asText()).isEqualTo("Method not found");
        assertThat(json.get("error").has("data")).isFalse();
    }

    @Test
    void should_serialize_call_tool_result_omitting_null_fields() throws Exception {
        // given
        McpCallToolResult response = new McpCallToolResult(
                7L, new McpCallToolResult.Result(List.of(new McpCallToolResult.Content("text", "ok")), null, null));

        // when
        JsonNode json = OBJECT_MAPPER.readTree(OBJECT_MAPPER.writeValueAsString(response));

        // then
        assertThat(json.get("jsonrpc").asText()).isEqualTo("2.0");
        assertThat(json.get("id").asLong()).isEqualTo(7L);
        assertThat(json.get("result").get("content").get(0).get("type").asText())
                .isEqualTo("text");
        assertThat(json.get("result").get("content").get(0).get("text").asText())
                .isEqualTo("ok");
        assertThat(json.get("result").has("structuredContent")).isFalse();
        assertThat(json.get("result").has("isError")).isFalse();
    }

    @Test
    void should_serialize_initialize_params_with_mcp_implementation_client_info() throws Exception {
        // given
        McpInitializeParams params = new McpInitializeParams();
        params.setProtocolVersion("2025-06-18");
        params.setClientInfo(new McpImplementation("client", "1.0", "Client Title"));

        // when
        JsonNode json = OBJECT_MAPPER.readTree(OBJECT_MAPPER.writeValueAsString(params));

        // then
        assertThat(json.get("protocolVersion").asText()).isEqualTo("2025-06-18");
        assertThat(json.get("clientInfo").get("name").asText()).isEqualTo("client");
        assertThat(json.get("clientInfo").get("version").asText()).isEqualTo("1.0");
        assertThat(json.get("clientInfo").get("title").asText()).isEqualTo("Client Title");
    }

    @Test
    void should_serialize_call_tool_request() throws Exception {
        ObjectNode args = OBJECT_MAPPER.createObjectNode();
        args.put("location", "Prague");
        McpCallToolRequest request = new McpCallToolRequest(1L, "get_weather", args);

        JsonNode json = OBJECT_MAPPER.readTree(OBJECT_MAPPER.writeValueAsString(request));

        assertThat(json.get("jsonrpc").asText()).isEqualTo("2.0");
        assertThat(json.get("id").asLong()).isEqualTo(1L);
        assertThat(json.get("method").asText()).isEqualTo("tools/call");
        assertThat(json.get("params").get("name").asText()).isEqualTo("get_weather");
        assertThat(json.get("params").get("arguments").get("location").asText()).isEqualTo("Prague");
        assertThat(json.get("params").has("_meta")).isFalse();
    }

    @Test
    void should_serialize_call_tool_request_with_meta() throws Exception {
        ObjectNode args = OBJECT_MAPPER.createObjectNode();
        args.put("location", "Prague");
        McpCallToolRequest request = new McpCallToolRequest(2L, "get_weather", args);
        request.getParams().setMeta(Map.of("traceparent", "00-0af7651916cd43dd8448eb211c80319c-00f067aa0ba902b7-01"));

        JsonNode json = OBJECT_MAPPER.readTree(OBJECT_MAPPER.writeValueAsString(request));

        assertThat(json.get("params").get("_meta").get("traceparent").asText())
                .isEqualTo("00-0af7651916cd43dd8448eb211c80319c-00f067aa0ba902b7-01");
        assertThat(json.get("params").get("name").asText()).isEqualTo("get_weather");
    }

    @Test
    void should_serialize_cancellation_notification() throws Exception {
        McpCancellationNotification notification = new McpCancellationNotification(42L, "Timeout");

        JsonNode json = OBJECT_MAPPER.readTree(OBJECT_MAPPER.writeValueAsString(notification));

        assertThat(json.has("id")).isFalse();
        assertThat(json.get("method").asText()).isEqualTo("notifications/cancelled");
        assertThat(json.get("params").get("requestId").asLong()).isEqualTo(42L);
        assertThat(json.get("params").get("reason").asText()).isEqualTo("Timeout");
    }

    @Test
    void should_serialize_cancellation_notification_without_reason() throws Exception {
        McpCancellationNotification notification = new McpCancellationNotification(42L, null);

        JsonNode json = OBJECT_MAPPER.readTree(OBJECT_MAPPER.writeValueAsString(notification));

        assertThat(json.get("params").get("requestId").asLong()).isEqualTo(42L);
        assertThat(json.get("params").has("reason")).isFalse();
    }

    @Test
    void should_serialize_get_prompt_request() throws Exception {
        McpGetPromptRequest request = new McpGetPromptRequest(3L, "summarize", Map.of("text", "hello"));

        JsonNode json = OBJECT_MAPPER.readTree(OBJECT_MAPPER.writeValueAsString(request));

        assertThat(json.get("method").asText()).isEqualTo("prompts/get");
        assertThat(json.get("params").get("name").asText()).isEqualTo("summarize");
        assertThat(json.get("params").get("arguments").get("text").asText()).isEqualTo("hello");
    }

    @Test
    void should_serialize_read_resource_request() throws Exception {
        McpReadResourceRequest request = new McpReadResourceRequest(4L, "file:///tmp/test.txt");

        JsonNode json = OBJECT_MAPPER.readTree(OBJECT_MAPPER.writeValueAsString(request));

        assertThat(json.get("method").asText()).isEqualTo("resources/read");
        assertThat(json.get("params").get("uri").asText()).isEqualTo("file:///tmp/test.txt");
    }

    @Test
    void should_serialize_list_tools_request_without_cursor() throws Exception {
        McpListToolsRequest request = new McpListToolsRequest(5L, null);

        JsonNode json = OBJECT_MAPPER.readTree(OBJECT_MAPPER.writeValueAsString(request));

        assertThat(json.get("method").asText()).isEqualTo("tools/list");
        assertThat(json.has("params")).isFalse();
    }

    @Test
    void should_serialize_list_tools_request_with_cursor() throws Exception {
        McpListToolsRequest request = new McpListToolsRequest(5L, "next_page");

        JsonNode json = OBJECT_MAPPER.readTree(OBJECT_MAPPER.writeValueAsString(request));

        assertThat(json.get("method").asText()).isEqualTo("tools/list");
        assertThat(json.get("params").get("cursor").asText()).isEqualTo("next_page");
    }

    @Test
    void should_serialize_ping_request_without_params() throws Exception {
        McpPingRequest request = new McpPingRequest(6L);

        JsonNode json = OBJECT_MAPPER.readTree(OBJECT_MAPPER.writeValueAsString(request));

        assertThat(json.get("method").asText()).isEqualTo("ping");
        assertThat(json.has("params")).isFalse();
    }

    @Test
    void should_serialize_initialize_request() throws Exception {
        McpInitializeRequest request = new McpInitializeRequest(7L);
        McpInitializeParams params = new McpInitializeParams();
        params.setProtocolVersion("2025-06-18");
        params.setClientInfo(new McpImplementation("test", "1.0", null));
        request.setParams(params);

        JsonNode json = OBJECT_MAPPER.readTree(OBJECT_MAPPER.writeValueAsString(request));

        assertThat(json.get("method").asText()).isEqualTo("initialize");
        assertThat(json.get("params").get("protocolVersion").asText()).isEqualTo("2025-06-18");
        assertThat(json.get("params").get("clientInfo").get("name").asText()).isEqualTo("test");
    }

    @Test
    void should_serialize_initialize_result_with_server_info() throws Exception {
        // given
        McpImplementation serverInfo = new McpImplementation("server", "1.0", "Server Title");
        McpInitializeResult.Capabilities.Tools tools = new McpInitializeResult.Capabilities.Tools(true);
        McpInitializeResult.Capabilities capabilities = new McpInitializeResult.Capabilities(tools);
        McpInitializeResult.Result result = new McpInitializeResult.Result("2025-06-18", capabilities, serverInfo);
        McpInitializeResult response = new McpInitializeResult(1L, result);

        // when
        JsonNode json = OBJECT_MAPPER.readTree(OBJECT_MAPPER.writeValueAsString(response));

        // then
        assertThat(json.get("jsonrpc").asText()).isEqualTo("2.0");
        assertThat(json.get("id").asLong()).isEqualTo(1L);
        assertThat(json.get("result").get("protocolVersion").asText()).isEqualTo("2025-06-18");
        assertThat(json.get("result").get("serverInfo").get("name").asText()).isEqualTo("server");
        assertThat(json.get("result").get("serverInfo").get("version").asText()).isEqualTo("1.0");
        assertThat(json.get("result").get("serverInfo").get("title").asText()).isEqualTo("Server Title");
        assertThat(json.get("result")
                        .get("capabilities")
                        .get("tools")
                        .get("listChanged")
                        .asBoolean())
                .isTrue();
    }
}
