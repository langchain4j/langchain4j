package dev.langchain4j.model.jina;

import static java.time.Duration.ofSeconds;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.CosineSimilarity;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JinaEmbeddingModelIT {

    @Test
    @DisplayName("Single text segment to embed, using Jina embedding model: jina-embeddings-v2-base-en")
    void should_embed_single_text_v2() {

        // given
        EmbeddingModel model = JinaEmbeddingModel.builder()
                .apiKey(System.getenv("JINA_API_KEY"))
                .modelName("jina-embeddings-v2-base-en")
                .timeout(ofSeconds(10))
                .logRequests(true)
                .logResponses(false) // embeddings are huge in logs
                .build();

        String text = "hello";

        // when
        Response<Embedding> response = model.embed(text);

        // then
        assertThat(response.content().dimension()).isEqualTo(768);

        assertThat(response.tokenUsage().inputTokenCount()).isEqualTo(3);
        assertThat(response.tokenUsage().outputTokenCount()).isZero();
        assertThat(response.tokenUsage().totalTokenCount()).isEqualTo(3);

        assertThat(response.finishReason()).isNull();
    }

    @Test
    @DisplayName(
            "Single text segments to embed, using Jina embedding model: jina-embeddings-v3; late chunking disabled")
    void should_embed_single_text() {

        // given
        EmbeddingModel model = JinaEmbeddingModel.builder()
                .apiKey(System.getenv("JINA_API_KEY"))
                .modelName("jina-embeddings-v3")
                .timeout(ofSeconds(10))
                .lateChunking(false)
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
    @DisplayName("Multiple text segments to embed, using Jina embedding model: jina-embeddings-v2-base-en")
    void should_embed_multiple_segments_v2() {

        // given
        EmbeddingModel model = JinaEmbeddingModel.builder()
                .apiKey(System.getenv("JINA_API_KEY"))
                .modelName("jina-embeddings-v2-base-en")
                .timeout(ofSeconds(10))
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
        assertThat(embedding1.dimension()).isEqualTo(768);

        Embedding embedding2 = response.content().get(1);
        assertThat(embedding2.dimension()).isEqualTo(768);

        Embedding embedding3 = response.content().get(2);
        assertThat(embedding3.dimension()).isEqualTo(768);

        assertThat(CosineSimilarity.between(embedding1, embedding2)).isGreaterThan(0.9);

        assertThat(response.tokenUsage().inputTokenCount()).isEqualTo(9);
        assertThat(response.tokenUsage().outputTokenCount()).isZero();
        assertThat(response.tokenUsage().totalTokenCount()).isEqualTo(9);

        assertThat(response.finishReason()).isNull();
    }

    @Test
    @DisplayName(
            "Multiple text segments to embed, using Jina embedding model: jina-embeddings-v3; late chunking DISABLED")
    void should_embed_multiple_segments() {

        // given
        EmbeddingModel model = JinaEmbeddingModel.builder()
                .apiKey(System.getenv("JINA_API_KEY"))
                .modelName("jina-embeddings-v3")
                .timeout(ofSeconds(10))
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
        assertThat(embedding1.dimension()).isEqualTo(1024);

        Embedding embedding2 = response.content().get(1);
        assertThat(embedding2.dimension()).isEqualTo(1024);

        Embedding embedding3 = response.content().get(2);
        assertThat(embedding3.dimension()).isEqualTo(1024);

        assertThat(CosineSimilarity.between(embedding1, embedding2)).isGreaterThan(0.85);
        assertThat(CosineSimilarity.between(embedding1, embedding3)).isGreaterThan(0.6);

        assertThat(response.tokenUsage().inputTokenCount()).isEqualTo(10);
        assertThat(response.tokenUsage().outputTokenCount()).isZero();
        assertThat(response.tokenUsage().totalTokenCount()).isEqualTo(10);

        assertThat(response.finishReason()).isNull();
    }

    @Test
    @DisplayName(
            "Multiple text segments to embed, using Jina embedding model: jina-embeddings-v3; late chunking ENABLED")
    void should_embed_multiple_segments_with_late_chunking() {

        // given
        EmbeddingModel model = JinaEmbeddingModel.builder()
                .apiKey(System.getenv("JINA_API_KEY"))
                .modelName("jina-embeddings-v3")
                .timeout(ofSeconds(10))
                .lateChunking(true)
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

        assertThat(CosineSimilarity.between(embedding1, embedding2)).isGreaterThan(0.95);
        assertThat(CosineSimilarity.between(embedding1, embedding3)).isGreaterThan(0.95);

        assertThat(response.tokenUsage().inputTokenCount()).isEqualTo(6);
        assertThat(response.tokenUsage().outputTokenCount()).isZero();
        assertThat(response.tokenUsage().totalTokenCount()).isEqualTo(6);

        assertThat(response.finishReason()).isNull();
    }

    @Test
    @DisplayName(
            "Multiple text segments to embed, using Jina embedding model: jina-embeddings-v3; late chunking compare ENABLED with DISABLED")
    void should_embed_multiple_segments_compare_with_late_chunking() {

        // given
        EmbeddingModel model = JinaEmbeddingModel.builder()
                .apiKey(System.getenv("JINA_API_KEY"))
                .modelName("jina-embeddings-v3")
                .timeout(ofSeconds(10))
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
                .timeout(ofSeconds(10))
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
