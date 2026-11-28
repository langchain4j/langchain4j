package dev.langchain4j.model.vertexai.anthropic.internal.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonReferenceSchema;
import dev.langchain4j.model.vertexai.anthropic.internal.api.AnthropicOutputConfig;
import dev.langchain4j.model.vertexai.anthropic.internal.api.AnthropicRequest;
import dev.langchain4j.model.vertexai.anthropic.internal.api.AnthropicThinking;
import dev.langchain4j.model.vertexai.anthropic.internal.api.AnthropicTool;
import dev.langchain4j.model.vertexai.anthropic.internal.client.VertexAiAnthropicJsonUtils;
import java.util.List;
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
    void should_pass_tool_description_through_unchanged() {
        ToolSpecification toolSpecification = ToolSpecification.builder()
                .name("time")
                .description("Returns the number of seconds the build has been running")
                .build();

        AnthropicTool tool = AnthropicRequestMapper.toAnthropicTool(toolSpecification);

        assertThat(tool.description).isEqualTo("Returns the number of seconds the build has been running");
    }

    @Test
    void should_not_invent_a_description_when_tool_has_none() {
        ToolSpecification toolSpecification =
                ToolSpecification.builder().name("time").build();

        AnthropicTool tool = AnthropicRequestMapper.toAnthropicTool(toolSpecification);

        assertThat(tool.description).isNull();
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

    @Test
    void should_send_neither_thinking_nor_output_config_by_default() {
        AnthropicRequest request = AnthropicRequestMapper.toRequest(
                "claude-opus-5-5", List.of(UserMessage.from("hi")), null, null, null, null, null, null, null, false);

        String json = VertexAiAnthropicJsonUtils.toJson(request);

        assertThat(json).doesNotContain("thinking").doesNotContain("output_config");
    }

    @Test
    void should_send_adaptive_thinking_with_display_and_effort() {
        AnthropicRequest request = toRequest(
                AnthropicRequestMapper.toThinking("adaptive", null, "summarized"),
                AnthropicRequestMapper.toOutputConfig("high"));

        String json = VertexAiAnthropicJsonUtils.toJson(request);

        assertThat(json)
                .contains("\"thinking\":{\"type\":\"adaptive\",\"display\":\"summarized\"}")
                .contains("\"output_config\":{\"effort\":\"high\"}")
                .doesNotContain("budget_tokens")
                .doesNotContain("outputConfig");
    }

    @Test
    void should_send_enabled_thinking_with_budget_tokens() {
        AnthropicRequest request = toRequest(AnthropicRequestMapper.toThinking("enabled", 2048, null), null);

        String json = VertexAiAnthropicJsonUtils.toJson(request);

        assertThat(json)
                .contains("\"thinking\":{\"type\":\"enabled\",\"budget_tokens\":2048}")
                .doesNotContain("display")
                .doesNotContain("output_config");
    }

    @Test
    void should_send_disabled_thinking_and_effort_without_thinking_unchanged() {
        assertThat(VertexAiAnthropicJsonUtils.toJson(
                        toRequest(AnthropicRequestMapper.toThinking("disabled", null, null), null)))
                .contains("\"thinking\":{\"type\":\"disabled\"}");

        assertThat(VertexAiAnthropicJsonUtils.toJson(toRequest(null, AnthropicRequestMapper.toOutputConfig("xhigh"))))
                .contains("\"output_config\":{\"effort\":\"xhigh\"}")
                .doesNotContain("thinking");
    }

    @Test
    void should_not_send_thinking_when_only_display_is_set() {
        assertThat(AnthropicRequestMapper.toThinking(null, null, "summarized")).isNull();
        assertThat(AnthropicRequestMapper.toThinking(null, null, null)).isNull();
        assertThat(AnthropicRequestMapper.toOutputConfig(null)).isNull();
    }

    @Test
    void should_not_set_sampling_parameters_when_thinking_is_set() {
        AnthropicRequest request = toRequest(
                AnthropicRequestMapper.toThinking("adaptive", null, null),
                AnthropicRequestMapper.toOutputConfig("low"));

        assertThat(request.temperature).isNull();
        assertThat(request.topP).isNull();
        assertThat(request.topK).isNull();
        assertThat(VertexAiAnthropicJsonUtils.toJson(request))
                .doesNotContain("temperature")
                .doesNotContain("top_p")
                .doesNotContain("top_k");
    }

    private static AnthropicRequest toRequest(AnthropicThinking thinking, AnthropicOutputConfig outputConfig) {
        return AnthropicRequestMapper.toRequest(
                "claude-opus-5-5",
                List.of(UserMessage.from("hi")),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                thinking,
                outputConfig);
    }
}
