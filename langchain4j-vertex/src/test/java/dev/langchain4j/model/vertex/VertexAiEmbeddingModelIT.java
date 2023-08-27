package dev.langchain4j.model.vertex;

import dev.langchain4j.data.embedding.Embedding;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VertexAiEmbeddingModelIT {

    @Test
    @Disabled("To run this test, you must have provide your own endpoint, project and location")
    void testEmbeddingModel() {
        VertexAiEmbeddingModel vertexAiEmbeddingModel = new VertexAiEmbeddingModel(
                "us-central1-aiplatform.googleapis.com:443",
                "langchain4j",
                "us-central1",
                "google",
                "textembedding-gecko@001",
                3);

        Embedding embedding = vertexAiEmbeddingModel.embed("hello world");

        assertThat(embedding.vector().length).isEqualTo(768);
        System.out.println(embedding);
    }

}