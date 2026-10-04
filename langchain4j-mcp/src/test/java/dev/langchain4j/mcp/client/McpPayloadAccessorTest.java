package dev.langchain4j.mcp.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import dev.langchain4j.mcp.client.logging.McpLogLevel;
import dev.langchain4j.mcp.client.logging.McpLogMessage;
import dev.langchain4j.mcp.protocol.McpInitializeResult;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * JSON-RPC allows {@code error.data} to be any value and MCP allows a log payload to be any
 * JSON-serializable value, so the accessors have to cope with more than an object.
 */
class McpPayloadAccessorTest {

    @Test
    void errorDataAsMap_should_return_null_rather_than_throw_for_a_non_object() {
        for (String data : new String[] {"\"boom\"", "[1,2]", "42", "true", "null"}) {
            McpException e = McpException.withErrorData(1, "m", data);
            assertThatCode(e::errorDataAsMap).as(data).doesNotThrowAnyException();
            assertThat(e.errorDataAsMap()).as(data).isNull();
        }
    }

    @Test
    void errorDataAsObject_should_expose_values_that_are_not_objects() {
        assertThat(McpException.withErrorData(1, "m", "\"boom\"").errorDataAsObject()).isEqualTo("boom");
        assertThat(McpException.withErrorData(1, "m", "42").errorDataAsObject()).isEqualTo(42);
        assertThat(McpException.withErrorData(1, "m", "[1,2]").errorDataAsObject()).isEqualTo(java.util.List.of(1, 2));
        assertThat(McpException.withErrorData(1, "m", "{\"k\":1}").errorDataAsMap()).containsEntry("k", 1);
        assertThat(McpException.withErrorData(1, "m", null).errorDataAsObject()).isNull();
    }

    @Test
    void logMessage_should_expose_a_scalar_payload() {
        // the common case: a server logs a plain string, which is not a map
        McpLogMessage message = new McpLogMessage(McpLogLevel.INFO, "lg", "plain text line");

        assertThat(message.dataAsMap()).isNull();
        assertThat(message.dataAsObject()).isEqualTo("plain text line");
        assertThat(message.dataAsJson()).isEqualTo("\"plain text line\"");
    }

    @Test
    void initialize_instructions_should_not_depend_on_a_jackson_default() {
        // ALLOW_FINAL_FIELDS_AS_MUTATORS is on by default in Jackson 2 and off in Jackson 3, so a
        // final field bound behind the getter rather than through the creator would silently
        // become null on the very migration this API exists to enable.
        String response =
                """
                {"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2025-06-18","capabilities":{},
                 "serverInfo":{"name":"s","version":"1"},"instructions":"hello"}}""";

        JsonMapper strict = JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .disable(MapperFeature.ALLOW_FINAL_FIELDS_AS_MUTATORS)
                .build();

        assertThatCode(() -> {
                    McpInitializeResult result = strict.readValue(response, McpInitializeResult.class);
                    assertThat(result.getResult().getInstructions()).isEqualTo("hello");
                })
                .doesNotThrowAnyException();
    }

    @Test
    void a_map_payload_still_reads_as_a_map() {
        McpLogMessage message = new McpLogMessage(McpLogLevel.INFO, "lg", Map.of("k", "v"));
        assertThat(message.dataAsMap()).containsEntry("k", "v");
    }
}
