package dev.langchain4j.model.jina;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.CosineSimilarity;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "JINA_API_KEY", matches = ".+")
class JinaEmbeddingModelIT {

    @Test
    void should_embed_single_text() {

        // given
        EmbeddingModel model = JinaEmbeddingModel.builder()
                .apiKey(System.getenv("JINA_API_KEY"))
                .modelName("jina-embeddings-v3")
                .logRequests(true)
                .logResponses(false) // embeddings are huge in logs
                .build();

        String text = "hello";

        // when
        Response<Embedding> response = model.embed(text);

        // then
        assertThat(response.content().dimension()).isEqualTo(1024);

        assertThat(response.tokenUsage().inputTokenCount()).isEqualTo(4);
        assertThat(response.tokenUsage().outputTokenCount()).isZero();
        assertThat(response.tokenUsage().totalTokenCount()).isEqualTo(4);

        assertThat(response.finishReason()).isNull();
    }

    @Test
    void should_embed_multiple_segments() {

        // given
        EmbeddingModel model = JinaEmbeddingModel.builder()
                .apiKey(System.getenv("JINA_API_KEY"))
                .modelName("jina-embeddings-v3")
                .logRequests(true)
                .logResponses(false) // embeddings are huge in logs
                .build();

        TextSegment segment1 = TextSegment.from("hello");
        TextSegment segment2 = TextSegment.from("hi");
        TextSegment segment3 = TextSegment.from("there");

        // when
        Response<List<Embedding>> response = model.embedAll(asList(segment1, segment2, segment3));

        // then
        assertThat(response.content()).hasSize(3);

        Embedding embedding1 = response.content().get(0);
        assertThat(embedding1.dimension()).isEqualTo(1024);

        Embedding embedding2 = response.content().get(1);
        assertThat(embedding2.dimension()).isEqualTo(1024);

        Embedding embedding3 = response.content().get(2);
        assertThat(embedding3.dimension()).isEqualTo(1024);

        assertThat(CosineSimilarity.between(embedding1, embedding2)).isGreaterThan(0.85);

        assertThat(response.tokenUsage().inputTokenCount()).isEqualTo(10);
        assertThat(response.tokenUsage().outputTokenCount()).isZero();
        assertThat(response.tokenUsage().totalTokenCount()).isEqualTo(10);

        assertThat(response.finishReason()).isNull();
    }

    @Test
    void should_embed_multiple_segments_with_late_chunking() {

        // given
        boolean lateChunking = true;

        EmbeddingModel model = JinaEmbeddingModel.builder()
                .apiKey(System.getenv("JINA_API_KEY"))
                .modelName("jina-embeddings-v3")
                .lateChunking(lateChunking)
                .logRequests(true)
                .logResponses(false) // embeddings are huge in logs
                .build();

        TextSegment segment1 = TextSegment.from("hello");
        TextSegment segment2 = TextSegment.from("hi");
        TextSegment segment3 = TextSegment.from("there");

        // when
        Response<List<Embedding>> response = model.embedAll(asList(segment1, segment2, segment3));

        // then
        assertThat(response.content()).hasSize(3);

        Embedding embedding1 = response.content().get(0);
        assertThat(embedding1.dimension()).isEqualTo(1024);

        Embedding embedding2 = response.content().get(1);
        assertThat(embedding2.dimension()).isEqualTo(1024);

        Embedding embedding3 = response.content().get(2);
        assertThat(embedding3.dimension()).isEqualTo(1024);

        assertThat(CosineSimilarity.between(embedding1, embedding2)).isGreaterThan(0.9);
        assertThat(CosineSimilarity.between(embedding1, embedding3)).isGreaterThan(0.9);

        assertThat(response.tokenUsage().inputTokenCount()).isEqualTo(6);
        assertThat(response.tokenUsage().outputTokenCount()).isZero();
        assertThat(response.tokenUsage().totalTokenCount()).isEqualTo(6);

        assertThat(response.finishReason()).isNull();
    }

    @Test
    void should_embed_multiple_segments_with_late_chunking_compare() {

        // given
        EmbeddingModel model = JinaEmbeddingModel.builder()
                .apiKey(System.getenv("JINA_API_KEY"))
                .modelName("jina-embeddings-v3")
                .lateChunking(false)
                .logRequests(true)
                .logResponses(false) // embeddings are huge in logs
                .build();

        TextSegment segment1 = TextSegment.from("hello");
        TextSegment segment2 = TextSegment.from("hi");
        TextSegment segment3 = TextSegment.from("there");

        // when
        Response<List<Embedding>> response = model.embedAll(asList(segment1, segment2, segment3));

        // then
        assertThat(response.content()).hasSize(3);

        Embedding embedding1 = response.content().get(0);
        Embedding embedding2 = response.content().get(1);
        Embedding embedding3 = response.content().get(2);

        double cosineSimilarity12 = CosineSimilarity.between(embedding1, embedding2);
        double cosineSimilarity13 = CosineSimilarity.between(embedding1, embedding3);
        Integer totalTokenCount = response.tokenUsage().totalTokenCount();

        assertThat(response.finishReason()).isNull();

        // given the model with lateChunking enabled
        model = JinaEmbeddingModel.builder()
                .apiKey(System.getenv("JINA_API_KEY"))
                .modelName("jina-embeddings-v3")
                .lateChunking(true)
                .logRequests(true)
                .logResponses(false) // embeddings are huge in logs
                .build();

        // when embedding the same segments
        response = model.embedAll(asList(segment1, segment2, segment3));

        // then
        assertThat(response.content()).hasSize(3);

        embedding1 = response.content().get(0);
        embedding2 = response.content().get(1);
        embedding3 = response.content().get(2);

        double cosineSimilarity12LateChunking = CosineSimilarity.between(embedding1, embedding2);
        double cosineSimilarity13LateChunking = CosineSimilarity.between(embedding1, embedding3);
        Integer totalTokenCountLateChunking = response.tokenUsage().totalTokenCount();

        // validate differences between lateChunking enabled and disabeld
        assertThat(cosineSimilarity12LateChunking).isNotEqualTo(cosineSimilarity12);
        assertThat(cosineSimilarity13LateChunking).isNotEqualTo(cosineSimilarity13);
        assertThat(totalTokenCountLateChunking).isNotEqualTo(totalTokenCount);
    }
}
