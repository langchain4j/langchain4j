package dev.langchain4j.model.jina.internal.client;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.jina.internal.api.JinaEmbeddingResponse;
import org.junit.jupiter.api.Test;

/**
 * Jina sends its fields in snake_case. The naming lives on the codec rather than on the response
 * types, so it is worth pinning here.
 */
class JinaEmbeddingResponseTest {

    @Test
    void should_read_a_response_whose_fields_are_snake_case() {
        JinaEmbeddingResponse response = JinaJsonUtils.fromJson(
                """
                {"model":"jina-embeddings-v3","object":"list",
                 "usage":{"total_tokens":4,"prompt_tokens":4},
                 "data":[{"object":"embedding","index":0,"embedding":[0.1,0.2]}]}""",
                JinaEmbeddingResponse.class);

        assertThat(response.model).isEqualTo("jina-embeddings-v3");
        assertThat(response.data).hasSize(1);
        assertThat(response.data.get(0).embedding).containsExactly(0.1f, 0.2f);
        assertThat(response.usage).isNotNull();
        assertThat(response.usage.totalTokens).isEqualTo(4);
        assertThat(response.usage.promptTokens).isEqualTo(4);
    }

    @Test
    void should_read_a_response_that_carries_no_usage() {
        JinaEmbeddingResponse response = JinaJsonUtils.fromJson(
                "{\"model\":\"jina-embeddings-v3\",\"data\":[{\"index\":0,\"embedding\":[0.1]}]}",
                JinaEmbeddingResponse.class);

        assertThat(response.data).hasSize(1);
        assertThat(response.usage).isNull();
    }

    @Test
    void should_ignore_fields_it_does_not_know() {
        JinaEmbeddingResponse response = JinaJsonUtils.fromJson(
                "{\"model\":\"jina-embeddings-v3\",\"data\":[],\"a_brand_new_field\":\"whatever\"}",
                JinaEmbeddingResponse.class);

        assertThat(response.model).isEqualTo("jina-embeddings-v3");
    }
}
