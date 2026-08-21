package dev.langchain4j.json.jackson3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.internal.Json;
import dev.langchain4j.internal.WireJson;
import dev.langchain4j.internal.WireJsonSpec;
import dev.langchain4j.model.openai.internal.chat.ChatCompletionRequest;
import dev.langchain4j.model.openai.internal.chat.ChatCompletionResponse;
import dev.langchain4j.model.openai.internal.embedding.EmbeddingResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * End-to-end check of the provider path: OpenAI's wire DTOs, serialized and parsed by Jackson 3,
 * with Jackson 2 absent from the classpath.
 */
class OpenAiProviderOnJackson3Test {

    private final Json.JsonCodec codec = WireJson.codec(WireJsonSpec.builder()
            .propertyNaming(WireJsonSpec.PropertyNaming.SNAKE_CASE)
            .build());

    @Test
    void the_spi_resolves_to_the_jackson3_wire_codec() {
        assertThat(codec).isInstanceOf(Jackson3WireJsonCodec.class);
    }

    @Test
    void jackson2_is_absent() {
        assertThatThrownBy(() -> Class.forName("com.fasterxml.jackson.databind.ObjectMapper"))
                .isInstanceOf(ClassNotFoundException.class);
    }

    @Test
    void serializes_a_request_in_snake_case_without_JsonNaming_annotations() {
        ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model("gpt-4o-mini")
                .addUserMessage("hello")
                .maxCompletionTokens(200)
                .topP(0.9)
                .frequencyPenalty(0.2)
                .parallelToolCalls(true)
                .build();

        String json = codec.toJson(request);

        assertThat(json)
                .contains("\"max_completion_tokens\"")
                .contains("\"top_p\"")
                .contains("\"frequency_penalty\"")
                .contains("\"parallel_tool_calls\"")
                .doesNotContain("maxCompletionTokens")
                .doesNotContain("topP");
    }

    @Test
    void deserializes_a_chat_response() {
        String json =
                """
                {"id":"chatcmpl-1","created":1700000000,"model":"gpt-4o-mini",
                 "system_fingerprint":"fp_abc",
                 "choices":[{"index":0,"message":{"role":"assistant","content":"hi"},"finish_reason":"stop"}],
                 "usage":{"prompt_tokens":10,"completion_tokens":5,"total_tokens":15}}
                """;

        ChatCompletionResponse response = codec.fromJson(json, ChatCompletionResponse.class);

        assertThat(response.id()).isEqualTo("chatcmpl-1");
        assertThat(response.content()).isEqualTo("hi");
        assertThat(response.systemFingerprint()).isEqualTo("fp_abc");
        assertThat(response.usage().totalTokens()).isEqualTo(15);
    }

    @Test
    void deserializes_an_embedding_response() {
        String json =
                """
                {"model":"text-embedding-3-small",
                 "data":[{"index":0,"embedding":[0.1,0.2]}],
                 "usage":{"prompt_tokens":1,"total_tokens":1}}
                """;

        EmbeddingResponse response = codec.fromJson(json, EmbeddingResponse.class);

        assertThat(response.model()).isEqualTo("text-embedding-3-small");
        assertThat(response.embedding()).containsExactly(0.1f, 0.2f);
    }

    @Test
    void snake_case_can_be_turned_off_via_the_spec() {
        Json.JsonCodec identity = WireJson.codec(WireJsonSpec.builder().build());
        assertThat(identity.toJson(List.of())).isEqualTo("[]");
    }
}
