package dev.langchain4j.model.embedding.onnx.allminilml6v2q;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.store.embedding.CosineSimilarity;
import dev.langchain4j.store.embedding.RelevanceScore;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static dev.langchain4j.internal.Utils.repeat;
import static dev.langchain4j.model.embedding.onnx.internal.VectorUtils.magnitudeOf;
import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Percentage.withPercentage;

class AllMiniLmL6V2QuantizedEmbeddingModelIT {

    @Test
    void should_embed() {

        EmbeddingModel model = new AllMiniLmL6V2QuantizedEmbeddingModel();

        Embedding first = model.embed("hi").content();
        assertThat(first.vector()).hasSize(384);

        Embedding second = model.embed("hello").content();
        assertThat(second.vector()).hasSize(384);

        double cosineSimilarity = CosineSimilarity.between(first, second);
        assertThat(RelevanceScore.fromCosineSimilarity(cosineSimilarity)).isGreaterThan(0.9);
    }

    @Test
    void should_embed_multiple_segments() {

        EmbeddingModel model = new AllMiniLmL6V2QuantizedEmbeddingModel();
        TextSegment first = TextSegment.from("hi");
        TextSegment second = TextSegment.from("hello");

        Response<List<Embedding>> response = model.embedAll(asList(first, second));

        List<Embedding> embeddings = response.content();
        assertThat(embeddings).hasSize(2);

        assertThat(embeddings.get(0)).isEqualTo(model.embed(first).content());
        assertThat(embeddings.get(1)).isEqualTo(model.embed(second).content());

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(2);
        assertThat(tokenUsage.outputTokenCount()).isNull();
        assertThat(tokenUsage.totalTokenCount()).isEqualTo(2);

        assertThat(response.finishReason()).isNull();
    }

    @Test
    void embedding_should_have_similar_values_to_embedding_produced_by_sentence_transformers_python_lib() {

        EmbeddingModel model = new AllMiniLmL6V2QuantizedEmbeddingModel();

        Embedding embedding = model.embed("I love sentence transformers.").content();

        assertThat(embedding.vector()[0]).isCloseTo(-0.0803190097f, withPercentage(18));
        assertThat(embedding.vector()[1]).isCloseTo(-0.0171345081f, withPercentage(18));
        assertThat(embedding.vector()[382]).isCloseTo(0.0478825271f, withPercentage(18));
        assertThat(embedding.vector()[383]).isCloseTo(-0.0561899580f, withPercentage(18));
    }

    @Test
    void should_embed_510_token_long_text() {

        EmbeddingModel model = new AllMiniLmL6V2QuantizedEmbeddingModel();

        String oneToken = "hello ";

        Embedding embedding = model.embed(repeat(oneToken, 510)).content();

        assertThat(embedding.vector()).hasSize(384);
    }

    @Test
    void should_embed_text_longer_than_510_tokens_by_splitting_and_averaging_embeddings_of_splits() {

        EmbeddingModel model = new AllMiniLmL6V2QuantizedEmbeddingModel();

        String oneToken = "hello ";

        Embedding embedding510 = model.embed(repeat(oneToken, 510)).content();
        assertThat(embedding510.vector()).hasSize(384);

        Embedding embedding511 = model.embed(repeat(oneToken, 511)).content();
        assertThat(embedding511.vector()).hasSize(384);

        double cosineSimilarity = CosineSimilarity.between(embedding510, embedding511);
        assertThat(RelevanceScore.fromCosineSimilarity(cosineSimilarity)).isGreaterThan(0.99);
    }

    @Test
    void should_produce_normalized_vectors() {

        EmbeddingModel model = new AllMiniLmL6V2QuantizedEmbeddingModel();

        String oneToken = "hello ";

        assertThat(magnitudeOf(model.embed(oneToken).content()))
                .isCloseTo(1, withPercentage(0.01));
        assertThat(magnitudeOf(model.embed(repeat(oneToken, 999)).content()))
                .isCloseTo(1, withPercentage(0.01));
    }

    @Test
    void should_embed_concurrently() throws Exception {

        // given
        EmbeddingModel model = new AllMiniLmL6V2QuantizedEmbeddingModel();
        String text = "This is a test sentence to embed";
        Embedding referenceEmbedding = model.embed(text).content();

        // when
        int numThreads = 10_000;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        List<Future<Embedding>> futures = new ArrayList<>();

        for (int i = 0; i < numThreads; i++) {
            futures.add(executor.submit(() -> model.embed(text).content()));
        }

        executor.shutdown();
        executor.awaitTermination(15, SECONDS);

        // then
        for (Future<Embedding> future : futures) {
            Embedding embedding = future.get();
            assertThat(embedding).isEqualTo(referenceEmbedding);
        }
    }

    @Test
    void should_return_correct_dimension() {

        EmbeddingModel model = new AllMiniLmL6V2QuantizedEmbeddingModel();

        assertThat(model.dimension()).isEqualTo(384);
    }
}