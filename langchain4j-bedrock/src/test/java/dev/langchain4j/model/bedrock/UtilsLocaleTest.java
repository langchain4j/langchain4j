package dev.langchain4j.model.bedrock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

import dev.langchain4j.data.image.Image;
import dev.langchain4j.exception.UnsupportedFeatureException;
import java.net.URI;
import java.util.Locale;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * These tests mutate the JVM default {@link Locale}, so the whole class runs {@link Isolated}
 * and single-threaded, which keeps them deterministic even if this module ever enables
 * parallel test execution.
 * <p>
 * {@code tr} and {@code az} lowercase {@code 'I'} to the dotless {@code 'ı'} (U+0131),
 * {@code lt} applies its own dot rules, and {@code en-US} is the baseline.
 */
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
class UtilsLocaleTest {

    @ParameterizedTest
    @ValueSource(strings = {"tr-TR", "az-AZ", "lt-LT", "en-US"})
    void uppercase_extension_should_resolve_locale_independently(String languageTag) {
        // "gif" is the only extension mapping containing an 'i': without Locale.ROOT,
        // "GIF".toLowerCase() yields "gıf" under tr/az and the lookup misses.
        withDefaultLocale(
                languageTag,
                () -> assertThat(Utils.extractAndValidateFormat(
                                Image.builder().url(URI.create("file:///image.GIF")).build()))
                        .isEqualTo("gif"));
    }

    @ParameterizedTest
    @CsvSource({
        "tr-TR, IMAGE/GIF, gif",
        "tr-TR, IMAGE/PNG, png",
        "az-AZ, IMAGE/GIF, gif",
        "az-AZ, IMAGE/PNG, png",
        "lt-LT, IMAGE/GIF, gif",
        "lt-LT, IMAGE/PNG, png",
        "en-US, IMAGE/GIF, gif",
        "en-US, IMAGE/PNG, png"
    })
    void uppercase_mime_type_should_resolve_locale_independently(String languageTag, String mimeType, String expected) {
        // Every MIME mapping starts with "image/", so under tr/az any uppercase 'I' misses,
        // not only the gif entry. The URI carries no extension, isolating the MIME path.
        withDefaultLocale(
                languageTag,
                () -> assertThat(Utils.extractAndValidateFormat(Image.builder()
                                .mimeType(mimeType)
                                .url(URI.create("file:///image"))
                                .build()))
                        .isEqualTo(expected));
    }

    @ParameterizedTest
    @ValueSource(strings = {"tr-TR", "az-AZ", "lt-LT", "en-US"})
    void dotless_extension_should_not_resolve(String languageTag) {
        // The mirror image: a dotless "gıf" must never fold onto the "gif" mapping.
        withDefaultLocale(
                languageTag,
                () -> assertThatExceptionOfType(UnsupportedFeatureException.class)
                        .isThrownBy(() -> Utils.extractAndValidateFormat(Image.builder()
                                .mimeType("ımage/gıf")
                                .build())));
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
