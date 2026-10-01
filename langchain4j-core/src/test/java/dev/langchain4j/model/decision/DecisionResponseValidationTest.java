package dev.langchain4j.model.decision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.exception.InvalidDecisionResponseException;
import dev.langchain4j.model.decision.listener.DecisionModelErrorContext;
import dev.langchain4j.model.decision.listener.DecisionModelListener;
import dev.langchain4j.model.decision.mock.DecisionModelMock;
import dev.langchain4j.model.decision.request.ChoiceQuestion;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.ScaleQuestion;
import dev.langchain4j.model.decision.request.YesNoQuestion;
import dev.langchain4j.model.decision.response.ChoiceAnswer;
import dev.langchain4j.model.decision.response.DecisionAnswer;
import dev.langchain4j.model.decision.response.ScaleAnswer;
import dev.langchain4j.model.decision.response.YesNoAnswer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class DecisionResponseValidationTest {

    static final DecisionRequest REQUEST = DecisionRequest.builder()
            .input("I was charged twice this month")
            .question("urgent", YesNoQuestion.of("Does this need attention today?"))
            .question("team", ChoiceQuestion.of("Which team?", Map.of("billing", "Payments", "support", "Bugs")))
            .question("frustration", ScaleQuestion.of("How frustrated?", List.of("Calm", "Frustrated", "Angry")))
            .build();

    static final YesNoAnswer URGENT = YesNoAnswer.of(0.9);
    static final ChoiceAnswer TEAM = ChoiceAnswer.builder().value("billing").build();
    static final ScaleAnswer FRUSTRATION = ScaleAnswer.builder().mean(1.2).build();

    @Test
    void should_accept_matching_response() {

        DecisionModelMock model = DecisionModelMock.thatAlwaysAnswers(
                Map.of("urgent", URGENT, "team", TEAM, "frustration", FRUSTRATION));

        assertThat(model.decide(REQUEST).choice("team").value()).isEqualTo("billing");
    }

    @Test
    void should_attach_offered_options_to_choice_answers_so_that_misspelled_options_are_rejected() {

        DecisionModelMock model = DecisionModelMock.thatAlwaysAnswers(Map.of(
                "urgent",
                URGENT,
                "team",
                ChoiceAnswer.builder().value("billing").probability("billing", 0.9).build(),
                "frustration",
                FRUSTRATION));

        ChoiceAnswer team = model.decide(REQUEST).choice("team");

        assertThat(team.options()).containsExactlyInAnyOrder("billing", "support");
        assertThat(team.probabilityOf("support")).isEqualTo(0.0);
        assertThatThrownBy(() -> team.probabilityOf("suport"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'suport' is not one of the options");
    }

    @ParameterizedTest
    @MethodSource("invalidAnswers")
    void should_reject_response_that_does_not_match_the_request(
            Map<String, DecisionAnswer> answers, String expectedMessage) {

        List<Throwable> errors = new ArrayList<>();
        DecisionModelMock model = new DecisionModelMock(request -> answers) {
            @Override
            public List<DecisionModelListener> listeners() {
                return List.of(new DecisionModelListener() {
                    @Override
                    public void onError(DecisionModelErrorContext context) {
                        errors.add(context.error());
                    }
                });
            }
        };

        assertThatThrownBy(() -> model.decide(REQUEST))
                .isInstanceOf(InvalidDecisionResponseException.class)
                .hasMessageContaining(expectedMessage);
        assertThat(model.decideAsync(REQUEST))
                .failsWithin(Duration.ZERO)
                .withThrowableOfType(ExecutionException.class)
                .withCauseInstanceOf(InvalidDecisionResponseException.class)
                .withMessageContaining(expectedMessage);
        assertThat(errors).hasSize(2).allMatch(InvalidDecisionResponseException.class::isInstance);
    }

    static List<Arguments> invalidAnswers() {
        return List.of(
                Arguments.of(Map.of("urgent", URGENT, "team", TEAM), "no answer to question 'frustration'"),
                Arguments.of(
                        Map.of("urgent", TEAM, "team", TEAM, "frustration", FRUSTRATION),
                        "The answer to question 'urgent' is a ChoiceAnswer, but a YesNoAnswer is expected"),
                Arguments.of(
                        Map.of(
                                "urgent",
                                URGENT,
                                "team",
                                ChoiceAnswer.builder().value("sales").build(),
                                "frustration",
                                FRUSTRATION),
                        "chose 'sales', which is not one of the options"),
                Arguments.of(
                        Map.of(
                                "urgent",
                                URGENT,
                                "team",
                                ChoiceAnswer.builder()
                                        .value("billing")
                                        .probability("billing", 0.9)
                                        .probability("sales", 0.1)
                                        .build(),
                                "frustration",
                                FRUSTRATION),
                        "has a probability for 'sales', which is not one of the options"),
                Arguments.of(
                        Map.of("urgent", URGENT, "team", TEAM, "frustration", ScaleAnswer.builder().mean(3.0).build()),
                        "has a mean of 3.0, but the levels are 0 to 2"),
                Arguments.of(
                        Map.of(
                                "urgent",
                                URGENT,
                                "team",
                                TEAM,
                                "frustration",
                                ScaleAnswer.builder().mean(0.5).probabilities(List.of(0.5, 0.5)).build()),
                        "has 2 level probabilities, but the question has 3 levels"));
    }
}
