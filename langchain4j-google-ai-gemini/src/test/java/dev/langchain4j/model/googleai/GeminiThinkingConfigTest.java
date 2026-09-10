package dev.langchain4j.model.googleai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.googleai.GeminiThinkingConfig.GeminiThinkingLevel;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@Isolated
@Execution(ExecutionMode.SAME_THREAD)
class GeminiThinkingConfigTest {

    @ParameterizedTest
    @CsvSource({
        "tr-TR, MINIMAL, minimal", "tr-TR, LOW, low", "tr-TR, MEDIUM, medium", "tr-TR, HIGH, high",
        "az-AZ, MINIMAL, minimal", "az-AZ, LOW, low", "az-AZ, MEDIUM, medium", "az-AZ, HIGH, high",
        "en-US, MINIMAL, minimal", "en-US, LOW, low", "en-US, MEDIUM, medium", "en-US, HIGH, high"
    })
    void should_serialize_thinking_level_independently_of_default_locale(
            String languageTag, GeminiThinkingLevel level, String expected) {
        Locale previous = Locale.getDefault();
        Locale previousDisplay = Locale.getDefault(Locale.Category.DISPLAY);
        Locale previousFormat = Locale.getDefault(Locale.Category.FORMAT);
        try {
            Locale.setDefault(Locale.forLanguageTag(languageTag));

            GeminiThinkingConfig config =
                    GeminiThinkingConfig.builder().thinkingLevel(level).build();

            assertThat(Json.toJsonWithoutIndent(config)).isEqualTo("{\"thinkingLevel\":\"" + expected + "\"}");
        } finally {
            Locale.setDefault(previous);
            Locale.setDefault(Locale.Category.DISPLAY, previousDisplay);
            Locale.setDefault(Locale.Category.FORMAT, previousFormat);
        }
    }

    @Test
    void should_preserve_explicit_string_thinking_level() {
        GeminiThinkingConfig config =
                GeminiThinkingConfig.builder().thinkingLevel("CUSTOM_LEVEL").build();

        assertThat(config.thinkingLevel()).isEqualTo("CUSTOM_LEVEL");
    }
}
