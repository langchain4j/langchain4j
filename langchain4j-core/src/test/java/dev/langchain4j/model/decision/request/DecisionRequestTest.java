package dev.langchain4j.model.decision.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class DecisionRequestTest {

    private static final YesNoQuestion QUESTION =
            YesNoQuestion.builder().instructions("Is this spam?").build();

    @ParameterizedTest
    @MethodSource("validStates")
    void should_accept_text_map_and_list_state(Object state) {

        DecisionRequest request =
                DecisionRequest.builder().state(state).question("spam", QUESTION).build();

        assertThat(request.state()).isEqualTo(state);
    }

    static List<Object> validStates() {
        return List.of("Buy now!", Map.of("subject", "Buy now!"), List.of("Buy now!", "Limited offer"));
    }

    @ParameterizedTest
    @MethodSource("invalidStates")
    void should_reject_invalid_state(Object state) {

        assertThatThrownBy(() -> DecisionRequest.builder()
                        .state(state)
                        .question("spam", QUESTION)
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("state");
    }

    static List<Object> invalidStates() {
        return List.of(" ", Map.of(), List.of(), 42);
    }

    @Test
    void should_reject_missing_state() {

        assertThatThrownBy(() -> DecisionRequest.builder().question("spam", QUESTION).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("state");
    }

    @Test
    void should_require_at_least_one_question() {

        assertThatThrownBy(() -> DecisionRequest.builder().state("Buy now!").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("questions");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    void should_reject_blank_question_name(String name) {

        assertThatThrownBy(() -> DecisionRequest.builder().question(name, QUESTION))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("question name");
    }

    @Test
    void should_keep_question_order() {

        DecisionRequest request = DecisionRequest.builder()
                .state("Buy now!")
                .question("c", QUESTION)
                .question("a", QUESTION)
                .question("b", QUESTION)
                .build();

        assertThat(request.questions()).containsOnlyKeys("c", "a", "b");
        assertThat(new ArrayList<>(request.questions().keySet())).containsExactly("c", "a", "b");
    }

    @Test
    void should_copy_state_and_questions() {

        Map<String, Object> state = new HashMap<>(Map.of("subject", "Buy now!"));
        Map<String, Question> questions = new LinkedHashMap<>(Map.of("spam", QUESTION));

        DecisionRequest request =
                DecisionRequest.builder().state(state).questions(questions).build();
        state.put("body", "Limited offer");
        questions.put("phishing", QUESTION);

        assertThat(request.state()).isEqualTo(Map.of("subject", "Buy now!"));
        assertThat(request.questions()).containsOnlyKeys("spam");
        assertThatThrownBy(() -> request.questions().put("phishing", QUESTION))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void should_default_to_empty_parameters() {

        DecisionRequest request =
                DecisionRequest.builder().state("Buy now!").question("spam", QUESTION).build();

        assertThat(request.parameters()).isEqualTo(DecisionRequestParameters.EMPTY);
        assertThat(request.modelName()).isNull();
    }

    @Test
    void should_expose_model_name_from_parameters() {

        DecisionRequest request = DecisionRequest.builder()
                .state("Buy now!")
                .question("spam", QUESTION)
                .parameters(DecisionRequestParameters.builder().modelName("model").build())
                .build();

        assertThat(request.modelName()).isEqualTo("model");
    }

    @Test
    void should_accept_custom_question_types() {

        record RankQuestion(String instructions, List<String> candidates) implements Question {}

        DecisionRequest request = DecisionRequest.builder()
                .state("Refactor the parser")
                .question("next_step", new RankQuestion("Which step is best?", List.of("test", "edit")))
                .build();

        assertThat(request.questions().get("next_step")).isInstanceOf(RankQuestion.class);
    }

    @Test
    void parameters_should_override() {

        DecisionRequestParameters defaults =
                DecisionRequestParameters.builder().modelName("default").build();

        assertThat(defaults.overrideWith(null)).isEqualTo(defaults);
        assertThat(defaults.overrideWith(DecisionRequestParameters.EMPTY).modelName())
                .isEqualTo("default");
        assertThat(defaults.overrideWith(DecisionRequestParameters.builder()
                                .modelName("override")
                                .build())
                        .modelName())
                .isEqualTo("override");
        assertThat(defaults.modelName()).isEqualTo("default");
    }
}
