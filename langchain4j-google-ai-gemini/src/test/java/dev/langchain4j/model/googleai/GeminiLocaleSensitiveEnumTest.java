package dev.langchain4j.model.googleai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.googleai.GeminiContent.GeminiPart.GeminiCodeExecutionResult.GeminiOutcome;
import dev.langchain4j.model.googleai.GeminiContent.GeminiPart.GeminiExecutableCode.GeminiLanguage;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

@Isolated
class GeminiLocaleSensitiveEnumTest {

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

    @Test
    void should_map_gemini_type_independently_of_default_locale() {
        assertThat(GeminiType.INTEGER.toString()).isEqualTo("integer");
    }

    @Test
    void should_map_gemini_language_independently_of_default_locale() {
        assertThat(GeminiLanguage.PYTHON.toString()).isEqualTo("python");
    }

    @Test
    void should_map_gemini_outcome_independently_of_default_locale() {
        assertThat(GeminiOutcome.OUTCOME_FAILED.toString()).isEqualTo("outcome_failed");
    }

    @Test
    void should_map_gemini_role_independently_of_default_locale() {
        assertThat(GeminiRole.MODEL.toString()).isEqualTo("model");
    }
}
