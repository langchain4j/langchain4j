package dev.langchain4j.model.ovhai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OvhAiEmbeddingModelTest {

    @Test
    void toString_should_mask_api_key() {
        String toString = OvhAiEmbeddingModel.builder().apiKey("secret-api-key").toString();

        assertThat(toString).doesNotContain("secret-api-key").contains("apiKey=********");
    }

    @Test
    void toString_should_render_null_api_key_as_null() {
        String toString = new OvhAiEmbeddingModel.OvhAiEmbeddingModelBuilder().toString();

        assertThat(toString).contains("apiKey=null");
    }
}
