package dev.langchain4j.model.decision.response;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.model.output.TokenUsage;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DecisionResponseTest {

    private static final YesNoAnswer YES_NO = YesNoAnswer.builder().probability(0.9).build();

    @Test
    void should_create_yes_no_answer() {

        assertThat(YesNoAnswer.builder().probability(0.0).build().probability()).isZero();
        assertThat(YesNoAnswer.builder().probability(1.0).build().probability()).isEqualTo(1.0);
    }

    @Test
    void yes_no_answer_should_be_created_with_of_and_compared_with_threshold() {

        YesNoAnswer answer = YesNoAnswer.of(0.7);

        assertThat(answer).isEqualTo(YesNoAnswer.builder().probability(0.7).build());
        assertThat(answer.isYes(0.7)).isTrue();
        assertThat(answer.isYes(0.8)).isFalse();
        assertThatThrownBy(() -> answer.isYes(1.5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("threshold");
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.1, 1.1, Double.NaN, Double.POSITIVE_INFINITY})
    void should_reject_invalid_yes_no_probability(double probability) {

        assertThatThrownBy(() -> YesNoAnswer.builder().probability(probability).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("probability");
    }

    @Test
    void should_reject_missing_yes_no_probability() {

        assertThatThrownBy(() -> YesNoAnswer.builder().build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("probability");
    }

    @Test
    void should_create_choice_answer() {

        ChoiceAnswer answer = ChoiceAnswer.builder()
                .value("billing")
                .probability("billing", 0.88)
                .probability("support", 0.12)
                .confidence(0.81)
                .build();

        assertThat(answer.value()).isEqualTo("billing");
        assertThat(answer.probabilities().keySet()).containsExactly("billing", "support");
        assertThat(answer.probabilities()).containsEntry("support", 0.12);
        assertThat(answer.confidence()).isEqualTo(0.81);
    }

    @Test
    void choice_answer_should_expose_probability_and_margin() {

        ChoiceAnswer answer = ChoiceAnswer.builder()
                .value("billing")
                .probability("billing", 0.55)
                .probability("support", 0.4)
                .build();

        assertThat(answer.probabilityOf("billing")).isEqualTo(0.55);
        assertThat(answer.probabilityOf("sales")).isZero();
        assertThat(answer.margin()).isCloseTo(0.15, org.assertj.core.data.Offset.offset(1e-9));
        assertThatThrownBy(() -> ChoiceAnswer.builder().value("billing").build().margin())
                .isInstanceOf(IllegalStateException.class);

        // probability that was not reported may belong to another option
        ChoiceAnswer partial = ChoiceAnswer.builder()
                .value("billing")
                .probability("billing", 0.55)
                .build();
        assertThat(partial.margin()).isCloseTo(0.10, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void choice_answer_probabilities_and_confidence_should_be_optional() {

        ChoiceAnswer answer = ChoiceAnswer.builder().value("billing").build();

        assertThat(answer.probabilities()).isEmpty();
        assertThat(answer.confidence()).isNull();
    }

    @Test
    void should_reject_invalid_choice_answer() {

        assertThatThrownBy(() -> ChoiceAnswer.builder().build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("value");

        assertThatThrownBy(() -> ChoiceAnswer.builder().probability("billing", 1.5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("probability");

        assertThatThrownBy(() -> ChoiceAnswer.builder()
                        .value("billing")
                        .confidence(Double.NaN)
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("confidence");
    }

    @Test
    void should_create_scale_answer() {

        ScaleAnswer answer = ScaleAnswer.builder()
                .mean(1.99)
                .probabilities(List.of(0.0, 0.01, 0.99))
                .confidence(0.99)
                .build();

        assertThat(answer.mean()).isEqualTo(1.99);
        assertThat(answer.probabilities()).containsExactly(0.0, 0.01, 0.99);
        assertThat(answer.confidence()).isEqualTo(0.99);
    }

    @Test
    void scale_answer_probabilities_and_confidence_should_be_optional() {

        ScaleAnswer answer = ScaleAnswer.builder().mean(0.5).build();

        assertThat(answer.probabilities()).isEmpty();
        assertThat(answer.confidence()).isNull();
    }

    @Test
    void should_reject_invalid_scale_answer() {

        assertThatThrownBy(() -> ScaleAnswer.builder().build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mean");

        assertThatThrownBy(() -> ScaleAnswer.builder().mean(Double.NaN).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mean");

        assertThatThrownBy(() -> ScaleAnswer.builder().probabilities(List.of(0.5, -0.5)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("probability");
    }

    @Test
    void should_create_response_with_answers_and_metadata() {

        TokenUsage tokenUsage = new TokenUsage(318, 34);

        DecisionResponse response = DecisionResponse.builder()
                .answer("urgent", YES_NO)
                .answer("team", ChoiceAnswer.builder().value("billing").build())
                .modelName("jev-1.13.0")
                .tokenUsage(tokenUsage)
                .build();

        assertThat(response.answers().keySet()).containsExactly("urgent", "team");
        assertThat(response.modelName()).isEqualTo("jev-1.13.0");
        assertThat(response.tokenUsage()).isEqualTo(tokenUsage);
        assertThat(response.metadata())
                .isEqualTo(DecisionResponseMetadata.builder()
                        .modelName("jev-1.13.0")
                        .tokenUsage(tokenUsage)
                        .build());
    }

    @Test
    void should_accept_metadata_instance() {

        DecisionResponseMetadata metadata =
                DecisionResponseMetadata.builder().modelName("jev-1.13.0").build();

        DecisionResponse response = DecisionResponse.builder()
                .answer("urgent", YES_NO)
                .metadata(metadata)
                .build();

        assertThat(response.metadata()).isSameAs(metadata);
        assertThat(response.modelName()).isEqualTo("jev-1.13.0");
    }

    @Test
    void should_reject_both_metadata_and_model_name() {

        assertThatThrownBy(() -> DecisionResponse.builder()
                        .answer("urgent", YES_NO)
                        .metadata(DecisionResponseMetadata.builder().build())
                        .modelName("jev-1.13.0")
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("modelName");
    }

    @Test
    void should_require_at_least_one_answer() {

        assertThatThrownBy(() -> DecisionResponse.builder().build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("answers");
    }

    @Test
    void answers_should_be_immutable_copies() {

        Map<String, DecisionAnswer> answers = new LinkedHashMap<>(Map.of("urgent", YES_NO));

        DecisionResponse response = DecisionResponse.builder().answers(answers).build();
        answers.put("spam", YES_NO);

        assertThat(response.answers()).containsOnlyKeys("urgent");
        assertThatThrownBy(() -> response.answers().put("spam", YES_NO))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void should_return_typed_answers() {

        ChoiceAnswer team = ChoiceAnswer.builder().value("billing").build();
        ScaleAnswer mood = ScaleAnswer.builder().mean(1.4).build();

        DecisionResponse response = DecisionResponse.builder()
                .answer("urgent", YES_NO)
                .answer("team", team)
                .answer("mood", mood)
                .build();

        assertThat(response.yesNo("urgent")).isSameAs(YES_NO);
        assertThat(response.choice("team")).isSameAs(team);
        assertThat(response.scale("mood")).isSameAs(mood);
        assertThat(response.answer("team", ChoiceAnswer.class)).isSameAs(team);
    }

    @Test
    void typed_accessors_should_fail_for_unknown_name() {

        DecisionResponse response =
                DecisionResponse.builder().answer("urgent", YES_NO).build();

        assertThatThrownBy(() -> response.yesNo("spam"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'spam'")
                .hasMessageContaining("[urgent]");
    }

    @Test
    void typed_accessors_should_fail_for_wrong_answer_type() {

        DecisionResponse response =
                DecisionResponse.builder().answer("urgent", YES_NO).build();

        assertThatThrownBy(() -> response.choice("urgent"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("YesNoAnswer")
                .hasMessageContaining("ChoiceAnswer");
    }

    @Test
    void should_accept_custom_answer_types() {

        record RankAnswer(List<String> ranked) implements DecisionAnswer {}

        DecisionResponse response = DecisionResponse.builder()
                .answer("next_step", new RankAnswer(List.of("test", "edit")))
                .build();

        assertThat(response.answers().get("next_step")).isInstanceOf(RankAnswer.class);
    }

    @Test
    void answers_should_implement_equals_and_hash_code() {

        assertThat(YesNoAnswer.builder().probability(0.9).build())
                .isEqualTo(YES_NO)
                .hasSameHashCodeAs(YES_NO)
                .isNotEqualTo(YesNoAnswer.builder().probability(0.8).build());

        assertThat(ChoiceAnswer.builder().value("a").confidence(0.5).build())
                .isEqualTo(ChoiceAnswer.builder().value("a").confidence(0.5).build())
                .isNotEqualTo(ChoiceAnswer.builder().value("a").build());

        assertThat(ScaleAnswer.builder().mean(1.0).build())
                .isEqualTo(ScaleAnswer.builder().mean(1.0).build())
                .isNotEqualTo(ScaleAnswer.builder().mean(2.0).build());
    }
}
