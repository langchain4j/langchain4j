package dev.langchain4j.model.judge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JudgeRequestTest {

    @Test
    void builds_a_typed_batch_request_and_defensively_copies_collections() {
        List<String> examples = new ArrayList<>(List.of("Please refund me"));
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("message", "I was charged twice");

        NoulQuestion refund = NoulQuestion.builder()
                .instructions("Is the customer requesting a refund?")
                .criteria(NoulCriteria.builder()
                        .what("A direct request to return money")
                        .notFor("Questions about refund policy")
                        .examples(examples)
                        .build())
                .build();
        ChoiceQuestion team = ChoiceQuestion.builder()
                .instructions("Which team should handle this?")
                .option(
                        "billing",
                        OptionCriteria.builder().what("Charges and refunds").build())
                .option(
                        "support",
                        OptionCriteria.builder().what("Product problems").build())
                .build();
        ScoreQuestion urgency = ScoreQuestion.builder()
                .instructions("How urgent is this?")
                .level("low", OptionCriteria.builder().what("Can wait").build())
                .level(
                        "high",
                        OptionCriteria.builder().what("Needs attention now").build())
                .build();

        JudgeRequest request = JudgeRequest.builder()
                .state(state)
                .question("refund", refund)
                .question("team", team)
                .question("urgency", urgency)
                .build();

        state.put("later", true);
        examples.add("Return my money");

        assertThat(request.state()).containsExactly(Map.entry("message", "I was charged twice"));
        assertThat(request.questions())
                .containsExactly(Map.entry("refund", refund), Map.entry("team", team), Map.entry("urgency", urgency));
        assertThat(refund.criteria().examples()).containsExactly("Please refund me");
        assertThat(team.options()).containsOnlyKeys("billing", "support");
        assertThat(urgency.levels()).containsOnlyKeys("low", "high");
        assertThatThrownBy(() -> request.state().put("x", "y")).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> request.questions().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void defaults_to_empty_request_parameters_and_supports_overrides() {
        JudgeRequest request = JudgeRequest.builder()
                .state(Map.of("message", "hello"))
                .question(
                        "greeting",
                        NoulQuestion.builder()
                                .instructions("Is this a greeting?")
                                .build())
                .build();
        JudgeRequestParameters configured =
                JudgeRequestParameters.builder().modelName("jev-latest").build();
        JudgeRequestParameters override =
                JudgeRequestParameters.builder().modelName("jev-preview").build();

        assertThat(request.parameters()).isEqualTo(JudgeRequestParameters.EMPTY);
        assertThat(request.modelName()).isNull();
        assertThat(configured.overrideWith(override).modelName()).isEqualTo("jev-preview");
        assertThat(configured.overrideWith(JudgeRequestParameters.EMPTY).modelName())
                .isEqualTo("jev-latest");
        assertThat(configured.overrideWith(null)).isSameAs(configured);
    }

    @Test
    void rejects_missing_or_invalid_request_fields() {
        NoulQuestion question = NoulQuestion.builder().instructions("Question?").build();

        assertThatThrownBy(() -> JudgeRequest.builder().question("q", question).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("state");
        assertThatThrownBy(() -> JudgeRequest.builder().state(Map.of()).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("questions");
        assertThatThrownBy(() -> JudgeRequest.builder().state(Map.of()).question(" ", question))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> JudgeRequest.builder().state(Map.of()).question("q", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validates_choice_options_and_score_levels() {
        OptionCriteria criterion = OptionCriteria.builder().what("criterion").build();

        assertThatThrownBy(
                        () -> ChoiceQuestion.builder().instructions("Pick one").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("options");
        assertThatThrownBy(
                        () -> ChoiceQuestion.builder().instructions("Pick one").option(" ", criterion))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ScoreQuestion.builder()
                        .instructions("Rate it")
                        .level("only", criterion)
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 2");
    }

    @Test
    void criteria_require_content() {
        assertThatThrownBy(() -> NoulCriteria.builder().build()).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OptionCriteria.builder().build()).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> NoulCriteria.builder().what(" ").build()).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OptionCriteria.builder().notFor("").build())
                .isInstanceOf(IllegalArgumentException.class);
    }
}
