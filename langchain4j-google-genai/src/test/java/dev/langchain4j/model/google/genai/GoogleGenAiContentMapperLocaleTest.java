package dev.langchain4j.model.google.genai;

import static dev.langchain4j.model.google.genai.GoogleGenAiContentMapper.detectMimeType;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.genai.types.Candidate;
import com.google.genai.types.Content;
import com.google.genai.types.ExecutableCode;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import java.net.URI;
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
class GoogleGenAiContentMapperLocaleTest {

    @ParameterizedTest
    @ValueSource(strings = {"tr-TR", "az-AZ", "lt-LT", "en-US"})
    void uppercase_extension_should_resolve_locale_independently(String languageTag) {
        // Without Locale.ROOT, "GIF".toLowerCase() yields "gıf" under tr/az, which is absent from
        // EXTENSION_TO_MIME_TYPE, so a valid image URL is rejected with IllegalArgumentException.
        withDefaultLocale(
                languageTag,
                () -> assertThat(detectMimeType(URI.create("https://example.com/photo.GIF")))
                        .isEqualTo("image/gif"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"tr-TR", "az-AZ", "lt-LT", "en-US"})
    void uppercase_tiff_extension_should_resolve_locale_independently(String languageTag) {
        withDefaultLocale(
                languageTag,
                () -> assertThat(detectMimeType(URI.create("https://example.com/scan.TIFF")))
                        .isEqualTo("image/tiff"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"tr-TR", "az-AZ", "lt-LT", "en-US"})
    void code_language_should_be_lowercased_locale_independently(String languageTag) {
        GenerateContentResponse response = GenerateContentResponse.builder()
                .candidates(List.of(Candidate.builder()
                        .content(Content.builder()
                                .parts(List.of(Part.builder()
                                        .executableCode(ExecutableCode.builder()
                                                .language("JAVASCRIPT")
                                                .code("console.log(1)"))
                                        .build()))
                                .build())
                        .build()))
                .build();

        withDefaultLocale(
                languageTag,
                () -> assertThat(GoogleGenAiContentMapper.toChatResponse(response, "test-model", false, true)
                                .aiMessage()
                                .text())
                        .startsWith("Code executed:\n```javascript\n"));
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
