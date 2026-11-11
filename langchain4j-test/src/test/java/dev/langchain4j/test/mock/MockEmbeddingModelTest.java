package dev.langchain4j.test.mock;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.embedding.listener.EmbeddingModelListener;
import dev.langchain4j.model.embedding.request.EmbeddingInputType;
import dev.langchain4j.model.embedding.request.EmbeddingRequest;
import dev.langchain4j.model.embedding.request.EmbeddingRequestParameters;
import dev.langchain4j.model.embedding.response.EmbeddingResponse;
import dev.langchain4j.store.embedding.CosineSimilarity;
import dev.langchain4j.store.embedding.RelevanceScore;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.Test;

class MockEmbeddingModelTest {

    @Test
    void should_always_embed_with_the_given_vector() {

        MockEmbeddingModel model = MockEmbeddingModel.thatEmbeds(1f, 2f, 3f);

        assertThat(vector(model, "one")).containsExactly(1f, 2f, 3f);
        assertThat(vector(model, "two")).containsExactly(1f, 2f, 3f);
    }

    @Test
    void should_compute_the_vector_from_the_text() {

        MockEmbeddingModel model = MockEmbeddingModel.thatEmbeds(text -> new float[] {text.length()});

        assertThat(vector(model, "one")).containsExactly(3f);
        assertThat(vector(model, "three")).containsExactly(5f);
    }

    @Test
    void should_record_the_requests() {

        MockEmbeddingModel model = MockEmbeddingModel.thatEmbeds(1f);

        model.embed("first");

        assertThat(model.request().inputs().get(0).text()).isEqualTo("first");

        model.embed("second");

        assertThat(model.requests()).hasSize(2);
        assertThatThrownBy(model::request).hasMessage("Expected exactly 1 embedding request, but received 2. To check each of them, use requests(), for example requests().get(0)");
    }

    @Test
    void should_embed_by_words_with_384_dimensions_and_unit_length() {

        float[] vector = vector(MockEmbeddingModel.thatEmbedsByWords(), "cancel my booking");

        assertThat(vector).hasSize(384);
        double length = 0;
        for (float value : vector) {
            length += value * value;
        }
        assertThat(Math.sqrt(length)).isCloseTo(1.0, within(1e-6));
    }

    @Test
    void should_embed_by_words_with_the_given_dimension() {

        assertThat(vector(MockEmbeddingModel.thatEmbedsByWords(50), "cancel my booking"))
                .hasSize(50);
    }

