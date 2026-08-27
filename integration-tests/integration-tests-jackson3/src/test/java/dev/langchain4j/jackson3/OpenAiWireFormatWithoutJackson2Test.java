package dev.langchain4j.jackson3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.internal.Json;
import dev.langchain4j.internal.WireJson;
import dev.langchain4j.internal.WireJsonSpec;
import dev.langchain4j.model.openai.internal.chat.ChatCompletionRequest;
import dev.langchain4j.model.openai.internal.chat.Content;
import dev.langchain4j.model.openai.internal.chat.ContentType;
import dev.langchain4j.model.openai.internal.chat.ImageDetail;
import dev.langchain4j.model.openai.internal.chat.ChatCompletionResponse;
import dev.langchain4j.model.openai.internal.embedding.EmbeddingResponse;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * End-to-end check of the provider path: OpenAI's wire DTOs, serialized and parsed by Jackson 3,
 * with Jackson 2 absent from the classpath.
 */
class OpenAiWireFormatWithoutJackson2Test {

    private final Json.JsonCodec codec = WireJson.codec(WireJsonSpec.builder()
            .propertyNaming(WireJsonSpec.PropertyNaming.SNAKE_CASE)
            .build());

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
    void deserializes_an_image_content_sent_as_an_object() {
        Content content = codec.fromJson(
                "{\"type\":\"image_url\",\"image_url\":{\"url\":\"https://example.com/cat.png\",\"detail\":\"high\"}}",
                Content.class);

        assertThat(content.type()).isEqualTo(ContentType.IMAGE_URL);
        assertThat(content.imageUrl().getUrl()).isEqualTo("https://example.com/cat.png");
        assertThat(content.imageUrl().getDetail()).isEqualTo(ImageDetail.HIGH);
    }

    @Test
    void deserializes_an_image_content_sent_as_a_bare_string() {
        Content content =
                codec.fromJson("{\"type\":\"input_image\",\"image_url\":\"https://example.com/cat.png\"}", Content.class);

        assertThat(content.inputImageUrl()).isEqualTo("https://example.com/cat.png");
        assertThat(content.imageUrl()).isNull();
    }

    @Test
    void deserializes_a_base64_encoded_embedding() {
        List<Float> original = List.of(4.2f, -1.5f, 0.0f);
        ByteBuffer buffer = ByteBuffer.allocate(original.size() * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        original.forEach(buffer::putFloat);
        String base64 = Base64.getEncoder().encodeToString(buffer.array());

        EmbeddingResponse response = codec.fromJson(
                "{\"model\":\"text-embedding-3-small\",\"data\":[{\"index\":0,\"embedding\":\"%s\"}]}"
                        .formatted(base64),
                EmbeddingResponse.class);

        assertThat(response.embedding()).containsExactlyElementsOf(original);
    }

    @Test
    void snake_case_can_be_turned_off_via_the_spec() {
        Json.JsonCodec identity = WireJson.codec(WireJsonSpec.builder().build());
        assertThat(identity.toJson(List.of())).isEqualTo("[]");
    }
}
