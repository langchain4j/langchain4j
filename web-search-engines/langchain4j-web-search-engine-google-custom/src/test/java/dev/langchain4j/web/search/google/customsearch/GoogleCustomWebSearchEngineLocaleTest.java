package dev.langchain4j.web.search.google.customsearch;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.web.search.WebSearchRequest;
import java.util.Locale;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * These tests mutate the JVM default {@link Locale}, so the whole class runs {@link Isolated}
 * and single-threaded to avoid races with the otherwise parallel test suite.
 */
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
class GoogleCustomWebSearchEngineLocaleTest {

    @ParameterizedTest
    @ValueSource(strings = {"en-US", "tr-TR", "az-AZ"})
    void should_build_country_restrict_independently_of_default_locale(String languageTag) {
        // Without Locale.ROOT, "in".toUpperCase() yields the dotted "İN" under tr/az, so "cr" becomes "countryİN"
        withDefaultLocale(languageTag, () -> {
            WebSearchRequest request = WebSearchRequest.builder()
                    .searchTerms("langchain4j")
                    .geoLocation("in")
                    .build();

            assertThat(GoogleCustomWebSearchEngine.setCountryRestrict(request)).isEqualTo("countryIN");
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
