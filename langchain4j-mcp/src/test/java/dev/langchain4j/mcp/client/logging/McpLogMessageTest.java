package dev.langchain4j.mcp.client.logging;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.mcp.client.transport.McpJson;
import org.junit.jupiter.api.Test;

class McpLogMessageTest {

    @Test
    void logMessageWithoutLogger() {
        String json =
                """
                {
                  "method" : "notifications/message",
                  "params" : {
                    "level" : "info",
                    "data" : "Searching DuckDuckGo for: length of pont des arts in meters"
                  },
                  "jsonrpc" : "2.0"
                }
                """;

        JsonNode jsonNode = toJsonNode(json);

        McpLogMessage message = McpLogMessage.fromJson(jsonNode.get("params"));
        assertThat(message.level()).isEqualTo(McpLogLevel.from("info"));
        assertThat(message.data()).isEqualTo(jsonNode.get("params").get("data"));
        assertThat(message.logger()).isNull();
    }

    private static JsonNode toJsonNode(String json) {
        return McpJson.parse(json);
    }
}
