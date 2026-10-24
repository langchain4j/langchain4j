package dev.langchain4j.store.embedding.oracle;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.Test;

class JSONIndexBuilderTest {

    @Test
    void indexNameDoesNotDependOnDefaultLocale() {
        Locale defaultLocale = Locale.getDefault();
        try {
            // In the Turkish locale, "i".toUpperCase() yields a dotted capital "İ"
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            EmbeddingTable table = EmbeddingTable.builder().name("vectors").build();
            JSONIndexBuilder builder = Index.jsonIndexBuilder().key("city", String.class, JSONIndexBuilder.Order.ASC);

            assertThat(builder.getIndexName(table)).isEqualTo("vectors_METADATA_CITY");
        } finally {
            Locale.setDefault(defaultLocale);
        }
    }
}
