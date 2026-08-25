package dev.langchain4j.model.openai.internal.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.internal.Json;
import dev.langchain4j.internal.WireJson;
import dev.langchain4j.internal.WireJsonSpec;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * OpenAI returns an embedding either as an array of numbers or, when {@code encoding_format} is
 * {@code base64}, as little-endian float bytes in Base64.
 */
class EmbeddingDeserializationTest {

    private final Json.JsonCodec codec = WireJson.codec(WireJsonSpec.builder()
            .propertyNaming(WireJsonSpec.PropertyNaming.SNAKE_CASE)
            .build());

    private static String base64(List<Float> floats) {
        ByteBuffer buffer = ByteBuffer.allocate(floats.size() * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        floats.forEach(buffer::putFloat);
        return Base64.getEncoder().encodeToString(buffer.array());
    }

    @Test
    void should_read_an_embedding_sent_as_an_array_of_numbers() {
        Embedding embedding = codec.fromJson("{\"index\":1,\"embedding\":[1.0,2.0,3.0]}", Embedding.class);

        assertThat(embedding.index()).isEqualTo(1);
        assertThat(embedding.embedding()).containsExactly(1.0f, 2.0f, 3.0f);
    }

    @Test
    void should_read_an_embedding_sent_as_base64() {
        List<Float> original = List.of(4.2f, -1.5f, 0.0f);

        Embedding embedding =
                codec.fromJson("{\"index\":2,\"embedding\":\"%s\"}".formatted(base64(original)), Embedding.class);

        assertThat(embedding.index()).isEqualTo(2);
        assertThat(embedding.embedding()).containsExactlyElementsOf(original);
    }

    @Test
    void should_read_an_empty_embedding() {
        assertThat(codec.fromJson("{\"index\":0,\"embedding\":[]}", Embedding.class).embedding())
                .isEmpty();
        assertThat(codec.fromJson("{\"index\":0,\"embedding\":\"\"}", Embedding.class).embedding())
                .isEmpty();
    }

    @Test
    void should_read_an_embedding_without_the_embedding_field() {
        Embedding embedding = codec.fromJson("{\"index\":0}", Embedding.class);

        assertThat(embedding.embedding()).isNull();
    }

    @Test
    void should_reject_an_embedding_that_is_neither_an_array_nor_a_string() {
        assertThatThrownBy(() -> codec.fromJson("{\"index\":3,\"embedding\":123}", Embedding.class))
                .hasMessageContaining("Illegal embedding");
    }

    @Test
    void should_reject_an_array_that_does_not_hold_numbers() {
        assertThatThrownBy(() -> codec.fromJson("{\"index\":3,\"embedding\":[\"a\"]}", Embedding.class))
                .hasMessageContaining("Illegal embedding");
    }

    @Test
    void should_read_an_embedding_response() {
        EmbeddingResponse response = codec.fromJson(
                "{\"model\":\"text-embedding-3-small\",\"data\":[{\"index\":0,\"embedding\":[0.1,0.2]}],"
                        + "\"usage\":{\"prompt_tokens\":1,\"total_tokens\":1}}",
                EmbeddingResponse.class);

        assertThat(response.model()).isEqualTo("text-embedding-3-small");
        assertThat(response.embedding()).containsExactly(0.1f, 0.2f);
    }
}
