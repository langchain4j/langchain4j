package dev.langchain4j.model.vertexai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class VertexAiEmbeddingModelTest {

    @Test
    void should_return_output_dimensionality_as_dimension_when_set() {
        VertexAiEmbeddingModel model = VertexAiEmbeddingModel.builder()
                .project("test-project")
                .location("us-central1")
                .publisher("google")
                .modelName("text-embedding-004")
                .outputDimensionality(256)
                .build();

        assertThat(model.dimension()).isEqualTo(256);
    }

    @Test
    void should_return_output_dimensionality_as_dimension_for_unknown_model_name() {
        VertexAiEmbeddingModel model = VertexAiEmbeddingModel.builder()
                .project("test-project")
                .location("us-central1")
                .publisher("google")
                .modelName("some-future-embedding-model")
                .outputDimensionality(512)
                .build();

        assertThat(model.dimension()).isEqualTo(512);
    }

    @Test
    void should_return_known_model_dimension_when_output_dimensionality_is_not_set() {
        VertexAiEmbeddingModel model = VertexAiEmbeddingModel.builder()
                .project("test-project")
                .location("us-central1")
                .publisher("google")
                .modelName("text-embedding-004")
                .build();

        assertThat(model.dimension()).isEqualTo(768);
    }
}
