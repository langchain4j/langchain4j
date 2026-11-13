package dev.langchain4j.test.mock;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.exception.TimeoutException;
import dev.langchain4j.model.scoring.listener.ScoringModelListener;
import dev.langchain4j.model.scoring.request.ScoringRequest;
import dev.langchain4j.model.scoring.response.ScoringResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.ToDoubleFunction;
import org.junit.jupiter.api.Test;

class MockScoringModelTest {

    private static final ScoringRequest REQUEST =
            ScoringRequest.builder().documents(List.of("a", "bb")).query("q").build();

    @Test
    void should_take_the_listeners_as_a_list() {

        ScoringModelListener listener = new ScoringModelListener() {};
        List<ScoringModelListener> listeners = new ArrayList<>(List.of(listener));
        MockScoringModel model = MockScoringModel.thatScores(0.5).withListeners(listeners);

        listeners.clear();

        assertThat(model.listeners()).containsExactly(listener);
    }

    @Test
    void should_score_and_record_the_requests() {

        MockScoringModel model =
                MockScoringModel.thatScores(document -> (double) document.length()).thenRepeats();

        assertThat(model.score(REQUEST).scores()).containsExactly(1.0, 2.0);
        assertThat(model.request().query()).isEqualTo("q");

        model.score(REQUEST);

        assertThat(model.requests()).hasSize(2);
        assertThatThrownBy(model::request)
                .hasMessage("Expected exactly 1 scoring request, but received 2. To check the last one, use"
                        + " lastRequest(); to check each of them, use requests(), for example requests().get(0)");
        assertThat(model.lastRequest()).isSameAs(model.requests().get(1));
    }

    @Test
    void should_record_asynchronous_requests_and_answer_on_another_thread() {

        Thread caller = Thread.currentThread();
        List<Thread> scoredOn = new CopyOnWriteArrayList<>();
        MockScoringModel model = MockScoringModel.thatScores(document -> {
            scoredOn.add(Thread.currentThread());
            return 0.5;
        });

        assertThat(model.scoreAsync(REQUEST))
                .succeedsWithin(5, SECONDS)
                .satisfies(response -> assertThat(response.scores()).containsExactly(0.5, 0.5));
        assertThat(scoredOn).isNotEmpty().allSatisfy(thread -> assertThat(thread).isNotSameAs(caller));
        assertThat(model.requests()).hasSize(1);
    }

    @Test
    void should_fail_the_future_of_an_asynchronous_call_with_the_given_exception() {

        RuntimeException failure = new IllegalStateException("down");

        assertThat(MockScoringModel.thatFails(failure).scoreAsync(REQUEST))
                .failsWithin(5, SECONDS)
                .withThrowableThat()
                .havingCause()
                .isSameAs(failure);
    }

    @Test
    void should_throw_the_given_exception_for_every_request_when_repeating() {

        RuntimeException failure = new IllegalStateException("down");
        MockScoringModel model = MockScoringModel.thatFails(failure).thenRepeats();

        assertThatThrownBy(() -> model.score(REQUEST)).isSameAs(failure);
        assertThatThrownBy(() -> model.score(REQUEST)).isSameAs(failure);
        assertThatThrownBy(() -> model.scoreAll(List.of(), "q")).isSameAs(failure);
    }

    @Test
    void should_give_every_document_the_given_score() {

        MockScoringModel model = MockScoringModel.thatScores(0.7).thenRepeats();

        assertThat(model.score(REQUEST).scores()).containsExactly(0.7, 0.7);
        assertThat(model.scoreAll(List.of(TextSegment.from("a"), TextSegment.from("b")), "q").content())
                .containsExactly(0.7, 0.7);
        assertThat(model.requests()).hasSize(2);
        assertThat(model.requests().get(1).documents()).containsExactly("a", "b");
    }

