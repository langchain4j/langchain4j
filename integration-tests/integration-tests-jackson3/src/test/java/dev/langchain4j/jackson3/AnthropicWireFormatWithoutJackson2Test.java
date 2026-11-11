package dev.langchain4j.jackson3;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.internal.Json;
import dev.langchain4j.internal.ProviderJson;
import dev.langchain4j.internal.ProviderJsonSpec;
import dev.langchain4j.model.anthropic.internal.api.AnthropicCreateMessageResponse;
import org.junit.jupiter.api.Test;

/**
 * End-to-end check of the provider path: Anthropic's wire DTOs, parsed by Jackson 3, with Jackson 2
 * absent from the classpath.
 */
class AnthropicWireFormatWithoutJackson2Test {

    private final Json.JsonCodec codec = ProviderJson.codec(ProviderJsonSpec.builder()
            .propertyNaming(ProviderJsonSpec.PropertyNaming.SNAKE_CASE)
            .build());

    @Test
    void deserializes_a_message_response_with_text_and_tool_use() {
        String json =
                """
                {"id":"msg_1","type":"message","role":"assistant","model":"claude-test",
                 "content":[
                   {"type":"text","text":"Let me check."},
                   {"type":"tool_use","id":"toolu_1","name":"get_weather","input":{"city":"Paris"}}
                 ],
                 "stop_reason":"tool_use",
                 "usage":{"input_tokens":12,"output_tokens":7,"cache_read_input_tokens":3}}
                """;

        AnthropicCreateMessageResponse response = codec.fromJson(json, AnthropicCreateMessageResponse.class);

        assertThat(response.id).isEqualTo("msg_1");
        assertThat(response.stopReason).isEqualTo("tool_use");
        assertThat(response.content).hasSize(2);
        assertThat(response.content.get(0).text).isEqualTo("Let me check.");
        assertThat(response.content.get(1).id).isEqualTo("toolu_1");
        assertThat(response.content.get(1).name).isEqualTo("get_weather");
        assertThat(response.content.get(1).input).containsEntry("city", "Paris");
        assertThat(response.usage.inputTokens).isEqualTo(12);
        assertThat(response.usage.outputTokens).isEqualTo(7);
        assertThat(response.usage.cacheReadInputTokens).isEqualTo(3);
    }
}
