package dev.langchain4j.test.mock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.model.scoring.request.ScoringRequest;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScoringModelMockTest {

    private static final ScoringRequest REQUEST =
            ScoringRequest.builder().documents(List.of("a", "bb")).query("q").build();

    @Test
    void should_score_and_record_the_requests() {

        ScoringModelMock model = ScoringModelMock.thatScores(document -> (double) document.length());

        assertThat(model.score(REQUEST).scores()).containsExactly(1.0, 2.0);
        assertThat(model.request().query()).isEqualTo("q");

        model.score(REQUEST);

        assertThat(model.requests()).hasSize(2);
        assertThatThrownBy(model::request).hasMessage("Expected exactly 1 scoring request, but received 2");
    }

    @Test
    void should_record_asynchronous_requests() {

        ScoringModelMock model = ScoringModelMock.thatScores(0.5);

        assertThat(model.scoreAsync(REQUEST).join().scores()).containsExactly(0.5, 0.5);
        assertThat(model.requests()).hasSize(1);
    }

    @Test
    void should_always_throw_the_given_exception() {

        RuntimeException failure = new IllegalStateException("down");

        assertThatThrownBy(() -> ScoringModelMock.thatThrows(failure).score(REQUEST))
                .isSameAs(failure);
    }
}
