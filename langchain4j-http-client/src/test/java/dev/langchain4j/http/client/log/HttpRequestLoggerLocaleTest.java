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
 * in the same thread, mirroring {@code SimpleToolSearchStrategyLocaleTest} (#5861).
 */
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
class HttpRequestLoggerLocaleTest {

    private static final String SECRET = "sk-1234567890abcdef";

    @ParameterizedTest
    @ValueSource(strings = {"tr-TR", "az-AZ"})
    void secret_header_should_be_masked_independently_of_default_locale(String languageTag) {
        // Without Locale.ROOT, "X-API-Key".toLowerCase() yields "x-apı-key" (dotless i) under
        // tr/az, matching neither COMMON_SECRET_HEADERS nor the "api-key" substring check, so
        // the key is logged in clear text. Only tr/az map uppercase I to a dotless i; lt keeps
        // the dot, so it is exercised in the non-secret test below instead.
        withDefaultLocale(languageTag, () -> {
            String formatted = HttpRequestLogger.format("X-API-Key", List.of(SECRET));

            assertThat(formatted).doesNotContain(SECRET).isEqualTo("[X-API-Key: sk-12...ef]");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"tr-TR", "az-AZ"})
    void authorization_header_should_be_masked_independently_of_default_locale(String languageTag) {
        withDefaultLocale(languageTag, () -> {
            String formatted = HttpRequestLogger.format("AUTHORIZATION", List.of(SECRET));

            assertThat(formatted).doesNotContain(SECRET);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"tr-TR", "az-AZ", "lt-LT"})
    void non_secret_header_should_not_be_masked_independently_of_default_locale(String languageTag) {
        // The mirror image: a header that must stay readable in every locale.
        withDefaultLocale(languageTag, () -> {
            String formatted = HttpRequestLogger.format("Content-Type", List.of("application/json"));

            assertThat(formatted).isEqualTo("[Content-Type: application/json]");
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
