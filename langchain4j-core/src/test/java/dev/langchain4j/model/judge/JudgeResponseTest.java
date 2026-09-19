package dev.langchain4j.model.judge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JudgeResponseTest {

    @Test
    void maps_each_typed_answer_and_defensively_copies_the_answer_map() {
        JudgeAnswer noul = JudgeAnswer.builder().noul(0.83).build();
        JudgeAnswer choice = JudgeAnswer.builder().choice("billing").confidence(0.91).build();
        JudgeAnswer score = JudgeAnswer.builder().score(1.7).confidence(0.64).build();
        Map<String, JudgeAnswer> answers = new LinkedHashMap<>();
        answers.put("refund", noul);
        answers.put("team", choice);
        answers.put("urgency", score);

        JudgeResponse response = JudgeResponse.builder().answers(answers).build();
        answers.clear();

        assertThat(response.answers()).containsExactly(
                Map.entry("refund", noul), Map.entry("team", choice), Map.entry("urgency", score));
        assertThat(response.answers().get("refund").noul()).isEqualTo(0.83);
        assertThat(response.answers().get("refund").confidence()).isNull();
        assertThat(response.answers().get("team").choice()).isEqualTo("billing");
        assertThat(response.answers().get("urgency").score()).isEqualTo(1.7);
        assertThatThrownBy(() -> response.answers().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void answer_requires_exactly_one_typed_value() {
        assertThatThrownBy(() -> JudgeAnswer.builder().build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly one");
        assertThatThrownBy(() -> JudgeAnswer.builder().noul(0.4).choice("yes").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly one");
    }

    @Test
    void validates_probabilities_and_response_entries() {
        assertThatThrownBy(() -> JudgeAnswer.builder().noul(1.01).build())
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> JudgeAnswer.builder().noul(Double.NaN).build())
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> JudgeAnswer.builder().choice("x").confidence(-0.01).build())
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> JudgeAnswer.builder().choice("x").confidence(Double.NaN).build())
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> JudgeResponse.builder().answers(Map.of()).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("answers");
    }
}
