package dev.langchain4j.model.vertexai;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static java.util.Arrays.asList;
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

        List<TextSegment> segments = asList(
                TextSegment.from("hello world"),
                TextSegment.from("how are you?")
        );

        Response<List<Embedding>> response = vertexAiEmbeddingModel.embedAll(segments);

        List<Embedding> embeddings = response.content();
        assertThat(embeddings).hasSize(2);

        Embedding embedding1 = embeddings.get(0);
        assertThat(embedding1.vector()).hasSize(768);
        System.out.println(Arrays.toString(embedding1.vector()));

        Embedding embedding2 = embeddings.get(1);
        assertThat(embedding2.vector()).hasSize(768);
        System.out.println(Arrays.toString(embedding2.vector()));

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(6);
        assertThat(tokenUsage.outputTokenCount()).isNull();
        assertThat(tokenUsage.totalTokenCount()).isEqualTo(6);

        assertThat(response.finishReason()).isNull();
    }
}