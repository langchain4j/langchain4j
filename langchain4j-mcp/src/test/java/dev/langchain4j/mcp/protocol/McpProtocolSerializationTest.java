package dev.langchain4j.mcp.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
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
