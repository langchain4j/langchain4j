package dev.langchain4j.mcp.server;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class McpToolSchemaMapperTest {

    @Test
    void should_map_tool_specification_to_mcp_schema() {
        ToolSpecification toolSpecification = ToolSpecification.builder()
                .name("createPerson")
                .description("Creates a person record")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("name", "Person name")
                        .addIntegerProperty("age", "Person age")
                        .required("name", "age")
                        .build())
                .build();

        McpToolSchemaMapper mapper = new McpToolSchemaMapper();
        Map<String, Object> mapped = mapper.toMcpTool(toolSpecification);

        @SuppressWarnings("unchecked")
        Map<String, Object> inputSchema = (Map<String, Object>) mapped.get("inputSchema");
        assertThat(inputSchema).containsEntry("type", "object");

        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) inputSchema.get("properties");
        assertThat(properties).containsKeys("name", "age");

        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) inputSchema.get("required");
        assertThat(required).containsExactlyInAnyOrder("name", "age");
    }
}
