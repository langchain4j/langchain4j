package dev.langchain4j.test.mock;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.exception.InternalServerException;
import dev.langchain4j.exception.RateLimitException;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.embedding.listener.EmbeddingModelListener;
import dev.langchain4j.model.embedding.request.EmbeddingInputType;
import dev.langchain4j.model.embedding.request.EmbeddingRequest;
import dev.langchain4j.model.embedding.request.EmbeddingRequestParameters;
import dev.langchain4j.model.embedding.response.EmbeddingResponse;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.store.embedding.CosineSimilarity;
import dev.langchain4j.store.embedding.RelevanceScore;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class MockEmbeddingModelTest {

    @Test
    void should_embed_every_request_with_the_given_vector_when_repeating() {

        MockEmbeddingModel model = MockEmbeddingModel.thatEmbeds(1f, 2f, 3f).thenRepeats();

        assertThat(vector(model, "one")).containsExactly(1f, 2f, 3f);
        assertThat(vector(model, "two")).containsExactly(1f, 2f, 3f);
    }

    @Test
    void should_compute_the_vector_from_the_text() {

        MockEmbeddingModel model =
                MockEmbeddingModel.thatEmbeds(text -> new float[] {text.length()}).thenRepeats();

        assertThat(vector(model, "one")).containsExactly(3f);
        assertThat(vector(model, "three")).containsExactly(5f);
    }

    @Test
    void should_record_the_requests() {

        MockEmbeddingModel model = MockEmbeddingModel.thatEmbeds(1f).thenRepeats();

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
                        text -> text.contains("refund") ? new float[] {1, 0} : new float[] {0, 1})
                .thenRepeats();

        assertThat(relevanceScore(model, "How do I get a refund?", "A refund takes 5 days."))
                .isCloseTo(1.0, within(1e-6));
        assertThat(relevanceScore(model, "How do I get a refund?", "Pets are allowed."))
                .isCloseTo(0.5, within(1e-6));
    }

    @Test
    void should_embed_every_input_of_a_request_in_order() {

        MockEmbeddingModel model =
                MockEmbeddingModel.thatEmbeds(text -> new float[] {text.length()}).thenRepeats();

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
    void should_fail_every_request_with_the_given_exception_when_repeating() {

        RuntimeException failure = new IllegalStateException("down");
        MockEmbeddingModel model = MockEmbeddingModel.thatFails(failure).thenRepeats();

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

        MockEmbeddingModel model = MockEmbeddingModel.thatEmbeds(1f).thenRepeats();

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
        MockEmbeddingModel model = MockEmbeddingModel.thatEmbeds(vector).thenRepeats();

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
        assertThatThrownBy(() -> MockEmbeddingModel.thatEmbeds((Function<String, float[]>) null))
                .isInstanceOf(IllegalArgumentException.class);
        MockEmbeddingModel model = MockEmbeddingModel.thatEmbeds(1f);
        assertThatThrownBy(() -> model.thenEmbeds((float[]) null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> model.thenEmbeds((Function<String, float[]>) null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> model.thenFails(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(model::thenEmbeds).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_answer_one_request_per_step_and_fail_a_request_after_the_last_one() {

        MockEmbeddingModel model = MockEmbeddingModel.thatEmbeds(1f)
                .thenEmbeds(2f)
                .thenEmbeds(text -> new float[] {text.length()});

        assertThat(vector(model, "one")).containsExactly(1f);
        assertThat(vector(model, "two")).containsExactly(2f);
        assertThat(vector(model, "three")).containsExactly(5f);
        assertThatThrownBy(() -> model.embed("four"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MockEmbeddingModel received request 4, but its script has only 3 steps")
                .hasMessageContaining("thenRepeats()")
                .hasMessageContaining("The texts of request 4: \"four\"");
        assertThat(model.requests()).hasSize(4);
    }

    @Test
    void should_show_the_first_texts_of_a_request_after_the_last_step() {

        MockEmbeddingModel model = MockEmbeddingModel.thatEmbeds(1f);
        model.embed("one");

        assertThatThrownBy(() -> model.embedAll(List.of(
                        TextSegment.from("a"),
                        TextSegment.from("b"),
                        TextSegment.from("c"),
                        TextSegment.from("d"),
                        TextSegment.from("e"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("The texts of request 2: \"a\" \"b\" \"c\" and 2 more");
    }

    @Test
    void should_fail_the_future_of_an_asynchronous_request_after_the_last_step() {

        MockEmbeddingModel model = MockEmbeddingModel.thatEmbeds(1f);
        EmbeddingRequest request = EmbeddingRequest.builder().input("hello").build();

        assertThat(model.embedAsync(request)).succeedsWithin(5, SECONDS);
        assertThat(model.embedAsync(request))
                .failsWithin(5, SECONDS)
                .withThrowableThat()
                .havingCause()
                .isInstanceOf(IllegalStateException.class)
                .withMessageContaining("received request 2");
    }

    @Test
    void should_let_a_request_fail_and_the_next_one_succeed() {

        RuntimeException rateLimit = new RateLimitException("Too many requests");
        MockEmbeddingModel model = MockEmbeddingModel.thatFails(rateLimit).thenEmbeds(0.1f, 0.2f);

        assertThatThrownBy(() -> model.embed("hello")).isSameAs(rateLimit);
        assertThat(vector(model, "hello")).containsExactly(0.1f, 0.2f);
    }

    @Test
    void should_fail_a_later_step_with_the_given_exception() {

        RuntimeException failure = new InternalServerException("Service unavailable");
        MockEmbeddingModel model = MockEmbeddingModel.thatEmbeds(1f)
                .thenFails(failure)
                .thenEmbeds(2f)
                .thenRepeats();

        assertThat(vector(model, "batch 1")).containsExactly(1f);
        assertThatThrownBy(() -> model.embed("batch 2")).isSameAs(failure);
        assertThat(vector(model, "batch 3")).containsExactly(2f);
        assertThat(vector(model, "batch 4")).containsExactly(2f);
    }

    @Test
    void should_repeat_only_the_last_step_and_compute_it_again_for_each_request() {

        AtomicInteger computed = new AtomicInteger();
        MockEmbeddingModel model = MockEmbeddingModel.thatEmbeds(1f)
                .thenEmbeds(text -> new float[] {computed.incrementAndGet()})
                .thenRepeats();

        assertThat(vector(model, "a")).containsExactly(1f);
        assertThat(vector(model, "b")).containsExactly(1f);
        assertThat(vector(model, "c")).containsExactly(2f);
        assertThat(vector(model, "d")).containsExactly(3f);
    }

    @Test
    void should_not_accept_a_step_after_repeating() {

        MockEmbeddingModel model = MockEmbeddingModel.thatEmbeds(1f).thenRepeats();

        assertThatThrownBy(() -> model.thenEmbeds(2f))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("thenRepeats() must be the last call");
        assertThatThrownBy(model::thenRepeats).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void should_take_the_steps_in_the_order_of_the_async_calls() {

        CountDownLatch release = new CountDownLatch(1);
        MockEmbeddingModel model = MockEmbeddingModel.thatEmbeds(text -> {
                    try {
                        release.await(5, SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return new float[] {1f};
                })
                .thenEmbeds(2f);
        EmbeddingRequest request = EmbeddingRequest.builder().input("hello").build();

        CompletableFuture<EmbeddingResponse> first = model.embedAsync(request);
        CompletableFuture<EmbeddingResponse> second = model.embedAsync(request);

        assertThat(model.requests()).hasSize(2);
        assertThat(second)
                .succeedsWithin(5, SECONDS)
                .satisfies(response -> assertThat(response.embeddings().get(0).vector())
                        .containsExactly(2f));
        release.countDown();
        assertThat(first)
                .succeedsWithin(5, SECONDS)
                .satisfies(response -> assertThat(response.embeddings().get(0).vector())
                        .containsExactly(1f));
    }

    @Test
    void should_not_record_a_null_request() {

        MockEmbeddingModel model = MockEmbeddingModel.thatEmbeds(1f);

        assertThatThrownBy(() -> model.doEmbed(null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(model.doEmbedAsync(null))
                .failsWithin(5, SECONDS)
                .withThrowableThat()
                .havingCause()
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(model.requests()).isEmpty();
        assertThat(vector(model, "hello")).containsExactly(1f);
    }

    @Test
    void should_take_the_dimension_from_the_first_step_that_embeds() {

        RuntimeException failure = new IllegalStateException("down");
        MockEmbeddingModel model = MockEmbeddingModel.thatFails(failure).thenEmbeds(new float[50]);

        assertThat(model.dimension()).isEqualTo(50);
        assertThat(model.requests()).isEmpty();
        assertThatThrownBy(() -> model.embed("hello")).isSameAs(failure);
        assertThatThrownBy(() -> MockEmbeddingModel.thatFails(failure).dimension()).isSameAs(failure);
    }

    private static float[] vector(MockEmbeddingModel model, String text) {
        return model.embed(text).content().vector();
    }

    private static double relevanceScore(MockEmbeddingModel model, String first, String second) {
        Embedding a = model.embed(first).content();
        Embedding b = model.embed(second).content();
        return RelevanceScore.fromCosineSimilarity(CosineSimilarity.between(a, b));
    }

    @Test
    void should_answer_with_exactly_the_given_response_including_its_metadata() {

        EmbeddingResponse given = EmbeddingResponse.builder()
                .embeddings(List.of(Embedding.from(new float[] {0.1f, 0.2f}), Embedding.from(new float[] {0.3f, 0.4f})))
                .modelName("embedding-model")
                .tokenUsage(new TokenUsage(12))
                .build();
        MockEmbeddingModel model = MockEmbeddingModel.thatResponds(given).thenRepeats();

        EmbeddingResponse response =
                model.embed(EmbeddingRequest.builder().inputs("a", "b").build());

        assertThat(response).isEqualTo(given);
        assertThat(response.tokenUsage().inputTokenCount()).isEqualTo(12);
        assertThat(model.dimension()).isEqualTo(2);
    }

    @Test
    void should_fail_a_request_whose_number_of_texts_does_not_match_the_given_response() {

        EmbeddingResponse given = EmbeddingResponse.builder()
                .embeddings(List.of(Embedding.from(new float[] {0.1f, 0.2f})))
                .build();
        MockEmbeddingModel model = MockEmbeddingModel.thatResponds(given);

        assertThatThrownBy(() -> model.embedAll(List.of(TextSegment.from("a"), TextSegment.from("b"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("has 1 embeddings, but the request has 2 texts");
    }

    @Test
    void should_give_the_whole_response_in_a_later_step() {

        EmbeddingResponse given = EmbeddingResponse.builder()
                .embeddings(List.of(Embedding.from(new float[] {0.5f})))
                .tokenUsage(new TokenUsage(3))
                .build();
        MockEmbeddingModel model = MockEmbeddingModel.thatEmbeds(0.1f).thenResponds(given);

        assertThat(model.embed("a").content().vector()).containsExactly(0.1f);
        assertThat(model.embed("b").tokenUsage().inputTokenCount()).isEqualTo(3);
        assertThatThrownBy(() -> model.embed("c")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> MockEmbeddingModel.thatResponds(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> model.thenResponds(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
