package dev.langchain4j.test.mock;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.exception.InvalidDecisionResponseException;
import dev.langchain4j.exception.RateLimitException;
import dev.langchain4j.model.decision.listener.DecisionModelListener;
import dev.langchain4j.model.decision.request.ChoiceQuestion;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.DecisionRequestParameters;
import dev.langchain4j.model.decision.request.YesNoQuestion;
import dev.langchain4j.model.decision.response.DecisionResponse;
import dev.langchain4j.model.decision.response.YesNoAnswer;
import dev.langchain4j.model.output.TokenUsage;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class MockDecisionModelTest {

    static final DecisionRequest REQUEST = DecisionRequest.builder()
            .input("Buy now!")
            .question("spam", YesNoQuestion.from("Is this spam?"))
            .build();

    @Test
    void should_report_the_model_name_of_the_request() {

        MockDecisionModel model =
                MockDecisionModel.thatAnswers(Map.of("spam", YesNoAnswer.from(0.9))).thenRepeats();
        DecisionRequest request = DecisionRequest.builder()
                .input("Buy now!")
                .question("spam", YesNoQuestion.from("Is this spam?"))
                .parameters(DecisionRequestParameters.builder().modelName("decider").build())
                .build();

        assertThat(model.decide(request).modelName()).isEqualTo("decider");
        assertThat(model.decide(REQUEST).modelName()).isNull();
    }

    @Test
    void should_take_the_listeners_as_a_list() {

        DecisionModelListener listener = new DecisionModelListener() {};
        List<DecisionModelListener> listeners = new ArrayList<>(List.of(listener));
        MockDecisionModel model =
                MockDecisionModel.thatAnswers(Map.of("spam", YesNoAnswer.from(0.9))).withListeners(listeners);

        listeners.clear();

        assertThat(model.listeners()).containsExactly(listener);
    }

    @Test
    void should_answer_and_record_requests() {

        // given
        MockDecisionModel model = MockDecisionModel.thatAnswers(Map.of("spam", YesNoAnswer.from(0.9)));

        // when
        DecisionResponse response = model.decide(REQUEST);

        // then
        assertThat(response.yesNo("spam").probability()).isEqualTo(0.9);
        assertThat(response.tokenUsage()).isNull();
        assertThat(model.request()).isEqualTo(REQUEST);
    }

    @Test
    void should_return_the_last_request() {

        MockDecisionModel model =
                MockDecisionModel.thatAnswers(Map.of("spam", YesNoAnswer.from(0.9))).thenRepeats();
        DecisionRequest second = DecisionRequest.builder()
                .input("Hello")
                .question("spam", REQUEST.questions().get("spam"))
                .build();

        assertThatThrownBy(model::lastRequest)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Expected at least 1 decision request, but received none");
        model.decide(REQUEST);
        model.decide(second);

        assertThat(model.lastRequest()).isSameAs(model.requests().get(1));
    }

    @Test
    void should_answer_asynchronously_on_another_thread() {

        // given
        Thread caller = Thread.currentThread();
        List<Thread> answeredOn = new CopyOnWriteArrayList<>();
        MockDecisionModel model = MockDecisionModel.thatAnswers(request -> {
            answeredOn.add(Thread.currentThread());
            return Map.of("spam", YesNoAnswer.from(0.9));
        });

        // when
        CompletableFuture<DecisionResponse> future = model.decideAsync(REQUEST);

        // then
        assertThat(future).succeedsWithin(5, SECONDS);
        assertThat(future.join().yesNo("spam").probability()).isEqualTo(0.9);
        assertThat(answeredOn).singleElement().isNotSameAs(caller);
        assertThat(model.requests()).hasSize(1);
    }

    @Test
    void should_answer_yes_no_questions_and_reject_other_question_types() {

        // given
        MockDecisionModel model =
                MockDecisionModel.thatAnswersYesNoQuestions(question -> 0.3).thenRepeats();
        DecisionRequest withChoiceQuestion = DecisionRequest.builder()
                .input("Buy now!")
                .question("team", ChoiceQuestion.from("Which team?", Map.of("sales", "Sales", "support", "Support")))
                .build();

        // when-then
        assertThat(model.decide(REQUEST).yesNo("spam").probability()).isEqualTo(0.3);
        assertThatThrownBy(() -> model.decide(withChoiceQuestion))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Expected a yes/no question")
                .hasMessageContaining("thatAnswersQuestions(...)");
    }

    @Test
    void should_fail_every_sync_and_async_call_when_the_failure_repeats() {

        // given
        RuntimeException failure = new RuntimeException("boom");
        MockDecisionModel model = MockDecisionModel.thatFails(failure).thenRepeats();

        // when-then
        assertThatThrownBy(() -> model.decide(REQUEST)).isSameAs(failure);
        assertThatThrownBy(() -> model.decide(REQUEST)).isSameAs(failure);
        assertThat(model.decideAsync(REQUEST))
                .failsWithin(Duration.ofSeconds(5))
                .withThrowableOfType(ExecutionException.class)
                .havingCause()
                .isSameAs(failure);
    }

    @Test
    void should_not_change_its_answers_when_the_given_map_changes() {

        Map<String, YesNoAnswer> answers = new HashMap<>(Map.of("spam", YesNoAnswer.from(0.9)));
        MockDecisionModel model = MockDecisionModel.thatAnswers(answers);

        answers.put("spam", YesNoAnswer.from(0.1));

        assertThat(model.decide(REQUEST).yesNo("spam").probability()).isEqualTo(0.9);
    }

    @Test
    void should_accept_a_function_typed_with_a_specific_answer_type() {

        Function<DecisionRequest, Map<String, YesNoAnswer>> answers = request -> Map.of("spam", YesNoAnswer.from(0.9));

        assertThat(MockDecisionModel.thatAnswers(answers).decide(REQUEST).yesNo("spam").probability())
                .isEqualTo(0.9);
    }

    @Test
    void should_not_accept_null_arguments() {

        assertThatThrownBy(() -> MockDecisionModel.thatAnswers((Map<String, YesNoAnswer>) null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MockDecisionModel.thatAnswers(
                        (Function<DecisionRequest, Map<String, YesNoAnswer>>) null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MockDecisionModel.thatAnswersQuestions(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MockDecisionModel.thatAnswersYesNoQuestions(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MockDecisionModel.thatFails(null)).isInstanceOf(IllegalArgumentException.class);

        MockDecisionModel model = MockDecisionModel.thatAnswersYesNoQuestions(question -> 0.9);
        assertThatThrownBy(() -> model.thenAnswers((Map<String, YesNoAnswer>) null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> model.thenAnswers((Function<DecisionRequest, Map<String, YesNoAnswer>>) null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> model.thenAnswersQuestions(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> model.thenAnswersYesNoQuestions(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MockDecisionModel.thatResponds(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> model.thenResponds(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> model.thenFails(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_answer_one_request_per_step_in_order() {

        // given
        MockDecisionModel model = MockDecisionModel.thatAnswers(Map.of("spam", YesNoAnswer.from(0.9)))
                .thenAnswers(request -> Map.of("spam", YesNoAnswer.from(0.8)))
                .thenAnswersQuestions(question -> YesNoAnswer.from(0.7))
                .thenAnswersYesNoQuestions(question -> 0.6)
                .thenAnswers(Map.of("spam", YesNoAnswer.from(0.5)));

        // when-then
        assertThat(model.decide(REQUEST).yesNo("spam").probability()).isEqualTo(0.9);
        assertThat(model.decide(REQUEST).yesNo("spam").probability()).isEqualTo(0.8);
        assertThat(model.decide(REQUEST).yesNo("spam").probability()).isEqualTo(0.7);
        assertThat(model.decide(REQUEST).yesNo("spam").probability()).isEqualTo(0.6);
        assertThat(model.decide(REQUEST).yesNo("spam").probability()).isEqualTo(0.5);
        assertThat(model.requests()).hasSize(5);
    }

    @Test
    void should_give_different_answers_to_the_same_question_in_a_loop() {

        // the same code as in the Javadoc of MockDecisionModel
        MockDecisionModel model = MockDecisionModel.thatAnswersYesNoQuestions(question -> 0.1)
                .thenAnswersYesNoQuestions(question -> 0.1)
                .thenAnswersYesNoQuestions(question -> 0.9);

        int rounds = 0;
        boolean done = false;
        while (!done) {
            rounds++;
            done = model.decide(REQUEST).yesNo("spam").isYes(0.5);
        }

        assertThat(rounds).isEqualTo(3);
    }

    @Test
    void should_fail_the_first_request_and_answer_the_next_one() {

        // the same code as in the Javadoc of MockDecisionModel
        MockDecisionModel model = MockDecisionModel.thatFails(new RateLimitException("Too many requests"))
                .thenAnswersYesNoQuestions(question -> 0.9);

        assertThatThrownBy(() -> model.decide(REQUEST))
                .isInstanceOf(RateLimitException.class)
                .hasMessage("Too many requests");
        assertThat(model.decide(REQUEST).yesNo("spam").probability()).isEqualTo(0.9);
    }

    @Test
    void should_fail_a_later_step_with_exactly_the_given_exception() {

        RuntimeException failure = new RuntimeException("boom");
        MockDecisionModel model =
                MockDecisionModel.thatAnswers(Map.of("spam", YesNoAnswer.from(0.9))).thenFails(failure);

        assertThat(model.decide(REQUEST).yesNo("spam").probability()).isEqualTo(0.9);
        assertThatThrownBy(() -> model.decide(REQUEST)).isSameAs(failure);
    }

    @Test
    void should_fail_a_request_after_the_last_step_and_show_its_questions() {

        // given
        MockDecisionModel model = MockDecisionModel.thatAnswers(Map.of("spam", YesNoAnswer.from(0.9)));
        model.decide(REQUEST);

        // when-then
        assertThatThrownBy(() -> model.decide(REQUEST))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MockDecisionModel received request 2, but its script has only 1 step.")
                .hasMessageContaining("thenRepeats()")
                .hasMessageContaining("spam \"Is this spam?\"");
        assertThat(model.requests()).hasSize(2);
    }

    @Test
    void should_fail_the_future_of_an_async_request_after_the_last_step() {

        // given
        MockDecisionModel model = MockDecisionModel.thatAnswers(Map.of("spam", YesNoAnswer.from(0.9)));
        assertThat(model.decideAsync(REQUEST)).succeedsWithin(5, SECONDS);

        // when
        CompletableFuture<DecisionResponse> future = model.decideAsync(REQUEST);

        // then
        assertThat(future)
                .failsWithin(Duration.ofSeconds(5))
                .withThrowableOfType(ExecutionException.class)
                .havingCause()
                .isInstanceOf(IllegalStateException.class)
                .withMessageContaining("received request 2, but its script has only 1 step");
    }

    @Test
    void should_repeat_only_the_last_step() {

        MockDecisionModel model = MockDecisionModel.thatAnswers(Map.of("spam", YesNoAnswer.from(0.9)))
                .thenAnswers(Map.of("spam", YesNoAnswer.from(0.1)))
                .thenRepeats();

        assertThat(model.decide(REQUEST).yesNo("spam").probability()).isEqualTo(0.9);
        assertThat(model.decide(REQUEST).yesNo("spam").probability()).isEqualTo(0.1);
        assertThat(model.decide(REQUEST).yesNo("spam").probability()).isEqualTo(0.1);
        assertThat(model.decideAsync(REQUEST))
                .succeedsWithin(5, SECONDS)
                .satisfies(response -> assertThat(response.yesNo("spam").probability()).isEqualTo(0.1));
    }

    @Test
    void should_compute_a_repeated_step_again_for_each_request() {

        // given
        AtomicInteger computed = new AtomicInteger();
        MockDecisionModel model = MockDecisionModel.thatAnswersQuestions(
                        question -> YesNoAnswer.from(computed.incrementAndGet() / 10.0))
                .thenRepeats();

        // when-then
        assertThat(model.decide(REQUEST).yesNo("spam").probability()).isEqualTo(0.1);
        assertThat(model.decide(REQUEST).yesNo("spam").probability()).isEqualTo(0.2);
        assertThat(model.decideAsync(REQUEST))
                .succeedsWithin(5, SECONDS)
                .satisfies(response -> assertThat(response.yesNo("spam").probability()).isEqualTo(0.3));
    }

    @Test
    void should_take_the_steps_in_the_order_of_the_async_calls() {

        // given
        CompletableFuture<Void> release = new CompletableFuture<>();
        MockDecisionModel model = MockDecisionModel.thatAnswers(request -> {
                    release.join();
                    return Map.of("spam", YesNoAnswer.from(0.9));
                })
                .thenAnswers(Map.of("spam", YesNoAnswer.from(0.1)));

        // when
        CompletableFuture<DecisionResponse> first = model.decideAsync(REQUEST);
        CompletableFuture<DecisionResponse> second = model.decideAsync(REQUEST);

        // then
        assertThat(model.requests()).hasSize(2);
        assertThat(second)
                .succeedsWithin(5, SECONDS)
                .satisfies(response -> assertThat(response.yesNo("spam").probability()).isEqualTo(0.1));
        release.complete(null);
        assertThat(first)
                .succeedsWithin(5, SECONDS)
                .satisfies(response -> assertThat(response.yesNo("spam").probability()).isEqualTo(0.9));
    }

    @Test
    void should_reject_invalid_answers_when_the_step_is_created() {

        MockDecisionModel model = MockDecisionModel.thatAnswersYesNoQuestions(question -> 0.9);
        Map<String, YesNoAnswer> withNullAnswer = new HashMap<>();
        withNullAnswer.put("spam", null);

        assertThatThrownBy(() -> MockDecisionModel.thatAnswers(Map.<String, YesNoAnswer>of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("answers");
        assertThatThrownBy(() -> model.thenAnswers(Map.of(" ", YesNoAnswer.from(0.9))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> model.thenAnswers(withNullAnswer)).isInstanceOf(IllegalArgumentException.class);
        assertThat(model.decide(REQUEST).yesNo("spam").probability()).isEqualTo(0.9);
    }

    @Test
    void should_fail_a_null_request_without_recording_it_and_without_taking_a_step() {

        MockDecisionModel model = MockDecisionModel.thatAnswersYesNoQuestions(question -> 0.9);

        assertThatThrownBy(() -> model.doDecide(null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(model.doDecideAsync(null))
                .failsWithin(5, SECONDS)
                .withThrowableOfType(ExecutionException.class)
                .withCauseInstanceOf(IllegalArgumentException.class);
        assertThat(model.requests()).isEmpty();
        assertThat(model.decide(REQUEST).yesNo("spam").probability()).isEqualTo(0.9);
    }

    @Test
    void should_not_accept_steps_after_thenRepeats() {

        MockDecisionModel model =
                MockDecisionModel.thatAnswers(Map.of("spam", YesNoAnswer.from(0.9))).thenRepeats();

        assertThatThrownBy(() -> model.thenAnswers(Map.of("spam", YesNoAnswer.from(0.1))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("thenRepeats() must be the last call of the script");
        assertThatThrownBy(() -> model.thenFails(new RuntimeException("boom")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(model::thenRepeats).isInstanceOf(IllegalStateException.class);
        assertThat(model.decide(REQUEST).yesNo("spam").probability()).isEqualTo(0.9);
    }

    @Test
    void should_answer_a_function_step_with_the_exception_it_throws() {

        RuntimeException failure = new IllegalArgumentException("unexpected question");
        MockDecisionModel model = MockDecisionModel.thatAnswersQuestions(question -> {
            throw failure;
        });

        assertThatThrownBy(() -> model.decide(REQUEST)).isSameAs(failure);
    }

    @Test
    void request_should_fail_unless_exactly_one_request_was_received() {

        MockDecisionModel model = MockDecisionModel.thatAnswers(Map.of("spam", YesNoAnswer.from(0.9)));

        assertThatThrownBy(model::request)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Expected exactly 1 decision request, but received 0");
    }

    @Test
    void should_answer_with_exactly_the_given_response_including_its_metadata() {

        DecisionResponse given = DecisionResponse.builder()
                .answer("spam", YesNoAnswer.from(0.9))
                .modelName("decision-model")
                .tokenUsage(new TokenUsage(20, 5))
                .build();
        MockDecisionModel model = MockDecisionModel.thatResponds(given).thenRepeats();

        DecisionResponse response = model.decide(REQUEST);

        assertThat(response.answers()).isEqualTo(given.answers());
        assertThat(response.modelName()).isEqualTo("decision-model");
        assertThat(response.tokenUsage()).isEqualTo(new TokenUsage(20, 5));
        assertThat(model.decideAsync(REQUEST))
                .succeedsWithin(5, SECONDS)
                .satisfies(async -> assertThat(async.tokenUsage()).isEqualTo(new TokenUsage(20, 5)));
    }

    @Test
    void should_still_check_the_answers_of_the_given_response() {

        DecisionResponse given = DecisionResponse.builder()
                .answer("other", YesNoAnswer.from(0.9))
                .build();
        MockDecisionModel model = MockDecisionModel.thatResponds(given);

        assertThatThrownBy(() -> model.decide(REQUEST)).isInstanceOf(InvalidDecisionResponseException.class);
    }

    @Test
    void should_give_the_whole_response_in_a_later_step() {

        DecisionResponse given = DecisionResponse.builder()
                .answer("spam", YesNoAnswer.from(0.1))
                .tokenUsage(new TokenUsage(7))
                .build();
        MockDecisionModel model = MockDecisionModel.thatAnswersYesNoQuestions(question -> 0.9).thenResponds(given);

        assertThat(model.decide(REQUEST).yesNo("spam").probability()).isEqualTo(0.9);
        assertThat(model.decide(REQUEST).tokenUsage()).isEqualTo(new TokenUsage(7));
        assertThatThrownBy(() -> model.decide(REQUEST)).isInstanceOf(IllegalStateException.class);
    }
}
