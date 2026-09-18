package dev.langchain4j.store.embedding.mariadb;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.store.embedding.filter.Filter;
import org.junit.jupiter.api.Test;

class JSONFilterMapperTest {

    private final JSONFilterMapper mapper = new JSONFilterMapper("metadata");

    @Test
    void should_escape_single_quote_in_key_to_prevent_sql_injection() {
        Filter filter =
                metadataKey("nonexistent') OR 1=1 OR JSON_VALUE(metadata, '$.x").isEqualTo("no-such-value");

        String sql = mapper.map(filter);

        // Every injected single quote is doubled, so the crafted key stays inside the JSON path
        // string literal and the "OR 1=1" never becomes a standalone SQL boolean.
        assertThat(sql)
                .isEqualTo("JSON_VALUE(metadata, '$.nonexistent'') OR 1=1 OR JSON_VALUE(metadata, ''$.x')"
                        + " is not null and JSON_VALUE(metadata, '$.nonexistent'') OR 1=1 OR"
                        + " JSON_VALUE(metadata, ''$.x') = 'no-such-value'");
    }

    @Test
    void should_escape_backslash_in_key_to_prevent_sql_injection() {
        Filter filter = metadataKey("a\\").isEqualTo("b");

        String sql = mapper.map(filter);

        // A trailing backslash is doubled so it cannot escape the closing quote of the literal.
        assertThat(sql)
                .isEqualTo("JSON_VALUE(metadata, '$.a\\\\') is not null and JSON_VALUE(metadata, '$.a\\\\') = 'b'");
    }

    @Test
    void should_escape_backslash_and_quote_in_value_to_prevent_sql_injection() {
        Filter filter = metadataKey("category").isEqualTo("x\\' OR 1=1 -- ");

        String sql = mapper.map(filter);

        // The backslash is doubled and the quote is doubled, so the value cannot break out
        // of its string literal even though MariaDB treats backslash as an escape character.
        assertThat(sql)
                .isEqualTo("JSON_VALUE(metadata, '$.category') is not null and"
                        + " JSON_VALUE(metadata, '$.category') = 'x\\\\'' OR 1=1 -- '");
    }
}