    @Test
    void should_not_accept_a_dimension_that_is_not_positive() {

        assertThatThrownBy(() -> MockEmbeddingModel.thatEmbedsByWords(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dimension");
    }

    @Test
    void should_embed_the_same_words_with_the_same_vector_ignoring_case_and_punctuation() {

        MockEmbeddingModel model = MockEmbeddingModel.thatEmbedsByWords();

        assertThat(vector(model, "Cancel, my BOOKING!")).containsExactly(vector(model, "cancel my booking"));
    }

    @Test
    void should_make_texts_closer_the_more_words_they_share() {

        MockEmbeddingModel model = MockEmbeddingModel.thatEmbedsByWords();
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

        assertThat(vector(MockEmbeddingModel.thatEmbedsByWords(3), "?!")).containsExactly(0f, 0f, 0f);
    }

    @Test
    void should_give_relevance_scores_by_shared_words() {

        MockEmbeddingModel model = MockEmbeddingModel.thatEmbedsByWords();
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

        MockEmbeddingModel model = MockEmbeddingModel.thatEmbedsByWords();

        assertThat(vector(model, "日本語")).isNotEqualTo(new float[384]);
        assertThat(vector(model, "日本語")).isNotEqualTo(vector(model, "中文"));
        assertThat(vector(model, "Größe")).isEqualTo(vector(model, "größe"));
        // vowel signs are combining marks: they belong to the word, so these two words share no part
        assertThat(similarity(model, "बुकिंग", "बैंक")).isCloseTo(0.0, within(1e-6));
    }

    @Test
    void should_embed_every_input_of_a_request_in_order() {

        MockEmbeddingModel model = MockEmbeddingModel.thatEmbedsByWords();

        List<Embedding> embeddings = model.embedAll(
                        List.of(TextSegment.from("cancel my booking"), TextSegment.from("pets")))
                .content();

        assertThat(model.request().inputs()).hasSize(2);
        assertThat(embeddings)
                .containsExactly(
                        model.embed("cancel my booking").content(),
                        model.embed("pets").content());
    }

    @Test
    void should_record_asynchronous_requests_at_once_and_embed_on_another_thread() {

        Thread caller = Thread.currentThread();
        List<Thread> embeddedOn = new CopyOnWriteArrayList<>();
        CountDownLatch release = new CountDownLatch(1);
        MockEmbeddingModel model = MockEmbeddingModel.thatEmbeds(text -> {
            embeddedOn.add(Thread.currentThread());
            try {
                release.await(5, SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return new float[] {1f, 2f};
        });

        CompletableFuture<EmbeddingResponse> future =
                model.embedAsync(EmbeddingRequest.builder().input("hello").build());

        assertThat(model.request().inputs().get(0).text()).isEqualTo("hello");
        release.countDown();
        assertThat(future)
                .succeedsWithin(5, SECONDS)
                .satisfies(response -> assertThat(response.embeddings().get(0).vector())
                        .containsExactly(1f, 2f));
        assertThat(embeddedOn).singleElement().isNotSameAs(caller);
        assertThat(model.requests()).hasSize(1);
    }

    @Test
    void should_fail_every_request_with_the_given_exception() {

        RuntimeException failure = new IllegalStateException("down");
        MockEmbeddingModel model = MockEmbeddingModel.thatFails(failure);

        assertThatThrownBy(() -> model.embed("hello")).isSameAs(failure);
        assertThatThrownBy(() -> model.embed("again")).isSameAs(failure);
    }

    @Test
    void should_accept_the_input_type() {

        MockEmbeddingModel model = MockEmbeddingModel.thatEmbedsByWords();

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

        MockEmbeddingModel model = MockEmbeddingModel.thatEmbedsByWords(50);

        assertThat(model.dimension()).isEqualTo(50);
        assertThat(model.requests()).isEmpty();
    }

    @Test
    void should_report_the_model_name_of_the_request_and_no_token_usage() {

        MockEmbeddingModel model = MockEmbeddingModel.thatEmbeds(1f);

        EmbeddingResponse response = model.embed(
                EmbeddingRequest.builder().input("hello").modelName("embed-test").build());

        assertThat(response.modelName()).isEqualTo("embed-test");
        assertThat(response.tokenUsage()).isNull();
        assertThat(model.embed(EmbeddingRequest.builder().input("hello").build()).modelName())
                .isNull();
    }

    @Test
    void should_take_the_listeners_as_a_list() {

        EmbeddingModelListener listener = new EmbeddingModelListener() {};
        List<EmbeddingModelListener> listeners = new ArrayList<>(List.of(listener));
        MockEmbeddingModel model = MockEmbeddingModel.thatEmbeds(1f).withListeners(listeners);

        listeners.clear();

        assertThat(model.listeners()).containsExactly(listener);
    }

    @Test
    void should_not_share_the_given_vector() {

        float[] vector = {1f, 2f};
        MockEmbeddingModel model = MockEmbeddingModel.thatEmbeds(vector);

        vector[0] = 5f;
        vector(model, "one")[1] = 5f;

        assertThat(vector(model, "two")).containsExactly(1f, 2f);
    }

    @Test
    void should_not_accept_an_empty_vector() {

        assertThatThrownBy(MockEmbeddingModel::thatEmbeds)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("thatEmbeds");
    }

    @Test
    void should_always_throw_the_given_exception() {

        RuntimeException failure = new IllegalStateException("down");

        assertThatThrownBy(() -> MockEmbeddingModel.thatFails(failure).embed("hello"))
                .isSameAs(failure);
    }

    @Test
    void should_fail_the_future_of_an_asynchronous_call_with_the_given_exception() {

        RuntimeException failure = new IllegalStateException("down");

        assertThat(MockEmbeddingModel.thatFails(failure)
                        .embedAsync(EmbeddingRequest.builder().input("hello").build()))
                .failsWithin(5, SECONDS)
                .withThrowableThat()
                .havingCause()
                .isSameAs(failure);
    }

    @Test
    void should_not_accept_a_requested_dimension_like_a_model_without_that_parameter() {

        MockEmbeddingModel model = MockEmbeddingModel.thatEmbedsByWords();
        EmbeddingRequest request = EmbeddingRequest.builder()
                .input("hello")
                .parameters(EmbeddingRequestParameters.builder().dimensions(256).build())
                .build();

        assertThatThrownBy(() -> model.embed(request))
                .isInstanceOf(UnsupportedFeatureException.class)
                .hasMessageContaining("dimensions");
    }

    @Test
    void should_not_accept_null_arguments() {

        assertThatThrownBy(() -> MockEmbeddingModel.thatEmbeds((float[]) null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MockEmbeddingModel.thatFails(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static float[] vector(MockEmbeddingModel model, String text) {
        return model.embed(text).content().vector();
    }

    private static double similarity(MockEmbeddingModel model, String first, String second) {
        Embedding a = model.embed(first).content();
        Embedding b = model.embed(second).content();
        return CosineSimilarity.between(a, b);
    }
}
