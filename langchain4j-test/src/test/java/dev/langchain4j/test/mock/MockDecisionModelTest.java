package dev.langchain4j.test.mock;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.model.decision.listener.DecisionModelListener;
import dev.langchain4j.model.decision.request.ChoiceQuestion;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.DecisionRequestParameters;
import dev.langchain4j.model.decision.request.YesNoQuestion;
import dev.langchain4j.model.decision.response.DecisionResponse;
import dev.langchain4j.model.decision.response.YesNoAnswer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class MockDecisionModelTest {

    static final DecisionRequest REQUEST = DecisionRequest.builder()
            .input("Buy now!")
            .question("spam", YesNoQuestion.from("Is this spam?"))
            .build();

    @Test
    void should_report_the_model_name_of_the_request() {

        MockDecisionModel model = MockDecisionModel.thatAnswers(Map.of("spam", YesNoAnswer.from(0.9)));
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

        MockDecisionModel model = MockDecisionModel.thatAnswers(Map.of("spam", YesNoAnswer.from(0.9)));
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
        MockDecisionModel model = MockDecisionModel.thatAnswersYesNoQuestions(question -> 0.3);
        DecisionRequest withChoiceQuestion = DecisionRequest.builder()
                .input("Buy now!")
                .question("team", ChoiceQuestion.from("Which team?", Map.of("sales", "Sales", "support", "Support")))
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
        RuntimeException failure = new RuntimeException("boom");
        MockDecisionModel model = MockDecisionModel.thatFails(failure);

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
    }

    @Test
    void request_should_fail_unless_exactly_one_request_was_received() {

        MockDecisionModel model = MockDecisionModel.thatAnswers(Map.of("spam", YesNoAnswer.from(0.9)));

        assertThatThrownBy(model::request)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Expected exactly 1 decision request, but received 0");
    }
}
