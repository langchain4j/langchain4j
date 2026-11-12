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
        assertThatThrownBy(model::request)
                .hasMessage("Expected exactly 1 embedding request, but received 2. To check the last one, use"
                        + " lastRequest(); to check each of them, use requests(), for example requests().get(0)");
        assertThat(model.lastRequest()).isSameAs(model.requests().get(1));
    }

    @Test
    void should_rank_texts_by_the_given_function() {

        MockEmbeddingModel model = MockEmbeddingModel.thatEmbeds(
                text -> text.contains("refund") ? new float[] {1, 0} : new float[] {0, 1});

        assertThat(relevanceScore(model, "How do I get a refund?", "A refund takes 5 days."))
                .isCloseTo(1.0, within(1e-6));
        assertThat(relevanceScore(model, "How do I get a refund?", "Pets are allowed."))
                .isCloseTo(0.5, within(1e-6));
    }

    @Test
    void should_embed_every_input_of_a_request_in_order() {

        MockEmbeddingModel model = MockEmbeddingModel.thatEmbeds(text -> new float[] {text.length()});

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

        MockEmbeddingModel model = MockEmbeddingModel.thatEmbeds(1f);

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

        MockEmbeddingModel model = MockEmbeddingModel.thatEmbeds(new float[50]);

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

        MockEmbeddingModel model = MockEmbeddingModel.thatEmbeds(1f);
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

    private static double relevanceScore(MockEmbeddingModel model, String first, String second) {
        Embedding a = model.embed(first).content();
        Embedding b = model.embed(second).content();
        return RelevanceScore.fromCosineSimilarity(CosineSimilarity.between(a, b));
    }
}
