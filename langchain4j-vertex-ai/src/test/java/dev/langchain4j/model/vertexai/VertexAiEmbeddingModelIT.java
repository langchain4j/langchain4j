package dev.langchain4j.model.vertexai;

import dev.langchain4j.data.embedding.Embedding;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VertexAiEmbeddingModelIT {

    @Test
    @Disabled("To run this test, you must have provide your own endpoint, project and location")
    void testEmbeddingModel() {
        VertexAiEmbeddingModel vertexAiEmbeddingModel = VertexAiEmbeddingModel.builder()
                .endpoint("us-central1-aiplatform.googleapis.com:443")
                .project("langchain4j")
                .location("us-central1")
                .publisher("google")
                .modelName("textembedding-gecko@001")
                .maxRetries(3)
                .build();

        Embedding embedding = vertexAiEmbeddingModel.embed("hello world");

        assertThat(embedding.vector().length).isEqualTo(768);
        System.out.println(embedding);
    }

}