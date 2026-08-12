package dev.langchain4j.model.vertexai.anthropic.internal.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonReferenceSchema;
import dev.langchain4j.model.vertexai.anthropic.internal.api.AnthropicTool;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AnthropicRequestMapperTest {

    @Test
    void should_include_defs_when_parameters_have_definitions() {
        JsonObjectSchema address = JsonObjectSchema.builder()
                .addStringProperty("street")
                .addStringProperty("city")
                .build();

        JsonObjectSchema parameters = JsonObjectSchema.builder()
                .addProperty(
                        "address",
                        JsonReferenceSchema.builder().reference("Address").build())
                .definitions(Map.of("Address", address))
                .build();

        ToolSpecification toolSpecification = ToolSpecification.builder()
                .name("register_address")
                .description("Registers an address")
                .parameters(parameters)
                .build();

        AnthropicTool tool = AnthropicRequestMapper.toAnthropicTool(toolSpecification);

        assertThat(tool.inputSchema).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> inputSchema = (Map<String, Object>) tool.inputSchema;
        assertThat(inputSchema).containsKey("$defs");

        @SuppressWarnings("unchecked")
        Map<String, Object> defs = (Map<String, Object>) inputSchema.get("$defs");
        assertThat(defs).containsKey("Address");
    }

    @Test
    void should_not_include_defs_when_parameters_have_no_definitions() {
        JsonObjectSchema parameters =
                JsonObjectSchema.builder().addStringProperty("name").build();

        ToolSpecification toolSpecification = ToolSpecification.builder()
                .name("greet")
                .description("Greets a person")
                .parameters(parameters)
                .build();

        AnthropicTool tool = AnthropicRequestMapper.toAnthropicTool(toolSpecification);

        @SuppressWarnings("unchecked")
        Map<String, Object> inputSchema = (Map<String, Object>) tool.inputSchema;
        assertThat(inputSchema).doesNotContainKey("$defs");
    }
}
