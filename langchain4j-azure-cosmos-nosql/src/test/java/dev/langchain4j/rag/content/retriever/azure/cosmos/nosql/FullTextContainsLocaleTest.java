package dev.langchain4j.rag.content.retriever.azure.cosmos.nosql;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.document.Metadata;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FullTextContainsLocaleTest {

    private static final String KEY = "text";
    private static final String DOCUMENT = "pin code 1234";
    private static final Locale DEFAULT_LOCALE = Locale.getDefault();

    private final Metadata metadata = Metadata.from(KEY, DOCUMENT);

    @BeforeEach
    void setUp() {
        Locale.setDefault(Locale.forLanguageTag("tr"));
    }

    @AfterEach
    void tearDown() {
        Locale.setDefault(DEFAULT_LOCALE);
    }

    @Test
    void fullTextContains_matches_uppercase_term_under_turkish_locale() {
        assertThat(new FullTextContains(KEY, "PIN").test(metadata)).isTrue();
    }

    @Test
    void fullTextContains_doesNotMatch_absent_term_under_turkish_locale() {
        assertThat(new FullTextContains(KEY, "TOKEN").test(metadata)).isFalse();
    }

    @Test
    void fullTextContainsAll_matches_uppercase_terms_under_turkish_locale() {
        assertThat(new FullTextContainsAll(KEY, "PIN", "1234").test(metadata)).isTrue();
    }

    @Test
    void fullTextContainsAll_doesNotMatch_when_one_term_is_absent_under_turkish_locale() {
        assertThat(new FullTextContainsAll(KEY, "PIN", "TOKEN").test(metadata)).isFalse();
    }

    @Test
    void fullTextContainsAny_matches_present_term_under_turkish_locale() {
        assertThat(new FullTextContainsAny(KEY, "TOKEN", "PIN").test(metadata)).isTrue();
    }

    @Test
    void fullTextContainsAny_doesNotMatch_when_all_terms_are_absent_under_turkish_locale() {
        assertThat(new FullTextContainsAny(KEY, "TOKEN", "PASSWORD").test(metadata)).isFalse();
    }
}
