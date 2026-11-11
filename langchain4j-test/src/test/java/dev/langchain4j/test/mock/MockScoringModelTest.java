package dev.langchain4j.test.mock;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.model.scoring.listener.ScoringModelListener;
import dev.langchain4j.model.scoring.request.ScoringRequest;
import dev.langchain4j.data.segment.TextSegment;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
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

        MockScoringModel model = MockScoringModel.thatScores(document -> (double) document.length());

        assertThat(model.score(REQUEST).scores()).containsExactly(1.0, 2.0);
        assertThat(model.request().query()).isEqualTo("q");

        model.score(REQUEST);

        assertThat(model.requests()).hasSize(2);
        assertThatThrownBy(model::request).hasMessage("Expected exactly 1 scoring request, but received 2. To check each of them, use requests(), for example requests().get(0)");
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
    void should_always_throw_the_given_exception() {

        RuntimeException failure = new IllegalStateException("down");
        MockScoringModel model = MockScoringModel.thatFails(failure);

        assertThatThrownBy(() -> model.score(REQUEST)).isSameAs(failure);
        assertThatThrownBy(() -> model.score(REQUEST)).isSameAs(failure);
        assertThatThrownBy(() -> model.scoreAll(List.of(), "q")).isSameAs(failure);
    }

    @Test
    void should_give_every_document_the_given_score() {

        MockScoringModel model = MockScoringModel.thatScores(0.7);

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
    }
}
