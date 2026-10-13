package dev.langchain4j.model.decision.mock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.exception.AsyncNotSupportedException;
import dev.langchain4j.model.decision.request.ChoiceQuestion;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.YesNoQuestion;
import dev.langchain4j.model.decision.response.DecisionResponse;
import dev.langchain4j.model.decision.response.YesNoAnswer;
import dev.langchain4j.model.output.TokenUsage;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;

class DecisionModelMockTest {

    static final DecisionRequest REQUEST = DecisionRequest.builder()
            .input("Buy now!")
            .question("spam", YesNoQuestion.of("Is this spam?"))
            .build();

    @Test
    void should_answer_and_record_requests() {

        // given
        DecisionModelMock model = DecisionModelMock.thatAlwaysAnswers(Map.of("spam", YesNoAnswer.of(0.9)))
                .withTokenUsage(new TokenUsage(10, 1));

        // when
        DecisionResponse response = model.decide(REQUEST);

        // then
        assertThat(response.yesNo("spam").probability()).isEqualTo(0.9);
        assertThat(response.tokenUsage()).isEqualTo(new TokenUsage(10, 1));
        assertThat(model.request()).isEqualTo(REQUEST);
    }

    @Test
    void should_answer_asynchronously_with_a_completed_future() {

        // given
        DecisionModelMock model = DecisionModelMock.thatAlwaysAnswers(Map.of("spam", YesNoAnswer.of(0.9)));

        // when
        CompletableFuture<DecisionResponse> future = model.decideAsync(REQUEST);

        // then
        assertThat(future).isCompleted();
        assertThat(future.join().yesNo("spam").probability()).isEqualTo(0.9);
        assertThat(model.requests()).hasSize(1);
    }

    @Test
    void should_fail_async_calls_without_recording_them_when_async_is_not_supported() {

        // given
        DecisionModelMock model = DecisionModelMock.thatAlwaysAnswers(Map.of("spam", YesNoAnswer.of(0.9)))
                .withoutAsyncSupport();

        // when
        CompletableFuture<DecisionResponse> future = model.decideAsync(REQUEST);

        // then
        assertThat(future)
                .failsWithin(Duration.ZERO)
                .withThrowableOfType(ExecutionException.class)
                .withCauseInstanceOf(AsyncNotSupportedException.class);
        assertThat(model.requests()).isEmpty();
    }

    @Test
    void should_answer_yes_no_questions_and_reject_other_question_types() {

        // given
        DecisionModelMock model = DecisionModelMock.thatAnswersYesNoQuestions(question -> 0.3);
        DecisionRequest withChoiceQuestion = DecisionRequest.builder()
                .input("Buy now!")
                .question("team", ChoiceQuestion.of("Which team?", Map.of("sales", "Sales", "support", "Support")))
                .build();

        // when-then
        assertThat(model.decide(REQUEST).yesNo("spam").probability()).isEqualTo(0.3);
        assertThatThrownBy(() -> model.decide(withChoiceQuestion))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Expected a yes/no question");
    }

    @Test
    void should_fail_sync_and_async_calls_when_configured_to_throw() {

        // given
        DecisionModelMock model = DecisionModelMock.thatAlwaysThrowsExceptionWithMessage("boom");

        // when-then
        assertThatThrownBy(() -> model.decide(REQUEST)).hasMessage("boom");
        assertThat(model.decideAsync(REQUEST))
                .failsWithin(Duration.ZERO)
                .withThrowableOfType(ExecutionException.class)
                .withMessageContaining("boom");
    }

    @Test
    void request_should_fail_unless_exactly_one_request_was_received() {

        DecisionModelMock model = DecisionModelMock.thatAlwaysAnswers(Map.of("spam", YesNoAnswer.of(0.9)));

        assertThatThrownBy(model::request)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Expected exactly 1 request, but received 0");
    }
}
