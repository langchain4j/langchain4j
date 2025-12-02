package dev.langchain4j.model.embedding.onnx.e5smallv2;

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

class E5SmallV2EmbeddingModelIT {

    @Test
    void should_embed() {

        EmbeddingModel model = new E5SmallV2EmbeddingModel();

        Embedding first = model.embed("query: hi").content();
        assertThat(first.vector()).hasSize(384);

        Embedding second = model.embed("query: hello").content();
        assertThat(second.vector()).hasSize(384);

        double cosineSimilarity = CosineSimilarity.between(first, second);
        assertThat(RelevanceScore.fromCosineSimilarity(cosineSimilarity)).isGreaterThan(0.98);
    }

    @Test
    void should_embed_multiple_segments() {

        EmbeddingModel model = new E5SmallV2EmbeddingModel();
        TextSegment first = TextSegment.from("query: hi");
        TextSegment second = TextSegment.from("query: hello");

        Response<List<Embedding>> response = model.embedAll(asList(first, second));

        List<Embedding> embeddings = response.content();
        assertThat(embeddings).hasSize(2);

        assertThat(embeddings.get(0)).isEqualTo(model.embed(first).content());
        assertThat(embeddings.get(1)).isEqualTo(model.embed(second).content());

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(6);
        assertThat(tokenUsage.outputTokenCount()).isNull();
        assertThat(tokenUsage.totalTokenCount()).isEqualTo(6);

        assertThat(response.finishReason()).isNull();
    }

    @Test
    void embedding_should_have_the_same_values_as_embedding_produced_by_transformers_python_lib() {

        EmbeddingModel model = new E5SmallV2EmbeddingModel();

        Embedding embedding = model.embed("query: I love transformers.").content();

        assertThat(embedding.vector()[0]).isCloseTo(-0.0663562790f, withPercentage(1));
        assertThat(embedding.vector()[1]).isCloseTo(0.0153982891f, withPercentage(1));
        assertThat(embedding.vector()[382]).isCloseTo(-0.0412562378f, withPercentage(1));
        assertThat(embedding.vector()[383]).isCloseTo(-0.0130311009f, withPercentage(1));
    }

    @Test
    void should_embed_510_token_long_text() {

        EmbeddingModel model = new E5SmallV2EmbeddingModel();

        String oneToken = "hello ";

        Embedding embedding = model.embed(repeat(oneToken, 510)).content();

        assertThat(embedding.vector()).hasSize(384);
    }

    @Test
    void should_embed_text_longer_than_510_tokens_by_splitting_and_averaging_embeddings_of_splits() {

        EmbeddingModel model = new E5SmallV2EmbeddingModel();

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

        EmbeddingModel model = new E5SmallV2EmbeddingModel();

        String oneToken = "hello ";

        assertThat(magnitudeOf(model.embed(oneToken).content()))
                .isCloseTo(1, withPercentage(0.01));
        assertThat(magnitudeOf(model.embed(repeat(oneToken, 999)).content()))
                .isCloseTo(1, withPercentage(0.01));
    }

    @Test
    void should_return_correct_dimension() {

        EmbeddingModel model = new E5SmallV2EmbeddingModel();

        assertThat(model.dimension()).isEqualTo(384);
    }
}