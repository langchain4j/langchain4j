package dev.langchain4j.model.mistralai.internal.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * These tests mutate the JVM default {@link Locale}, so the whole class runs {@link Isolated}
 * and single-threaded, which keeps them deterministic even if this module ever enables
 * parallel test execution.
 * <p>
 * {@code tr} and {@code az} lowercase {@code 'I'} to the dotless {@code 'ı'} (U+0131),
 * {@code lt} applies its own dot rules, and {@code en-US} is the baseline. The dotless
 * variant would silently break a future constant containing an 'I' (e.g. a JSON_SCHEMA-style
 * name), because {@link MistralAiResponseFormat#fromType} serializes {@code toString()} onto
 * the wire.
 */
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
class MistralAiResponseFormatTypeLocaleTest {

    @ParameterizedTest
    @ValueSource(strings = {"tr-TR", "az-AZ", "lt-LT", "en-US"})
    void to_string_should_be_locale_independent(String languageTag) {
        withDefaultLocale(languageTag, () -> {
            assertThat(MistralAiResponseFormatType.TEXT.toString()).isEqualTo("text");
            assertThat(MistralAiResponseFormatType.JSON_OBJECT.toString()).isEqualTo("json_object");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"tr-TR", "az-AZ"})
    void to_string_should_never_contain_dotless_i(String languageTag) {
        // The mirror image: under tr/az any future constant with an 'I' (e.g. JSON_SCHEMA)
        // must not degrade to the dotless 'ı'.
        withDefaultLocale(languageTag, () -> {
            for (MistralAiResponseFormatType type : MistralAiResponseFormatType.values()) {
                assertThat(type.toString()).doesNotContain("ı");
            }
        });
    }

    private static void withDefaultLocale(String languageTag, Runnable action) {
        Locale previousDefault = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag(languageTag));
            action.run();
        } finally {
            Locale.setDefault(previousDefault);
        }
    }
}
