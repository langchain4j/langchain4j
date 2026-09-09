package dev.langchain4j.model.voyageai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;
import org.junit.jupiter.api.Test;

/**
 * Voyage returns an embedding either as an array of numbers or, when {@code encoding_format} is
 * {@code base64}, as little-endian float bytes in Base64.
 */
class EmbeddingResponseTest {

    private static String base64(float... floats) {
        ByteBuffer buffer = ByteBuffer.allocate(floats.length * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (float value : floats) {
            buffer.putFloat(value);
        }
        return Base64.getEncoder().encodeToString(buffer.array());
    }

    private static EmbeddingResponse read(String json) {
        return VoyageAiJsonUtils.fromJson(json, EmbeddingResponse.class);
    }

    @Test
    void should_read_embeddings_sent_as_arrays_of_numbers() {
        EmbeddingResponse response = read(
                """
                {"object":"list","model":"voyage-3","data":[
                  {"object":"embedding","embedding":[1.1,2.2,3.3],"index":0},
                  {"object":"embedding","embedding":[4.4,5.5,6.6],"index":1}]}""");

        assertThat(response.getModel()).isEqualTo("voyage-3");
        assertThat(response.getData()).hasSize(2);
        assertThat(response.getData().get(0).getEmbedding()).containsExactly(1.1f, 2.2f, 3.3f);
        assertThat(response.getData().get(0).getObject()).isEqualTo("embedding");
        assertThat(response.getData().get(0).getIndex()).isZero();
        assertThat(response.getData().get(1).getEmbedding()).containsExactly(4.4f, 5.5f, 6.6f);
        assertThat(response.getData().get(1).getIndex()).isEqualTo(1);
    }

    @Test
    void should_read_embeddings_sent_as_base64() {
        EmbeddingResponse response = read(
                """
                {"data":[
                  {"object":"embedding","embedding":"%s","index":0},
                  {"object":"embedding","embedding":"%s","index":1}]}"""
                        .formatted(base64(1.1f, 2.2f, 3.3f), base64(4.4f, 5.5f, 6.6f)));

        assertThat(response.getData()).hasSize(2);
        assertThat(response.getData().get(0).getEmbedding()).containsExactly(1.1f, 2.2f, 3.3f);
        assertThat(response.getData().get(1).getEmbedding()).containsExactly(4.4f, 5.5f, 6.6f);
    }

    @Test
    void should_read_an_embedding_without_an_embedding_field() {
        EmbeddingResponse response = read("{\"data\":[{\"object\":\"embedding\",\"index\":0}]}");

        assertThat(response.getData().get(0).getEmbedding()).isNull();
    }

    @Test
    void should_reject_an_embedding_that_is_neither_an_array_nor_a_string() {
        assertThatThrownBy(() -> read("{\"data\":[{\"object\":\"embedding\",\"embedding\":123,\"index\":0}]}"))
                .hasMessageContaining("Unexpected embedding");
    }

    @Test
    void should_reject_an_array_that_does_not_hold_numbers() {
        assertThatThrownBy(() -> read("{\"data\":[{\"object\":\"embedding\",\"embedding\":[\"a\"],\"index\":0}]}"))
                .hasMessageContaining("Unexpected embedding");
    }
}
