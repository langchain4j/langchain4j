package dev.langchain4j.model.vertexai.anthropic.internal.api;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.vertexai.anthropic.internal.client.VertexAiAnthropicJsonUtils;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Anthropic on Vertex sends and receives snake_case. The naming lives on the codec rather than on
 * the DTOs, so it is worth pinning here.
 */
class AnthropicWireFormatTest {

    @Test
    void should_write_multi_word_fields_as_snake_case() {
        AnthropicMessage message = new AnthropicMessage("user", List.of(new AnthropicContent("text", "hi")));
        message.cacheControl = AnthropicCacheControl.ephemeral();

        AnthropicContent toolUse = new AnthropicContent("tool_use", null);
        toolUse.toolUseId = "toolu_1";
        toolUse.source = AnthropicSource.base64("image/png", "AAAA");

        String json = VertexAiAnthropicJsonUtils.toJson(List.of(message, toolUse));

        assertThat(json)
                .contains("\"cache_control\":{\"type\":\"ephemeral\"}")
                .contains("\"tool_use_id\":\"toolu_1\"")
                .contains("\"media_type\":\"image/png\"");
        assertThat(json).doesNotContain("cacheControl").doesNotContain("toolUseId").doesNotContain("mediaType");
    }

    @Test
    void should_write_a_tool_with_a_snake_case_input_schema() {
        String json = VertexAiAnthropicJsonUtils.toJson(new AnthropicTool("getWeather", "gets weather", java.util.Map.of("type", "object")));

        assertThat(json).contains("\"input_schema\":{\"type\":\"object\"}").doesNotContain("inputSchema");
    }

    @Test
    void should_read_a_response_whose_fields_are_snake_case() {
        AnthropicResponse response = VertexAiAnthropicJsonUtils.fromJson(
                """
                {"id":"msg_1","type":"message","role":"assistant","model":"claude-sonnet-4-5",
                 "content":[{"type":"text","text":"hi"}],
                 "stop_reason":"end_turn","stop_sequence":null,
                 "usage":{"input_tokens":10,"output_tokens":5,
                          "cache_creation_input_tokens":2,"cache_read_input_tokens":3}}""",
                AnthropicResponse.class);

        assertThat(response.id).isEqualTo("msg_1");
        assertThat(response.stopReason).isEqualTo("end_turn");
        assertThat(response.content).hasSize(1);
        assertThat(response.usage.inputTokens).isEqualTo(10);
        assertThat(response.usage.outputTokens).isEqualTo(5);
        assertThat(response.usage.cacheCreationInputTokens).isEqualTo(2);
        assertThat(response.usage.cacheReadInputTokens).isEqualTo(3);
    }

    @Test
    void should_ignore_fields_it_does_not_know() {
        AnthropicResponse response =
                VertexAiAnthropicJsonUtils.fromJson("{\"id\":\"msg_1\",\"a_brand_new_field\":\"whatever\"}", AnthropicResponse.class);

        assertThat(response.id).isEqualTo("msg_1");
    }
}
