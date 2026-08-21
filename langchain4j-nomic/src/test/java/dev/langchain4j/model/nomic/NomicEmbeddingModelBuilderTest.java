package dev.langchain4j.model.nomic;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NomicEmbeddingModelBuilderTest {

    @Test
    void toString_should_mask_api_key() {
        String toString = NomicEmbeddingModel.builder()
                .apiKey("secret-api-key")
                .modelName("nomic-embed-text-v1.5")
                .toString();

        assertThat(toString).doesNotContain("secret-api-key").contains("apiKey=********");
    }

    @Test
    void toString_should_render_null_api_key_as_null() {
        String toString = new NomicEmbeddingModel.NomicEmbeddingModelBuilder().toString();

        assertThat(toString).contains("apiKey=null");
    }
}
