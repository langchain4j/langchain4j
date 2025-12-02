package dev.langchain4j.model.embedding.onnx.bgesmallen;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.store.embedding.CosineSimilarity;
import dev.langchain4j.store.embedding.RelevanceScore;
import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.langchain4j.internal.Utils.repeat;
import static dev.langchain4j.model.embedding.onnx.internal.VectorUtils.magnitudeOf;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Percentage.withPercentage;

class BgeSmallEnEmbeddingModelIT {

    @Test
    void should_embed() {

        EmbeddingModel model = new BgeSmallEnEmbeddingModel();

        Embedding first = model.embed("hi").content();
        assertThat(first.vector()).hasSize(384);

        Embedding second = model.embed("hello").content();
        assertThat(second.vector()).hasSize(384);

        double cosineSimilarity = CosineSimilarity.between(first, second);
        assertThat(RelevanceScore.fromCosineSimilarity(cosineSimilarity)).isGreaterThan(0.97);
    }

    @Test
    void should_embed_multiple_segments() {

        EmbeddingModel model = new BgeSmallEnEmbeddingModel();
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
    void embedding_should_have_the_same_values_as_embedding_produced_by_sentence_transformers_python_lib() {

        EmbeddingModel model = new BgeSmallEnEmbeddingModel();

        Embedding embedding = model.embed("I love cool flags!").content();

        assertThat(embedding.vector()[0]).isCloseTo(-0.0440094993f, withPercentage(1));
        assertThat(embedding.vector()[1]).isCloseTo(0.0160218030f, withPercentage(1));
        assertThat(embedding.vector()[382]).isCloseTo(-0.0074426383f, withPercentage(1));
        assertThat(embedding.vector()[383]).isCloseTo(-0.0055019930f, withPercentage(1));
    }

    @Test
    void should_embed_510_token_long_text() {

        EmbeddingModel model = new BgeSmallEnEmbeddingModel();

        String oneToken = "hello ";

        Embedding embedding = model.embed(repeat(oneToken, 510)).content();

        assertThat(embedding.vector()).hasSize(384);
    }

    @Test
    void should_embed_text_longer_than_510_tokens_by_splitting_and_averaging_embeddings_of_splits() {

        EmbeddingModel model = new BgeSmallEnEmbeddingModel();

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

        EmbeddingModel model = new BgeSmallEnEmbeddingModel();

        String oneToken = "hello ";

        assertThat(magnitudeOf(model.embed(oneToken).content()))
                .isCloseTo(1, withPercentage(0.01));
        assertThat(magnitudeOf(model.embed(repeat(oneToken, 999)).content()))
                .isCloseTo(1, withPercentage(0.01));
    }

    @Test
    void should_return_correct_dimension() {

        EmbeddingModel model = new BgeSmallEnEmbeddingModel();

        assertThat(model.dimension()).isEqualTo(384);
    }
}