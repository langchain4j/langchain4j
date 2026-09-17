package dev.langchain4j.store.embedding.oracle;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class JSONIndexBuilderTest {

    private final Locale originalLocale = Locale.getDefault();

    @AfterEach
    void restoreLocale() {
        Locale.setDefault(originalLocale);
    }

    @Test
    void indexNameIsLocaleIndependent() {
        Locale.setDefault(Locale.forLanguageTag("tr-TR"));
        EmbeddingTable table = EmbeddingTable.builder().name("vectors").build();

        JSONIndexBuilder builder = Index.jsonIndexBuilder().key("city", String.class, JSONIndexBuilder.Order.ASC);

        // Under the Turkish locale a naive toUpperCase() turns "city" into "CİTY",
        // producing a locale-dependent (and thus unstable) Oracle index name.
        assertThat(builder.getIndexName(table)).isEqualTo("vectors_METADATA_CITY");
    }
}
