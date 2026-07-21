package dev.langchain4j.model.jina;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JinaScoringModelBuilderTest {

    @Test
    void toString_should_mask_api_key() {
        String toString = JinaScoringModel.builder()
                .apiKey("secret-api-key")
                .modelName("jina-reranker-v2-base-multilingual")
                .toString();

        assertThat(toString).doesNotContain("secret-api-key").contains("apiKey=********");
    }

    @Test
    void toString_should_render_null_api_key_as_null() {
        String toString = new JinaScoringModel.JinaScoringModelBuilder().toString();

        assertThat(toString).contains("apiKey=null");
    }
}
