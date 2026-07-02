package dev.langchain4j.agentic.patterns.debate;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ConvergenceStrategyTest {

    @Test
    void unanimous_converges_when_all_equal() {
        assertThat(ConvergenceStrategy.unanimous().hasConverged(List.of("A", "A", "A")))
                .isTrue();
    }

    @Test
    void unanimous_does_not_converge_when_different() {
        assertThat(ConvergenceStrategy.unanimous().hasConverged(List.of("A", "B", "A")))
                .isFalse();
    }

    @Test
    void unanimous_converges_on_single_element() {
        assertThat(ConvergenceStrategy.unanimous().hasConverged(List.of("X"))).isTrue();
    }

    @Test
    void unanimous_converges_on_empty_list() {
        assertThat(ConvergenceStrategy.unanimous().hasConverged(List.of())).isTrue();
    }

    @Test
    void unanimousLastWord_converges_with_trailing_punctuation() {
        assertThat(ConvergenceStrategy.unanimousLastWord()
                        .hasConverged(List.of("I think AGREE.", "Definitely AGREE", "So AGREE!")))
                .isTrue();
    }

    @Test
    void unanimousLastWord_converges_across_multiline_verdicts() {
        assertThat(ConvergenceStrategy.unanimousLastWord()
                        .hasConverged(List.of("Reasoning here.\nAPPROVE", "Verdict: APPROVE.")))
                .isTrue();
    }

    @Test
    void unanimousLastWord_converges_on_single_word_verdicts() {
        assertThat(ConvergenceStrategy.unanimousLastWord().hasConverged(List.of("AGREE", "AGREE")))
                .isTrue();
    }

    @Test
    void unanimousLastWord_does_not_converge_on_conflicting_verdicts() {
        assertThat(ConvergenceStrategy.unanimousLastWord().hasConverged(List.of("I think AGREE", "Honestly DISAGREE")))
                .isFalse();
    }

    @Test
    void custom_strategy_via_lambda() {
        ConvergenceStrategy atLeastTwo =
                positions -> positions.stream().distinct().count() <= 2;

        assertThat(atLeastTwo.hasConverged(List.of("A", "B", "A"))).isTrue();
        assertThat(atLeastTwo.hasConverged(List.of("A", "B", "C"))).isFalse();
    }
}
