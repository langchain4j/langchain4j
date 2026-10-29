package dev.langchain4j.jackson3;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.internal.Json;
import dev.langchain4j.internal.ProviderJson;
import dev.langchain4j.internal.ProviderJsonSpec;
import dev.langchain4j.model.mistralai.internal.api.MistralAiChatCompletionResponse;
import dev.langchain4j.model.mistralai.internal.api.MistralAiChatMessage;
import dev.langchain4j.model.mistralai.internal.api.MistralAiTextContent;
import dev.langchain4j.model.mistralai.internal.api.MistralAiToolType;
import org.junit.jupiter.api.Test;

/**
 * End-to-end check of the provider path: Mistral AI's wire DTOs, parsed by Jackson 3, with Jackson 2
 * absent from the classpath.
 */
class MistralAiWireFormatWithoutJackson2Test {

    private final Json.JsonCodec codec = ProviderJson.codec(ProviderJsonSpec.builder()
            .propertyNaming(ProviderJsonSpec.PropertyNaming.SNAKE_CASE)
            .build());

    @Test
    void deserializes_a_chat_response_with_text() {
        String json =
                """
                {"id":"cmpl-1","object":"chat.completion","created":1700000000,"model":"mistral-test",
                 "choices":[{"index":0,"message":{"role":"assistant","content":"hi"},"finish_reason":"stop"}],
                 "usage":{"prompt_tokens":10,"completion_tokens":5,"total_tokens":15}}
                """;

        MistralAiChatCompletionResponse response = codec.fromJson(json, MistralAiChatCompletionResponse.class);

        assertThat(response.getId()).isEqualTo("cmpl-1");
        assertThat(response.getChoices().get(0).getFinishReason()).isEqualTo("stop");
        MistralAiChatMessage message = response.getChoices().get(0).getMessage();
        assertThat(message.getContent()).hasSize(1);
        assertThat(((MistralAiTextContent) message.getContent().get(0)).getText()).isEqualTo("hi");
        assertThat(response.getUsage().getTotalTokens()).isEqualTo(15);
    }

    @Test
    void deserializes_a_chat_response_with_a_tool_call() {
        String json =
                """
                {"id":"cmpl-2","object":"chat.completion","created":1700000000,"model":"mistral-test",
                 "choices":[{"index":0,"message":{"role":"assistant","content":"",
                   "tool_calls":[{"id":"call_1","function":{"name":"get_weather","arguments":"{\\"city\\":\\"Paris\\"}"}}]},
                   "finish_reason":"tool_calls"}],
                 "usage":{"prompt_tokens":10,"completion_tokens":5,"total_tokens":15}}
                """;

        MistralAiChatCompletionResponse response = codec.fromJson(json, MistralAiChatCompletionResponse.class);

        MistralAiChatMessage message = response.getChoices().get(0).getMessage();
        assertThat(message.getToolCalls()).hasSize(1);
        assertThat(message.getToolCalls().get(0).getId()).isEqualTo("call_1");
        assertThat(message.getToolCalls().get(0).getType()).isEqualTo(MistralAiToolType.FUNCTION);
        assertThat(message.getToolCalls().get(0).getFunction().getName()).isEqualTo("get_weather");
        assertThat(message.getToolCalls().get(0).getFunction().getArguments()).isEqualTo("{\"city\":\"Paris\"}");
    }
}
