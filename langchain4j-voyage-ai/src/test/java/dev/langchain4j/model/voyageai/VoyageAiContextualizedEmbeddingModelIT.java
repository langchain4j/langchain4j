package dev.langchain4j.model.voyageai;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.CosineSimilarity;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "VOYAGE_API_KEY", matches = ".+")
class VoyageAiContextualizedEmbeddingModelIT {

    @Test
    void should_embed_single_chunk() {

        // given
        EmbeddingModel model = VoyageAiContextualizedEmbeddingModel.builder()
                .apiKey(System.getenv("VOYAGE_API_KEY"))
                .modelName(VoyageAiEmbeddingModelName.VOYAGE_CONTEXT_4)
                .timeout(Duration.ofSeconds(60))
                .logRequests(true)
                .logResponses(false) // embeddings are huge in logs
                .build();

        // when
        Response<Embedding> response = model.embed("Hello World");

        // then
        assertThat(response.content().dimension()).isEqualTo(model.dimension());

        assertThat(response.tokenUsage().inputTokenCount()).isNotNegative();
        assertThat(response.tokenUsage().outputTokenCount()).isNull();
        assertThat(response.tokenUsage().totalTokenCount()).isNotNegative();

        assertThat(response.finishReason()).isNull();
    }

    @Test
    void should_embed_multiple_chunks_of_a_document() {

        // given
        EmbeddingModel model = VoyageAiContextualizedEmbeddingModel.builder()
                .apiKey(System.getenv("VOYAGE_API_KEY"))
                .modelName(VoyageAiEmbeddingModelName.VOYAGE_CONTEXT_4)
                .timeout(Duration.ofSeconds(60))
                .inputType("document")
                .logRequests(true)
                .logResponses(false) // embeddings are huge in logs
                .build();

        TextSegment chunk1 = TextSegment.from("The Eiffel Tower is located in Paris.");
        TextSegment chunk2 = TextSegment.from("It is one of the most visited monuments in the world.");

        // when
        Response<List<Embedding>> response = model.embedAll(asList(chunk1, chunk2));

        // then
        assertThat(response.content()).hasSize(2);

        Embedding embedding1 = response.content().get(0);
        assertThat(embedding1.dimension()).isEqualTo(model.dimension());

        Embedding embedding2 = response.content().get(1);
        assertThat(embedding2.dimension()).isEqualTo(model.dimension());

        assertThat(CosineSimilarity.between(embedding1, embedding2)).isGreaterThan(0.5);

        assertThat(response.tokenUsage().inputTokenCount()).isNotNegative();
        assertThat(response.tokenUsage().outputTokenCount()).isNull();
        assertThat(response.tokenUsage().totalTokenCount()).isNotNegative();

        assertThat(response.finishReason()).isNull();
    }

    @AfterEach
    void afterEach() throws InterruptedException {
        String ciDelaySeconds = System.getenv("CI_DELAY_SECONDS_VOYAGE_AI");
        if (ciDelaySeconds != null) {
            Thread.sleep(Integer.parseInt(ciDelaySeconds) * 1000L);
        }
    }
}
