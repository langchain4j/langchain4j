package dev.langchain4j.mcp.client.logging;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.mcp.client.transport.McpJson;
import java.util.Map;
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

        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) McpJson.toMap(json).get("params");

        McpLogMessage message = McpLogMessage.fromMap(params);
        assertThat(message.level()).isEqualTo(McpLogLevel.from("info"));
        assertThat(message.dataAsObject()).isEqualTo("Searching DuckDuckGo for: length of pont des arts in meters");
        assertThat(message.logger()).isNull();
    }
}
