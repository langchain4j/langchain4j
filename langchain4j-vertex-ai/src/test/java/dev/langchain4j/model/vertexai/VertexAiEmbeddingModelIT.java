package dev.langchain4j.model.vertexai;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

class VertexAiEmbeddingModelIT {

    @Test
    void testEmbeddingModel() {

        EmbeddingModel embeddingModel = VertexAiEmbeddingModel.builder()
                .endpoint(System.getenv("GCP_VERTEXAI_ENDPOINT"))
                .project(System.getenv("GCP_PROJECT_ID"))
                .location(System.getenv("GCP_LOCATION"))
                .publisher("google")
                .modelName("textembedding-gecko@001")
                .maxRetries(3)
                .build();

        List<TextSegment> segments = asList(
                TextSegment.from("one"),
                TextSegment.from("two"),
                TextSegment.from("three"),
                TextSegment.from("four"),
                TextSegment.from("five"),
                TextSegment.from("six")
        );

        Response<List<Embedding>> response = embeddingModel.embedAll(segments);

        List<Embedding> embeddings = response.content();
        assertThat(embeddings).hasSize(6);

        Embedding embedding = embeddings.get(0);
        assertThat(embedding.vector()).hasSize(768);
        System.out.println(Arrays.toString(embedding.vector()));

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(6);
        assertThat(tokenUsage.outputTokenCount()).isNull();
        assertThat(tokenUsage.totalTokenCount()).isEqualTo(6);

        assertThat(response.finishReason()).isNull();
    }
}