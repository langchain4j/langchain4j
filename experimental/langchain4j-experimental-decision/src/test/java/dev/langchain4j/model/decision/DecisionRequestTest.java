package dev.langchain4j.model.decision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.message.ImageContent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DecisionRequestTest {

    @Test
    void accepts_text_state_and_preserves_multimodal_content() {
        ImageContent image = ImageContent.from("aGVsbG8=", "image/png");
        DecisionRequest request = DecisionRequest.builder()
                .state("Customer asks about this image")
                .content(image)
                .question(
                        "q",
                        NoulQuestion.builder().instructions("Is it relevant?").build())
                .build();

        assertThat(request.state()).isEqualTo("Customer asks about this image");
        assertThat(request.contents()).containsExactly(image);
        assertThatThrownBy(() -> request.contents().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void merges_additional_parameters_without_losing_known_fields() {
        Map<String, Object> extras = new LinkedHashMap<>();
        extras.put("temperature", 0.2);
        DecisionRequestParameters defaults = DecisionRequestParameters.builder()
                .modelName("jev-latest")
                .additionalProperties(extras)
                .build();
        DecisionRequestParameters overrides = DecisionRequestParameters.builder()
                .additionalProperty("temperature", 0.5)
                .additionalProperty("trace", true)
                .build();

        extras.clear();
        assertThat(defaults.overrideWith(overrides).modelName()).isEqualTo("jev-latest");
        assertThat(defaults.overrideWith(overrides).additionalProperties())
                .containsExactly(Map.entry("temperature", 0.5), Map.entry("trace", true));
        assertThatThrownBy(() -> defaults.additionalProperties().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

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

        DecisionRequest request = DecisionRequest.builder()
                .state(state)
                .question("refund", refund)
                .question("team", team)
                .question("urgency", urgency)
                .build();

        state.put("later", true);
        examples.add("Return my money");

        assertThat((Map<String, Object>) request.state()).containsExactly(Map.entry("message", "I was charged twice"));
        assertThat(request.questions())
                .containsExactly(Map.entry("refund", refund), Map.entry("team", team), Map.entry("urgency", urgency));
        assertThat(refund.criteria().examples()).containsExactly("Please refund me");
        assertThat(team.options()).containsOnlyKeys("billing", "support");
        assertThat(urgency.levels()).containsOnlyKeys("low", "high");
        assertThatThrownBy(() -> ((Map<String, Object>) request.state()).put("x", "y"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> request.questions().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void defaults_to_empty_request_parameters_and_supports_overrides() {
        DecisionRequest request = DecisionRequest.builder()
                .state(Map.of("message", "hello"))
                .question(
                        "greeting",
                        NoulQuestion.builder()
                                .instructions("Is this a greeting?")
                                .build())
                .build();
        DecisionRequestParameters configured =
                DecisionRequestParameters.builder().modelName("jev-latest").build();
        DecisionRequestParameters override =
                DecisionRequestParameters.builder().modelName("jev-preview").build();

        assertThat(request.parameters()).isEqualTo(DecisionRequestParameters.EMPTY);
        assertThat(request.modelName()).isNull();
        assertThat(configured.overrideWith(override).modelName()).isEqualTo("jev-preview");
        assertThat(configured.overrideWith(DecisionRequestParameters.EMPTY).modelName())
                .isEqualTo("jev-latest");
        assertThat(configured.overrideWith(null)).isSameAs(configured);

        DecisionRequestParameters configuredFromEmpty = DecisionRequestParameters.EMPTY.overrideWith(configured);
        assertThat(configuredFromEmpty).isEqualTo(configured).isNotSameAs(DecisionRequestParameters.EMPTY);
        assertThat(DecisionRequestParameters.EMPTY.modelName()).isNull();
    }

    @Test
    void rejects_missing_or_invalid_request_fields() {
        NoulQuestion question = NoulQuestion.builder().instructions("Question?").build();

        assertThatThrownBy(
                        () -> DecisionRequest.builder().question("q", question).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("state");
        assertThatThrownBy(() -> DecisionRequest.builder()
                        .state(Map.of())
                        .question("q", question)
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("state");
        assertThatThrownBy(() -> DecisionRequest.builder()
                        .state(Map.of("message", "hello"))
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("questions");
        assertThatThrownBy(() -> DecisionRequest.builder()
                        .state(Map.of("message", "hello"))
                        .question(" ", question))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DecisionRequest.builder()
                        .state(Map.of("message", "hello"))
                        .question("q", null))
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
