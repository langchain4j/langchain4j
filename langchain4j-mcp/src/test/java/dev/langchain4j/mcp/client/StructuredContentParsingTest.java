package dev.langchain4j.mcp.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.service.tool.ToolExecutionResult;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class StructuredContentParsingTest {

    ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void testComplexObject() throws JsonProcessingException {
        // JSON
        String response =
                """
                {
                  "jsonrpc": "2.0",
                  "id": 2,
                  "result": {
                    "isError": false,
                    "structuredContent": {
                      "integer": 1,
                      "string": "hello",
                      "boolean": true,
                      "innerObject": {
                        "double": 1.0,
                        "null": null
                      }
                    }
                  }
                }
                """;
        JsonNode responseNode = objectMapper.readTree(response);
        ToolExecutionResult toolExecutionResult = ToolExecutionHelper.extractResult(responseNode, false);
        assertThat(toolExecutionResult.result()).isInstanceOf(Map.class);
        Map<String, Object> map = (Map<String, Object>) toolExecutionResult.result();
        assertThat(map).hasSize(4);
        assertThat(map.get("integer")).isEqualTo(1);
        assertThat(map.get("string")).isEqualTo("hello");
        assertThat(map.get("boolean")).isEqualTo(true);
        assertThat(map.get("innerObject")).isInstanceOf(Map.class);
        Map<String, Object> innerMap = (Map<String, Object>) map.get("innerObject");
        assertThat(innerMap).hasSize(2);
        assertThat(innerMap.get("double")).isEqualTo(1.0);
        assertThat(innerMap.containsKey("null")).isTrue();
        assertThat(innerMap.get("null")).isNull();
    }
}
