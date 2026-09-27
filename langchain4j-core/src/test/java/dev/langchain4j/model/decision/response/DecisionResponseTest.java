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

    private static final NoulAnswer NOUL = NoulAnswer.builder().probability(0.9).build();

    @Test
    void should_create_noul_answer() {

        assertThat(NoulAnswer.builder().probability(0.0).build().probability()).isZero();
        assertThat(NoulAnswer.builder().probability(1.0).build().probability()).isEqualTo(1.0);
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.1, 1.1, Double.NaN, Double.POSITIVE_INFINITY})
    void should_reject_invalid_noul_probability(double probability) {

        assertThatThrownBy(() -> NoulAnswer.builder().probability(probability).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("probability");
    }

    @Test
    void should_reject_missing_noul_probability() {

        assertThatThrownBy(() -> NoulAnswer.builder().build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("probability");
    }

    @Test
    void should_create_choice_answer() {

        ChoiceAnswer answer = ChoiceAnswer.builder()
                .choice("billing")
                .probability("billing", 0.88)
                .probability("support", 0.12)
                .confidence(0.81)
                .build();

        assertThat(answer.choice()).isEqualTo("billing");
        assertThat(answer.probabilities().keySet()).containsExactly("billing", "support");
        assertThat(answer.probabilities()).containsEntry("support", 0.12);
        assertThat(answer.confidence()).isEqualTo(0.81);
    }

    @Test
    void choice_answer_probabilities_and_confidence_should_be_optional() {

        ChoiceAnswer answer = ChoiceAnswer.builder().choice("billing").build();

        assertThat(answer.probabilities()).isEmpty();
        assertThat(answer.confidence()).isNull();
    }

    @Test
    void should_reject_invalid_choice_answer() {

        assertThatThrownBy(() -> ChoiceAnswer.builder().build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("choice");

        assertThatThrownBy(() -> ChoiceAnswer.builder().probability("billing", 1.5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("probability");

        assertThatThrownBy(() -> ChoiceAnswer.builder()
                        .choice("billing")
                        .confidence(Double.NaN)
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("confidence");
    }

    @Test
    void should_create_score_answer() {

        ScoreAnswer answer = ScoreAnswer.builder()
                .score(1.99)
                .probabilities(List.of(0.0, 0.01, 0.99))
                .confidence(0.99)
                .build();

        assertThat(answer.score()).isEqualTo(1.99);
        assertThat(answer.probabilities()).containsExactly(0.0, 0.01, 0.99);
        assertThat(answer.confidence()).isEqualTo(0.99);
    }

    @Test
    void score_answer_probabilities_and_confidence_should_be_optional() {

        ScoreAnswer answer = ScoreAnswer.builder().score(0.5).build();

        assertThat(answer.probabilities()).isEmpty();
        assertThat(answer.confidence()).isNull();
    }

    @Test
    void should_reject_invalid_score_answer() {

        assertThatThrownBy(() -> ScoreAnswer.builder().build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("score");

        assertThatThrownBy(() -> ScoreAnswer.builder().score(Double.NaN).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("score");

        assertThatThrownBy(() -> ScoreAnswer.builder().probabilities(List.of(0.5, -0.5)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("probability");
    }

    @Test
    void should_create_response_with_answers_and_metadata() {

        TokenUsage tokenUsage = new TokenUsage(318, 34);

        DecisionResponse response = DecisionResponse.builder()
                .answer("urgent", NOUL)
                .answer("team", ChoiceAnswer.builder().choice("billing").build())
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
                .answer("urgent", NOUL)
                .metadata(metadata)
                .build();

        assertThat(response.metadata()).isSameAs(metadata);
        assertThat(response.modelName()).isEqualTo("jev-1.13.0");
    }

    @Test
    void should_reject_both_metadata_and_model_name() {

        assertThatThrownBy(() -> DecisionResponse.builder()
                        .answer("urgent", NOUL)
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

        Map<String, DecisionAnswer> answers = new LinkedHashMap<>(Map.of("urgent", NOUL));

        DecisionResponse response = DecisionResponse.builder().answers(answers).build();
        answers.put("spam", NOUL);

        assertThat(response.answers()).containsOnlyKeys("urgent");
        assertThatThrownBy(() -> response.answers().put("spam", NOUL))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void should_return_typed_answers() {

        ChoiceAnswer team = ChoiceAnswer.builder().choice("billing").build();
        ScoreAnswer mood = ScoreAnswer.builder().score(1.4).build();

        DecisionResponse response = DecisionResponse.builder()
                .answer("urgent", NOUL)
                .answer("team", team)
                .answer("mood", mood)
                .build();

        assertThat(response.noul("urgent")).isSameAs(NOUL);
        assertThat(response.choice("team")).isSameAs(team);
        assertThat(response.score("mood")).isSameAs(mood);
        assertThat(response.answer("team", ChoiceAnswer.class)).isSameAs(team);
    }

    @Test
    void typed_accessors_should_fail_for_unknown_name() {

        DecisionResponse response =
                DecisionResponse.builder().answer("urgent", NOUL).build();

        assertThatThrownBy(() -> response.noul("spam"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'spam'")
                .hasMessageContaining("[urgent]");
    }

    @Test
    void typed_accessors_should_fail_for_wrong_answer_type() {

        DecisionResponse response =
                DecisionResponse.builder().answer("urgent", NOUL).build();

        assertThatThrownBy(() -> response.choice("urgent"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NoulAnswer")
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

        assertThat(NoulAnswer.builder().probability(0.9).build())
                .isEqualTo(NOUL)
                .hasSameHashCodeAs(NOUL)
                .isNotEqualTo(NoulAnswer.builder().probability(0.8).build());

        assertThat(ChoiceAnswer.builder().choice("a").confidence(0.5).build())
                .isEqualTo(ChoiceAnswer.builder().choice("a").confidence(0.5).build())
                .isNotEqualTo(ChoiceAnswer.builder().choice("a").build());

        assertThat(ScoreAnswer.builder().score(1.0).build())
                .isEqualTo(ScoreAnswer.builder().score(1.0).build())
                .isNotEqualTo(ScoreAnswer.builder().score(2.0).build());
    }
}
