package dev.langchain4j.store.embedding.mariadb;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.store.embedding.filter.Filter;
import org.junit.jupiter.api.Test;

class ColumnFilterMapperTest {

    private final ColumnFilterMapper mapper = new ColumnFilterMapper();

    @Test
    void should_quote_a_normal_key_as_identifier() {
        Filter filter = metadataKey("category").isEqualTo("sports");

        String sql = mapper.map(filter);

        assertThat(sql).isEqualTo("`category` is not null and `category` = 'sports'");
    }

    @Test
    void should_reject_key_that_cannot_be_quoted_instead_of_emitting_raw_sql() {
        // A key containing a NUL character cannot be quoted as an identifier. It must be rejected,
        // not silently passed through as raw SQL (which would be a SQL injection vector).
        Filter filter = metadataKey("key IS NOT NULL OR 1=1 -- \0").isEqualTo("v");

        assertThatThrownBy(() -> mapper.map(filter)).isInstanceOf(IllegalArgumentException.class);
    }
}
