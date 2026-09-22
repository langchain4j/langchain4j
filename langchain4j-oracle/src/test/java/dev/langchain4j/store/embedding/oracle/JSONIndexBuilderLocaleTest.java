package dev.langchain4j.store.embedding.oracle;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JSONIndexBuilderLocaleTest {

    private static final Locale DEFAULT_LOCALE = Locale.getDefault();

    private final EmbeddingTable embeddingTable =
            EmbeddingTable.builder().name("vectors").build();

    @BeforeEach
    void setUp() {
        Locale.setDefault(Locale.forLanguageTag("tr"));
    }

    @AfterEach
    void tearDown() {
        Locale.setDefault(DEFAULT_LOCALE);
    }

    @Test
    void index_name_is_the_same_under_turkish_locale() {
        JSONIndexBuilder indexBuilder = new JSONIndexBuilder().key("title", String.class, JSONIndexBuilder.Order.ASC);

        assertThat(indexBuilder.getIndexName(embeddingTable)).isEqualTo("vectors_METADATA_TITLE");
    }

    @Test
    void index_name_of_several_keys_is_the_same_under_turkish_locale() {
        JSONIndexBuilder indexBuilder = new JSONIndexBuilder()
                .key("title", String.class, JSONIndexBuilder.Order.ASC)
                .key("isbn", String.class, JSONIndexBuilder.Order.DESC);

        assertThat(indexBuilder.getIndexName(embeddingTable)).isEqualTo("vectors_METADATA_TITLE_ISBN");
    }
}
