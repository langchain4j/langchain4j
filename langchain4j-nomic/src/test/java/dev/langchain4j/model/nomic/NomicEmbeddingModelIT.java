package dev.langchain4j.model.nomic;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.CosineSimilarity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static java.time.Duration.ofSeconds;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

class NomicEmbeddingModelIT {

    @Test
    void should_embed_single_text() {

        // given
        EmbeddingModel model = NomicEmbeddingModel.builder()
                .apiKey(System.getenv("NOMIC_API_KEY"))
                .modelName("nomic-embed-text-v1")
                .build();

        String text = "hello";

        // when
        Response<Embedding> response = model.embed(text);

        // then
        assertThat(response.content().dimension()).isEqualTo(768);

        assertThat(response.tokenUsage().inputTokenCount()).isEqualTo(1);
        assertThat(response.tokenUsage().outputTokenCount()).isEqualTo(0);
        assertThat(response.tokenUsage().totalTokenCount()).isEqualTo(1);
    }

    @Test
    void should_embed_multiple_segments() {

        // given
        EmbeddingModel model = NomicEmbeddingModel.builder()
                .baseUrl("https://api-atlas.nomic.ai/v1/")
                .apiKey(System.getenv("NOMIC_API_KEY"))
                .modelName("nomic-embed-text-v1")
                .taskType("clustering")
                .maxSegmentsPerBatch(1)
                .timeout(ofSeconds(10))
                .logRequests(true)
                .logResponses(false) // embeddings are huge in logs
                .build();

        TextSegment segment1 = TextSegment.from("hello");
        TextSegment segment2 = TextSegment.from("hi");

        // when
        Response<List<Embedding>> response = model.embedAll(asList(segment1, segment2));

        // then
        assertThat(response.content()).hasSize(2);

        Embedding embedding1 = response.content().get(0);
        assertThat(embedding1.dimension()).isEqualTo(768);

        Embedding embedding2 = response.content().get(1);
        assertThat(embedding2.dimension()).isEqualTo(768);

        assertThat(CosineSimilarity.between(embedding1, embedding2)).isGreaterThan(0.9);

        assertThat(response.tokenUsage().inputTokenCount()).isEqualTo(2);
        assertThat(response.tokenUsage().outputTokenCount()).isEqualTo(0);
        assertThat(response.tokenUsage().totalTokenCount()).isEqualTo(2);
    }
}
