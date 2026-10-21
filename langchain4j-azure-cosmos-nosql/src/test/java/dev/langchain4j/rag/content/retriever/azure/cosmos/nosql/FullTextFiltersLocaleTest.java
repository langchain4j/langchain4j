package dev.langchain4j.rag.content.retriever.azure.cosmos.nosql;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.document.Metadata;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FullTextFiltersLocaleTest {

    private Locale originalLocale;

    @BeforeEach
    void setTurkishLocale() {
        originalLocale = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("tr"));
    }

    @AfterEach
    void restoreLocale() {
        Locale.setDefault(originalLocale);
    }

    @Test
    void fullTextContains_is_case_insensitive_under_turkish_locale() {
        Metadata metadata = Metadata.from("key", "TITLE");

        assertThat(new FullTextContains("key", "title").test(metadata)).isTrue();
        assertThat(new FullTextContains("key", "TITLE").test(Metadata.from("key", "title")))
                .isTrue();
    }

    @Test
    void fullTextContainsAll_is_case_insensitive_under_turkish_locale() {
        Metadata metadata = Metadata.from("key", "TITLE");

        assertThat(new FullTextContainsAll("key", "title", "TIT").test(metadata))
                .isTrue();
    }

    @Test
    void fullTextContainsAny_is_case_insensitive_under_turkish_locale() {
        Metadata metadata = Metadata.from("key", "TITLE");

        assertThat(new FullTextContainsAny("key", "missing", "title").test(metadata))
                .isTrue();
    }
}
