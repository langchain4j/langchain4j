package dev.langchain4j.mcp.client;

import static dev.langchain4j.mcp.McpFields.PROMPTS_FIELD;
import static dev.langchain4j.mcp.McpFields.RESULT_FIELD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import org.junit.jupiter.api.Test;

class PromptsHelperTest {

    @Test
    void should_parse_multiple_prompts_successfully_from_json_string() throws Exception {
        // given: JSON string representing the full MCP response
        String json =
                """
				{
				  "jsonrpc": "2.0",
				  "id": 1,
				  "result": {
				    "prompts": [
				      {
				        "name": "code_review",
				        "title": "Request Code Review",
				        "description": "Asks the LLM to analyze code quality and suggest improvements",
				        "arguments": [
				          {
				            "name": "code",
				            "description": "The code to review",
				            "required": true
				          }
				        ],
				        "icons": [
				          {
				            "src": "https://example.com/review-icon.svg",
				            "mimeType": "image/svg+xml",
				            "sizes": ["any"]
				          }
				        ]
				      },
				      {
				        "name": "bug_report",
				        "title": "Report a Bug",
				        "description": "Collects information about a bug to report to the team",
				        "arguments": [
				          {
				            "name": "steps",
				            "description": "Steps to reproduce the bug",
				            "required": true
				          }
				        ]
				      }
				    ],
				    "nextCursor": "next-page-cursor"
				  }
				}
				""";

        // convert string to JsonNode
        JsonNode root = DefaultMcpClient.OBJECT_MAPPER.readTree(json);

        // when
        List<McpPrompt> prompts = PromptsHelper.parsePromptRefs(root);

        // then
        assertThat(prompts).hasSize(2);

        // verify first prompt
        McpPrompt first = prompts.get(0);
        assertThat(first.name()).isEqualTo("code_review");
        assertThat(first.description()).isEqualTo("Asks the LLM to analyze code quality and suggest improvements");
        assertThat(first.arguments()).hasSize(1);
        assertThat(first.arguments().get(0).name()).isEqualTo("code");

        // verify second prompt
        McpPrompt second = prompts.get(1);
        assertThat(second.name()).isEqualTo("bug_report");
        assertThat(second.description()).isEqualTo("Collects information about a bug to report to the team");
        assertThat(second.arguments()).hasSize(1);
        assertThat(second.arguments().get(0).name()).isEqualTo("steps");
    }

    @Test
    void should_throw_exception_when_result_is_missing() {
        // given: empty JSON
        ObjectNode root = JsonNodeFactory.instance.objectNode();

        // when / then
        assertThatThrownBy(() -> PromptsHelper.parsePromptRefs(root))
                .isInstanceOf(IllegalResponseException.class)
                .hasMessageContaining(String.format("Result does not contain '%s'", RESULT_FIELD));
    }

    @Test
    void should_throw_exception_when_prompts_are_missing() {
        // given: JSON with "result" but no "prompts"
        ObjectNode root = JsonNodeFactory.instance.objectNode();
        root.putObject(RESULT_FIELD);

        // when / then
        assertThatThrownBy(() -> PromptsHelper.parsePromptRefs(root))
                .isInstanceOf(IllegalResponseException.class)
                .hasMessageContaining(String.format("Result does not contain '%s'", PROMPTS_FIELD));
    }

    @Test
    void should_handle_empty_prompts_array() {
        // given: JSON with empty prompts array
        ObjectNode root = JsonNodeFactory.instance.objectNode();
        ObjectNode resultNode = root.putObject(RESULT_FIELD);
        resultNode.putArray(PROMPTS_FIELD);

        // when
        List<McpPrompt> prompts = PromptsHelper.parsePromptRefs(root);

        // then
        assertThat(prompts).isEmpty();
    }
}
