package dev.langchain4j.mcp.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.transport.McpJson;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Reading the tool list as plain values rather than a Jackson tree dropped the coercions
 * JsonNode accessors performed. These pin the behaviour the tree model had.
 */
class McpSchemaCoercionTest {

    @SuppressWarnings("unchecked")
    private static ToolSpecification firstTool(String toolsArrayJson) {
        List<Map<String, Object>> tools = (List<Map<String, Object>>)
                McpJson.toMap("{\"tools\":" + toolsArrayJson + "}").get("tools");
        return ToolSpecificationHelper.toolSpecificationListFromMcpResponse(tools).get(0);
    }

    @Test
    void annotation_hints_should_accept_a_string_as_JsonNode_asBoolean_did() {
        ToolSpecification tool = firstTool(
                """
                [{"name":"t","description":"d","inputSchema":{"type":"object"},
                 "annotations":{"readOnlyHint":"true","destructiveHint":"false"}}]""");

        assertThat(tool.metadata()).containsEntry("readOnlyHint", true);
        assertThat(tool.metadata()).containsEntry("destructiveHint", false);
    }

    @Test
    void annotation_hints_should_accept_a_number_as_JsonNode_asBoolean_did() {
        ToolSpecification tool = firstTool(
                """
                [{"name":"t","description":"d","inputSchema":{"type":"object"},
                 "annotations":{"idempotentHint":1,"openWorldHint":0}}]""");

        assertThat(tool.metadata()).containsEntry("idempotentHint", true);
        assertThat(tool.metadata()).containsEntry("openWorldHint", false);
    }

    @Test
    void a_boolean_schema_should_degrade_to_an_empty_object_schema() {
        assertThatCode(() -> firstTool(
                        """
                        [{"name":"t","description":"d",
                         "inputSchema":{"type":"object","properties":{"anything":true}}}]"""))
                .doesNotThrowAnyException();
    }

    @Test
    void tuple_form_items_should_degrade_rather_than_fail() {
        assertThatCode(() -> firstTool(
                        """
                        [{"name":"t","description":"d","inputSchema":{"type":"object",
                         "properties":{"pair":{"type":"array","items":[{"type":"string"},{"type":"number"}]}}}}]"""))
                .doesNotThrowAnyException();
    }
}
