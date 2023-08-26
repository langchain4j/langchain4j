package dev.langchain4j.model.vertex;

import dev.langchain4j.data.embedding.Embedding;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VertexAiEmbeddingModelTest {

    @Test
    void testEmbeddingModel() {
        VertexAiEmbeddingModel vertexAiEmbeddingModel = new VertexAiEmbeddingModel(
                "textembedding-gecko@001",
                "langchain4j",
                "us-central1",
                "google",
                "us-central1-aiplatform.googleapis.com:443",
                3);

        Embedding embedding = vertexAiEmbeddingModel.embed("hello world");

        assertThat(embedding.vector().length).isEqualTo(768);
        System.out.println(embedding);
    }

}