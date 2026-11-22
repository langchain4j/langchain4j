package dev.langchain4j.http.client.log;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * These tests mutate the JVM default {@link Locale}, so the whole class runs {@link Isolated}
 * and single-threaded to avoid races with the otherwise parallel test suite.
 * <p>
 * {@code tr} and {@code az} lowercase {@code 'I'} to the dotless {@code 'ı'} (U+0131),
 * {@code lt} applies its own dot rules, and {@code en-US} is the baseline.
 */
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
class HttpRequestLoggerTest {

    private static final String API_KEY = "sk-1234567890abcdefgh";

    @ParameterizedTest
    @ValueSource(strings = {"tr-TR", "az-AZ", "lt-LT", "en-US"})
    void secret_header_should_be_masked_locale_independently(String languageTag) {
        // Without Locale.ROOT, "X-SESSION-ID".toLowerCase() yields "x-sessıon-ıd" under tr/az, which does not
        // contain "session", so the session value is logged in clear text.
        withDefaultLocale(languageTag, () -> {
            String formatted = HttpRequestLogger.format("X-SESSION-ID", List.of(API_KEY));

            assertThat(formatted).isEqualTo("[X-SESSION-ID: sk-12...gh]").doesNotContain(API_KEY);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"tr-TR", "az-AZ", "lt-LT", "en-US"})
    void dotless_header_name_should_not_be_masked_locale_independently(String languageTag) {
        // The mirror image: a header name spelled with the dotless 'ı' is not a secret header name
        // in any locale, so its value is rendered unchanged.
        withDefaultLocale(languageTag, () -> {
            String formatted = HttpRequestLogger.format("X-Sessıon-Id", List.of(API_KEY));

            assertThat(formatted).isEqualTo("[X-Sessıon-Id: " + API_KEY + "]");
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
