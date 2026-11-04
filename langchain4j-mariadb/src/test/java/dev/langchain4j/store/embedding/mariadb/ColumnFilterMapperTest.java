package dev.langchain4j.store.embedding.mariadb;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.IsIn;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.Test;

class ColumnFilterMapperTest {

    private final ColumnFilterMapper mapper = new ColumnFilterMapper();

    @Test
    void should_quote_a_normal_key_as_identifier() {
        Filter filter = metadataKey("category").isEqualTo("sports");

        WhereClause whereClause = mapper.map(filter);

        assertThat(whereClause.sql()).isEqualTo("`category` is not null and `category` = ?");
        assertThat(whereClause.parameters()).containsExactly("sports");
    }

    @Test
    void should_reject_key_that_cannot_be_quoted_instead_of_emitting_raw_sql() {
        // A key containing a NUL character cannot be quoted as an identifier. It must be rejected,
        // not silently passed through as raw SQL (which would be a SQL injection vector).
        Filter filter = metadataKey("key IS NOT NULL OR 1=1 -- \0").isEqualTo("v");

        assertThatThrownBy(() -> mapper.map(filter)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_bind_numbers_as_numbers_and_other_values_as_strings() {
        Filter filter = metadataKey("age")
                .isGreaterThan(18)
                .and(unvalidatedIsIn("name", new StringBuilder("x) or (1=1")));

        WhereClause whereClause = mapper.map(filter);

        assertThat(whereClause.sql()).isEqualTo("`age` > ? and `name` in (?)");
        assertThat(whereClause.parameters()).containsExactly(18, "x) or (1=1");
    }

    // The filter classes reject values like these, so they are returned from the overridden accessor
    // to check that the store handles them safely on its own.
    private static IsIn unvalidatedIsIn(String key, Object... values) {
        return new IsIn(key, List.of("placeholder")) {
            @Override
            public Collection<?> comparisonValues() {
                return List.of(values);
            }
        };
    }
}
