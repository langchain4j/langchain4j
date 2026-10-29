package dev.langchain4j.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
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
class CustomMimeTypesFileTypeDetectorLocaleTest {

    private static final Map<String, String> CUSTOM_MAPPINGS = Map.of("ai", "application/illustrator");

    @ParameterizedTest
    @ValueSource(strings = {"tr-TR", "az-AZ", "lt-LT", "en-US"})
    void uppercase_extension_should_resolve_locale_independently(String languageTag) {
        // Without Locale.ROOT, "AI".toLowerCase() yields "aı" under tr/az, so the mapping is
        // missed and the JDK fallback answers "application/postscript" instead.
        CustomMimeTypesFileTypeDetector detector = new CustomMimeTypesFileTypeDetector(CUSTOM_MAPPINGS);

        withDefaultLocale(
                languageTag,
                () -> assertThat(detector.probeContentType(Path.of("/foo/bar/logo.AI")))
                        .isEqualTo("application/illustrator"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"tr-TR", "az-AZ", "lt-LT", "en-US"})
    void dotless_extension_should_not_resolve_locale_independently(String languageTag) {
        // The mirror image: "aı" must never be folded onto the "ai" mapping, which is what
        // happens under tr/az once both sides are lowercased with the default locale.
        CustomMimeTypesFileTypeDetector detector = new CustomMimeTypesFileTypeDetector(CUSTOM_MAPPINGS);

        withDefaultLocale(
                languageTag,
                () -> assertThat(detector.probeContentType(Path.of("/foo/bar/logo.aı")))
                        .isNotEqualTo("application/illustrator"));
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