    @Test
    void should_not_accept_null_arguments() {

        assertThatThrownBy(() -> MockScoringModel.thatScores((ToDoubleFunction<String>) null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MockScoringModel.thatFails(null)).isInstanceOf(IllegalArgumentException.class);
        MockScoringModel model = MockScoringModel.thatScores(0.5);
        assertThatThrownBy(() -> model.thenScores((ToDoubleFunction<String>) null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> model.thenFails(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_answer_one_request_per_step_and_fail_a_request_after_the_last_one() {

        MockScoringModel model = MockScoringModel.thatScores(0.1)
                .thenScores(0.2)
                .thenScores(document -> (double) document.length());

        assertThat(model.score(REQUEST).scores()).containsExactly(0.1, 0.1);
        assertThat(model.score(REQUEST).scores()).containsExactly(0.2, 0.2);
        assertThat(model.score(REQUEST).scores()).containsExactly(1.0, 2.0);
        assertThatThrownBy(() -> model.score(REQUEST))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MockScoringModel received request 4, but its script has only 3 steps")
                .hasMessageContaining("thenRepeats()")
                .hasMessageContaining("The query of request 4: \"q\", with 2 documents.");
        assertThat(model.requests()).hasSize(4);
    }

    @Test
    void should_fail_the_future_of_an_asynchronous_request_after_the_last_step() {

        MockScoringModel model = MockScoringModel.thatScores(0.5);

        assertThat(model.scoreAsync(REQUEST)).succeedsWithin(5, SECONDS);
        assertThat(model.scoreAsync(REQUEST))
                .failsWithin(5, SECONDS)
                .withThrowableThat()
                .havingCause()
                .isInstanceOf(IllegalStateException.class)
                .withMessageContaining("received request 2");
    }

    @Test
    void should_let_a_request_fail_and_the_next_one_succeed() {

        RuntimeException timeout = new TimeoutException("Read timed out");
        MockScoringModel model = MockScoringModel.thatFails(timeout)
                .thenScores(document -> document.contains("b") ? 0.9 : 0.1)
                .thenRepeats();

        assertThatThrownBy(() -> model.score(REQUEST)).isSameAs(timeout);
        assertThat(model.score(REQUEST).scores()).containsExactly(0.1, 0.9);
        assertThat(model.scoreAll(List.of(TextSegment.from("b")), "q").content()).containsExactly(0.9);
    }

    @Test
    void should_fail_a_later_step_with_the_given_exception() {

        RuntimeException failure = new IllegalStateException("down");
        MockScoringModel model = MockScoringModel.thatScores(0.5).thenFails(failure);

        assertThat(model.score(REQUEST).scores()).containsExactly(0.5, 0.5);
        assertThatThrownBy(() -> model.scoreAll(List.of(), "q")).isSameAs(failure);
    }

    @Test
    void should_repeat_only_the_last_step_and_compute_it_again_for_each_request() {

        AtomicInteger computed = new AtomicInteger();
        MockScoringModel model = MockScoringModel.thatScores(0.5)
                .thenScores(document -> computed.incrementAndGet())
                .thenRepeats();

        assertThat(model.score(REQUEST).scores()).containsExactly(0.5, 0.5);
        assertThat(model.score(REQUEST).scores()).containsExactly(1.0, 2.0);
        assertThat(model.score(REQUEST).scores()).containsExactly(3.0, 4.0);
    }

    @Test
    void should_not_accept_a_step_after_repeating() {

        MockScoringModel model = MockScoringModel.thatScores(0.5).thenRepeats();

        assertThatThrownBy(() -> model.thenScores(0.7))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("thenRepeats() must be the last call");
        assertThatThrownBy(model::thenRepeats).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void should_take_the_steps_in_the_order_of_the_async_calls() {

        CountDownLatch release = new CountDownLatch(1);
        MockScoringModel model = MockScoringModel.thatScores(document -> {
                    try {
                        release.await(5, SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return 0.9;
                })
                .thenScores(0.1);

        CompletableFuture<ScoringResponse> first = model.scoreAsync(REQUEST);
        CompletableFuture<ScoringResponse> second = model.scoreAsync(REQUEST);

        assertThat(model.requests()).hasSize(2);
        assertThat(second)
                .succeedsWithin(5, SECONDS)
                .satisfies(response -> assertThat(response.scores()).containsExactly(0.1, 0.1));
        release.countDown();
        assertThat(first)
                .succeedsWithin(5, SECONDS)
                .satisfies(response -> assertThat(response.scores()).containsExactly(0.9, 0.9));
    }

    @Test
    void should_not_record_a_null_request() {

        MockScoringModel model = MockScoringModel.thatScores(0.5);

        assertThatThrownBy(() -> model.doScore(null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(model.doScoreAsync(null))
                .failsWithin(5, SECONDS)
                .withThrowableThat()
                .havingCause()
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(model.requests()).isEmpty();
        assertThat(model.score(REQUEST).scores()).containsExactly(0.5, 0.5);
    }
}
