package dev.langchain4j.model.cohere;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CohereModelBuilderTest {

    @Test
    void embeddingModelBuilder_toString_should_mask_api_key() {
        String toString = CohereEmbeddingModel.builder()
                .apiKey("secret-api-key")
                .modelName("embed-english-v3.0")
                .toString();

        assertThat(toString).doesNotContain("secret-api-key").contains("apiKey=********");
    }

    @Test
    void embeddingModelBuilder_toString_should_render_null_api_key_as_null() {
        String toString = new CohereEmbeddingModel.CohereEmbeddingModelBuilder().toString();

        assertThat(toString).contains("apiKey=null");
    }

    @Test
    void scoringModelBuilder_toString_should_mask_api_key() {
        String toString = CohereScoringModel.builder()
                .apiKey("secret-api-key")
                .modelName("rerank-english-v3.0")
                .toString();

        assertThat(toString).doesNotContain("secret-api-key").contains("apiKey=********");
    }

    @Test
    void scoringModelBuilder_toString_should_render_null_api_key_as_null() {
        String toString = new CohereScoringModel.CohereScoringModelBuilder().toString();

        assertThat(toString).contains("apiKey=null");
    }
}
