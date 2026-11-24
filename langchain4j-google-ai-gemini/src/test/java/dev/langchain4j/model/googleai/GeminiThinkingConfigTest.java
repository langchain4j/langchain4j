package dev.langchain4j.model.googleai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.googleai.GeminiThinkingConfig.GeminiThinkingLevel;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@Isolated // mutates the JVM-wide default locale
class GeminiThinkingConfigTest {

    private Locale defaultLocale;

    @BeforeEach
    void setTurkishLocale() {
        defaultLocale = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("tr-TR"));
    }

    @AfterEach
    void restoreDefaultLocale() {
        Locale.setDefault(defaultLocale);
    }

    @ParameterizedTest
    @CsvSource({"MINIMAL,minimal", "LOW,low", "MEDIUM,medium", "HIGH,high"})
    void should_map_thinking_level_independently_of_default_locale(
            GeminiThinkingLevel thinkingLevel, String expectedThinkingLevel) {

        GeminiThinkingConfig thinkingConfig =
                GeminiThinkingConfig.builder().thinkingLevel(thinkingLevel).build();

        assertThat(thinkingConfig.thinkingLevel()).isEqualTo(expectedThinkingLevel);
    }
}
