package dev.langchain4j.mcp.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

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
        McpToolResultExtractor extractor = mock(McpToolResultExtractor.class);
        ToolExecutionResult toolExecutionResult = ToolExecutionHelper.extractResult(responseNode, false, extractor);
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
        verifyNoInteractions(extractor);
    }

    @Test
    public void testStructuredContentWithArrays() throws JsonProcessingException {
        String response =
                """
                {
                  "jsonrpc": "2.0",
                  "id": 3,
                  "result": {
                    "structuredContent": {
                      "items": [1, 2, 3],
                      "nested": {
                        "labels": ["a", "b"]
                      }
                    }
                  }
                }
                """;

        JsonNode responseNode = objectMapper.readTree(response);
        McpToolResultExtractor extractor = mock(McpToolResultExtractor.class);

        ToolExecutionResult toolExecutionResult = ToolExecutionHelper.extractResult(responseNode, false, extractor);

        assertThat(toolExecutionResult.result()).isInstanceOf(Map.class);
        Map<String, Object> map = (Map<String, Object>) toolExecutionResult.result();
        assertThat(map.get("items")).isEqualTo(java.util.List.of(1, 2, 3));
        assertThat(map.get("nested")).isInstanceOf(Map.class);
        assertThat(((Map<String, Object>) map.get("nested")).get("labels"))
                .isEqualTo(java.util.List.of("a", "b"));
        verifyNoInteractions(extractor);
    }

    @Test
    public void should_prefer_structured_content_over_content_array() throws JsonProcessingException {
        String response =
                """
                {
                  "jsonrpc": "2.0",
                  "id": 4,
                  "result": {
                    "structuredContent": {
                      "source": "structured"
                    },
                    "content": [
                      {
                        "type": "text",
                        "text": "{\\"source\\":\\"text\\"}"
                      }
                    ]
                  }
                }
                """;

        JsonNode responseNode = objectMapper.readTree(response);
        McpToolResultExtractor extractor = mock(McpToolResultExtractor.class);

        ToolExecutionResult toolExecutionResult = ToolExecutionHelper.extractResult(responseNode, false, extractor);

        assertThat(toolExecutionResult.result()).isEqualTo(Map.of("source", "structured"));
        assertThat(toolExecutionResult.resultText()).isEqualTo("{\"source\":\"structured\"}");
        verifyNoInteractions(extractor);
    }
}
