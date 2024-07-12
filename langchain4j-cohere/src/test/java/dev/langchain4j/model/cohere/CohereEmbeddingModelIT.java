package dev.langchain4j.model.cohere;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.CosineSimilarity;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

class CohereEmbeddingModelIT {

    @Test
    void should_embed_single_text() {

        // given
        EmbeddingModel model = CohereEmbeddingModel.withApiKey(System.getenv("COHERE_API_KEY"));

        // when
        Response<Embedding> response = model.embed("Hello World");

        // then
        assertThat(response.content().dimension()).isEqualTo(4096);

        assertThat(response.tokenUsage().inputTokenCount()).isEqualTo(2);
        assertThat(response.tokenUsage().outputTokenCount()).isEqualTo(0);
        assertThat(response.tokenUsage().totalTokenCount()).isEqualTo(2);

        assertThat(response.finishReason()).isNull();
    }

    @Test
    public void should_embed_multiple_segments() {

        // given
        EmbeddingModel model = CohereEmbeddingModel.builder()
                .baseUrl("https://api.cohere.ai/v1/")
                .apiKey(System.getenv("COHERE_API_KEY"))
                .modelName("embed-english-light-v3.0")
                .inputType("search_document")
                .timeout(Duration.ofSeconds(60))
                .logRequests(true)
                .logResponses(true)
                .build();

        TextSegment segment1 = TextSegment.from("hello");
        TextSegment segment2 = TextSegment.from("hi");

        // when
        Response<List<Embedding>> response = model.embedAll(asList(segment1, segment2));

        // then
        assertThat(response.content()).hasSize(2);

        Embedding embedding1 = response.content().get(0);
        assertThat(embedding1.dimension()).isEqualTo(384);

        Embedding embedding2 = response.content().get(1);
        assertThat(embedding2.dimension()).isEqualTo(384);

        assertThat(CosineSimilarity.between(embedding1, embedding2)).isGreaterThan(0.9);

        assertThat(response.tokenUsage().inputTokenCount()).isEqualTo(2);
        assertThat(response.tokenUsage().outputTokenCount()).isEqualTo(0);
        assertThat(response.tokenUsage().totalTokenCount()).isEqualTo(2);

        assertThat(response.finishReason()).isNull();
    }

    @Test
    public void should_embed_any_number_of_segments()
    {
        EmbeddingModel model = CohereEmbeddingModel.builder()
                .baseUrl("https://api.cohere.ai/v1/")
                .apiKey(System.getenv("COHERE_API_KEY"))
                .modelName("embed-english-light-v3.0")
                .inputType("search_document")
                .timeout(Duration.ofSeconds(60))
                .logRequests(true)
                .logResponses(true)
                .build();

        List<TextSegment> segments = new ArrayList<>();
        for (int i = 0; i < 97; i++) {
            segments.add(TextSegment.from("text"));
        }

        // when
        Response<List<Embedding>> response = model.embedAll(segments);

        // then
        assertThat(response.content()).hasSize(97);

        assertThat(response.tokenUsage().inputTokenCount()).isEqualTo(97);
        assertThat(response.tokenUsage().outputTokenCount()).isEqualTo(0);
        assertThat(response.tokenUsage().totalTokenCount()).isEqualTo(97);

        assertThat(response.finishReason()).isNull();
    }
}