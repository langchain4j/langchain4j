package dev.langchain4j.mcp.client;

import static dev.langchain4j.mcp.client.DefaultMcpClient.OBJECT_MAPPER;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import dev.langchain4j.service.tool.ToolExecutionResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class McpToolResultMetaParsingTest {

    private final McpToolResultExtractor extractor = new DefaultMcpToolResultExtractor();

    @Test
    void should_map_meta_of_text_result_into_attributes() throws Exception {
        JsonNode response = OBJECT_MAPPER.readTree(
                // language=json
                """
                        {
                          "jsonrpc": "2.0",
                          "id": 1,
                          "result": {
                            "content": [
                              {
                                "type": "text",
                                "text": "Sunny, 22 degrees"
                              }
                            ],
                            "_meta": {
                              "example.org/widget": {
                                "temperature": 22,
                                "conditions": ["sunny", "windy"]
                              },
                              "example.org/traceId": "abc-123"
                            }
                          }
                        }
                        """);

        ToolExecutionResult result = ToolExecutionHelper.extractResult(response, false, extractor);

        assertThat(result.resultText()).isEqualTo("Sunny, 22 degrees");
        assertThat(result.attributes())
                .containsEntry("example.org/traceId", "abc-123")
                .containsEntry(
                        "example.org/widget", Map.of("temperature", 22, "conditions", List.of("sunny", "windy")));
    }

    @Test
    void should_map_meta_of_structured_content_result_into_attributes() throws Exception {
        JsonNode response = OBJECT_MAPPER.readTree(
                // language=json
                """
                        {
                          "jsonrpc": "2.0",
                          "id": 2,
                          "result": {
                            "structuredContent": {
                              "temperature": 22
                            },
                            "_meta": {
                              "example.org/traceId": "abc-123"
                            }
                          }
                        }
                        """);

        ToolExecutionResult result = ToolExecutionHelper.extractResult(response, false, extractor);

        assertThat(result.result()).isEqualTo(Map.of("temperature", 22));
        assertThat(result.attributes()).containsExactly(Map.entry("example.org/traceId", "abc-123"));
    }

    @Test
    void should_return_no_attributes_when_there_is_no_meta() throws Exception {
        JsonNode response = OBJECT_MAPPER.readTree(
                // language=json
                """
                        {
                          "jsonrpc": "2.0",
                          "id": 3,
                          "result": {
                            "content": [
                              {
                                "type": "text",
                                "text": "Sunny, 22 degrees"
                              }
                            ]
                          }
                        }
                        """);

        ToolExecutionResult result = ToolExecutionHelper.extractResult(response, false, extractor);

        assertThat(result.attributes()).isEmpty();
    }

    @Test
    void should_keep_meta_of_error_result_when_application_level_errors_are_ignored() throws Exception {
        JsonNode response = OBJECT_MAPPER.readTree(
                // language=json
                """
                        {
                          "jsonrpc": "2.0",
                          "id": 4,
                          "result": {
                            "isError": true,
                            "content": [
                              {
                                "type": "text",
                                "text": "City not found"
                              }
                            ],
                            "_meta": {
                              "example.org/traceId": "abc-123"
                            }
                          }
                        }
                        """);

        ToolExecutionResult result = ToolExecutionHelper.extractResult(response, true, extractor);

        assertThat(result.isError()).isTrue();
        assertThat(result.resultText()).isEqualTo("City not found");
        assertThat(result.attributes()).containsExactly(Map.entry("example.org/traceId", "abc-123"));
    }

    @Test
    void should_give_precedence_to_attributes_set_by_the_extractor() throws Exception {
        JsonNode response = OBJECT_MAPPER.readTree(
                // language=json
                """
                        {
                          "jsonrpc": "2.0",
                          "id": 5,
                          "result": {
                            "content": [
                              {
                                "type": "text",
                                "text": "Sunny, 22 degrees"
                              }
                            ],
                            "_meta": {
                              "source": "meta",
                              "example.org/traceId": "abc-123"
                            }
                          }
                        }
                        """);

        McpToolResultExtractor customExtractor = (content, isError) -> ToolExecutionResult.builder()
                .resultText("custom")
                .isError(isError)
                .attributes(Map.of("source", "extractor"))
                .build();

        ToolExecutionResult result = ToolExecutionHelper.extractResult(response, false, customExtractor);

        assertThat(result.resultText()).isEqualTo("custom");
        assertThat(result.attributes())
                .containsEntry("source", "extractor")
                .containsEntry("example.org/traceId", "abc-123");
    }

    @Test
    void should_skip_meta_keys_reserved_by_mcp() throws Exception {
        JsonNode response = OBJECT_MAPPER.readTree(
                // language=json
                """
                        {
                          "jsonrpc": "2.0",
                          "id": 6,
                          "result": {
                            "content": [
                              {
                                "type": "text",
                                "text": "Sunny, 22 degrees"
                              }
                            ],
                            "_meta": {
                              "io.modelcontextprotocol/serverInfo": {
                                "name": "weather-server",
                                "version": "1.0.0"
                              },
                              "org.modelcontextprotocol.api/hint": "reserved",
                              "com.mcp.tools/hint": "reserved",
                              "dev.mcp/hint": "reserved",
                              "com.example.mcp/traceId": "not-reserved",
                              "traceId": "not-reserved"
                            }
                          }
                        }
                        """);

        ToolExecutionResult result = ToolExecutionHelper.extractResult(response, false, extractor);

        assertThat(result.attributes()).containsOnlyKeys("com.example.mcp/traceId", "traceId");
    }
}
