package dev.langchain4j.test.mock;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.request.EmbeddingInputType;
import dev.langchain4j.model.embedding.request.EmbeddingRequest;
import dev.langchain4j.model.embedding.request.EmbeddingRequestParameters;
import dev.langchain4j.model.embedding.response.EmbeddingResponse;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.store.embedding.CosineSimilarity;
import dev.langchain4j.store.embedding.RelevanceScore;
import org.junit.jupiter.api.Test;

class EmbeddingModelMockTest {

    @Test
    void should_always_embed_with_the_given_vector() {

        EmbeddingModelMock model = EmbeddingModelMock.thatEmbeds(1f, 2f, 3f);

        assertThat(vector(model, "one")).containsExactly(1f, 2f, 3f);
        assertThat(vector(model, "two")).containsExactly(1f, 2f, 3f);
    }

    @Test
    void should_compute_the_vector_from_the_text() {

        EmbeddingModelMock model = EmbeddingModelMock.thatEmbeds(text -> new float[] {text.length()});

        assertThat(vector(model, "one")).containsExactly(3f);
        assertThat(vector(model, "three")).containsExactly(5f);
    }

    @Test
    void should_record_the_requests() {

        EmbeddingModelMock model = EmbeddingModelMock.thatEmbeds(1f);

        model.embed("first");

        assertThat(model.request().inputs().get(0).text()).isEqualTo("first");

        model.embed("second");

        assertThat(model.requests()).hasSize(2);
        assertThatThrownBy(model::request).hasMessage("Expected exactly 1 embedding request, but received 2");
    }

    @Test
    void should_embed_by_words_with_384_dimensions_and_unit_length() {

        float[] vector = vector(EmbeddingModelMock.thatEmbedsByWords(), "cancel my booking");

        assertThat(vector).hasSize(384);
        double length = 0;
        for (float value : vector) {
            length += value * value;
        }
        assertThat(Math.sqrt(length)).isCloseTo(1.0, within(1e-6));
    }

    @Test
    void should_embed_by_words_with_the_given_dimension() {

        assertThat(vector(EmbeddingModelMock.thatEmbedsByWords(50), "cancel my booking"))
                .hasSize(50);
    }

    @Test
    void should_not_accept_a_dimension_that_is_not_positive() {

        assertThatThrownBy(() -> EmbeddingModelMock.thatEmbedsByWords(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dimension");
    }

    @Test
    void should_embed_the_same_words_with_the_same_vector_ignoring_case_and_punctuation() {

        EmbeddingModelMock model = EmbeddingModelMock.thatEmbedsByWords();

        assertThat(vector(model, "Cancel, my BOOKING!")).containsExactly(vector(model, "cancel my booking"));
    }

    @Test
    void should_make_texts_closer_the_more_words_they_share() {

        EmbeddingModelMock model = EmbeddingModelMock.thatEmbedsByWords();
        String query = "cancel my booking";

        double twoSharedWords = similarity(model, query, "cancel a booking today");
        double oneSharedWord = similarity(model, query, "a booking today");
        double noSharedWords = similarity(model, query, "pets are allowed");

        assertThat(twoSharedWords).isGreaterThan(oneSharedWord);
        assertThat(oneSharedWord).isGreaterThan(noSharedWords);
        assertThat(noSharedWords).isCloseTo(0.0, within(1e-6));
    }

    @Test
    void should_return_a_zero_vector_for_a_text_without_words() {

        assertThat(vector(EmbeddingModelMock.thatEmbedsByWords(3), "?!")).containsExactly(0f, 0f, 0f);
    }

    @Test
    void should_give_relevance_scores_by_shared_words() {

        EmbeddingModelMock model = EmbeddingModelMock.thatEmbedsByWords();
        String query = "How do I cancel my booking?";

        double twoSharedWords = RelevanceScore.fromCosineSimilarity(
                similarity(model, query, "You can cancel a booking free of charge up to 24 hours before departure."));
        double oneSharedWord = RelevanceScore.fromCosineSimilarity(
                similarity(model, query, "Refunds are paid within 14 days of a cancelled booking."));
        double noSharedWords =
                RelevanceScore.fromCosineSimilarity(similarity(model, query, "Pets are allowed on all routes."));

        assertThat(twoSharedWords).isGreaterThan(oneSharedWord);
        assertThat(oneSharedWord).isGreaterThan(0.55);
        assertThat(noSharedWords).isCloseTo(0.5, within(1e-6));
    }

    @Test
    void should_embed_words_of_any_language() {

        EmbeddingModelMock model = EmbeddingModelMock.thatEmbedsByWords();

        assertThat(vector(model, "日本語")).isNotEqualTo(new float[384]);
        assertThat(vector(model, "日本語")).isNotEqualTo(vector(model, "中文"));
        assertThat(vector(model, "Größe")).isEqualTo(vector(model, "größe"));
    }

    @Test
    void should_support_asynchronous_calls_by_default() {

        EmbeddingModelMock model = EmbeddingModelMock.thatEmbedsByWords();

        assertThat(model.embedAsync(EmbeddingRequest.builder().input("hello").build()))
                .succeedsWithin(5, SECONDS);
    }

    @Test
    void should_accept_the_input_type() {

        EmbeddingModelMock model = EmbeddingModelMock.thatEmbedsByWords();

        model.embed(EmbeddingRequest.builder()
                .input("hello")
                .parameters(EmbeddingRequestParameters.builder()
                        .inputType(EmbeddingInputType.QUERY)
                        .build())
                .build());

        assertThat(model.request().parameters().inputType()).isEqualTo(EmbeddingInputType.QUERY);
    }

    @Test
    void should_not_record_a_request_for_the_dimension() {

        EmbeddingModelMock model = EmbeddingModelMock.thatEmbedsByWords(50);

        assertThat(model.dimension()).isEqualTo(50);
        assertThat(model.requests()).isEmpty();
    }

    @Test
    void should_report_the_given_model_name_and_token_usage() {

        EmbeddingModelMock model = EmbeddingModelMock.thatEmbeds(1f)
                .withModelName("model")
                .withTokenUsage(new TokenUsage(1, 0));

        EmbeddingResponse response = model.embed(EmbeddingRequest.builder().input("hello").build());

        assertThat(response.modelName()).isEqualTo("model");
        assertThat(response.tokenUsage()).isEqualTo(new TokenUsage(1, 0));
    }

    @Test
    void should_not_share_the_given_vector() {

        float[] vector = {1f, 2f};
        EmbeddingModelMock model = EmbeddingModelMock.thatEmbeds(vector);

        vector[0] = 5f;
        vector(model, "one")[1] = 5f;

        assertThat(vector(model, "two")).containsExactly(1f, 2f);
    }

    @Test
    void should_not_accept_an_empty_vector() {

        assertThatThrownBy(EmbeddingModelMock::thatEmbeds)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("thatEmbeds");
    }

    @Test
    void should_always_throw_the_given_exception() {

        RuntimeException failure = new IllegalStateException("down");

        assertThatThrownBy(() -> EmbeddingModelMock.thatThrows(failure).embed("hello"))
                .isSameAs(failure);
    }

    @Test
    void should_not_accept_null_arguments() {

        assertThatThrownBy(() -> EmbeddingModelMock.thatEmbeds((float[]) null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EmbeddingModelMock.thatThrows(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static float[] vector(EmbeddingModelMock model, String text) {
        return model.embed(text).content().vector();
    }

    private static double similarity(EmbeddingModelMock model, String first, String second) {
        Embedding a = model.embed(first).content();
        Embedding b = model.embed(second).content();
        return CosineSimilarity.between(a, b);
    }
}
