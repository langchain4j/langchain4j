package dev.langchain4j.agentic.patterns.debate;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConvergenceStrategyLocaleTest {

    private static final Locale DEFAULT_LOCALE = Locale.getDefault();

    @BeforeEach
    void setUp() {
        Locale.setDefault(Locale.forLanguageTag("tr"));
    }

    @AfterEach
    void tearDown() {
        Locale.setDefault(DEFAULT_LOCALE);
    }

    @Test
    void unanimousLastWord_converges_on_mixed_case_verdicts_under_turkish_locale() {
        assertThat(ConvergenceStrategy.unanimousLastWord()
                        .hasConverged(List.of("On balance, disagree", "Still DISAGREE", "I disagree.")))
                .isTrue();
    }

    @Test
    void unanimousLastWord_does_not_converge_on_different_verdicts_under_turkish_locale() {
        assertThat(ConvergenceStrategy.unanimousLastWord().hasConverged(List.of("I agree", "I disagree")))
                .isFalse();
    }
}
