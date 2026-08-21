package dev.langchain4j.mcp.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.mcp.client.McpDiscoverResult;
import org.junit.jupiter.api.Test;

class McpProtocolDtoTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void serverDiscoverRequestSerializes() throws Exception {
        McpServerDiscoverRequest request = new McpServerDiscoverRequest(1L);
        String json = MAPPER.writeValueAsString(request);
        assertThat(json).contains("\"method\":\"server/discover\"");
        assertThat(json).contains("\"id\":1");
    }

    @Test
    void subscriptionsListenRequestSerializes() throws Exception {
        McpSubscriptionsListenRequest request = new McpSubscriptionsListenRequest(2L);
        String json = MAPPER.writeValueAsString(request);
        assertThat(json).contains("\"method\":\"subscriptions/listen\"");
    }

    @Test
    void discoverResultDeserializes() throws Exception {
        String json = """
            {
                "supportedVersions": ["2026-07-28", "2025-11-25"],
                "capabilities": {"tools": {}},
                "resultType": "complete",
                "instructions": "Test server"
            }
            """;
        McpDiscoverResult result = MAPPER.readValue(json, McpDiscoverResult.class);
        assertThat(result.supportedVersions()).containsExactly("2026-07-28", "2025-11-25");
        assertThat(result.resultType()).isEqualTo("complete");
        assertThat(result.instructions()).isEqualTo("Test server");
    }
}
