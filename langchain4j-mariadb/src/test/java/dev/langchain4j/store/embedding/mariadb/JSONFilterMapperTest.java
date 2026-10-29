package dev.langchain4j.store.embedding.mariadb;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsNotIn;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JSONFilterMapperTest {

    private final JSONFilterMapper mapper = new JSONFilterMapper("metadata");

    @Test
    void should_escape_single_quote_in_key_to_prevent_sql_injection() {
        Filter filter =
                metadataKey("nonexistent') OR 1=1 OR JSON_VALUE(metadata, '$.x").isEqualTo("no-such-value");

        WhereClause whereClause = mapper.map(filter);

        // Every injected single quote is doubled, so the crafted key stays inside the JSON path
        // string literal and the "OR 1=1" never becomes a standalone SQL boolean.
        assertThat(whereClause.sql())
                .isEqualTo("JSON_VALUE(metadata, '$.nonexistent'') OR 1=1 OR JSON_VALUE(metadata, ''$.x')"
                        + " is not null and JSON_VALUE(metadata, '$.nonexistent'') OR 1=1 OR"
                        + " JSON_VALUE(metadata, ''$.x') = ?");
        assertThat(whereClause.parameters()).containsExactly("no-such-value");
    }

    @Test
    void should_escape_backslash_in_key_to_prevent_sql_injection() {
        Filter filter = metadataKey("a\\").isEqualTo("b");

        WhereClause whereClause = mapper.map(filter);

        // A trailing backslash is doubled so it cannot escape the closing quote of the literal.
        assertThat(whereClause.sql())
                .isEqualTo("JSON_VALUE(metadata, '$.a\\\\') is not null and JSON_VALUE(metadata, '$.a\\\\') = ?");
        assertThat(whereClause.parameters()).containsExactly("b");
    }

    @Test
    void should_bind_value_with_backslash_and_quote() {
        Filter filter = metadataKey("category").isEqualTo("x\\' OR 1=1 -- ");

        WhereClause whereClause = mapper.map(filter);

        assertThat(whereClause.sql())
                .isEqualTo("JSON_VALUE(metadata, '$.category') is not null and"
                        + " JSON_VALUE(metadata, '$.category') = ?");
        assertThat(whereClause.parameters()).containsExactly("x\\' OR 1=1 -- ");
    }

    @Test
    void should_bind_map_value_as_string() {
        // A loosely typed value, e.g. a JSON object parsed by Jackson. Its toString() starts with "{",
        // which the driver would otherwise process as a JDBC escape sequence.
        Filter filter = unvalidatedIsEqualTo("tenant", Map.of("fn 1} or 1=1 -- ", ""));

        WhereClause whereClause = mapper.map(filter);

        assertThat(whereClause.sql())
                .isEqualTo("JSON_VALUE(metadata, '$.tenant') is not null and JSON_VALUE(metadata, '$.tenant') = ?");
        assertThat(whereClause.parameters()).containsExactly("{fn 1} or 1=1 -- =}");
    }

    @Test
    void should_bind_every_collection_element() {
        Filter filter = unvalidatedIsNotIn("tag", Map.of("fn 1}) or 1=1 -- ", ""), List.of("x') or 1=1 --"));

        WhereClause whereClause = mapper.map(filter);

        assertThat(whereClause.sql())
                .isEqualTo("(JSON_VALUE(metadata, '$.tag') is null or JSON_VALUE(metadata, '$.tag') not in (?,?))");
        assertThat(whereClause.parameters())
                .containsExactlyInAnyOrder("{fn 1}) or 1=1 -- =}", "[x') or 1=1 --]");
    }

    @Test
    void should_bind_subclass_of_number_type_as_string() {
        // The driver writes a BigDecimal into the query text as toPlainString(), and BigDecimal is not final
        BigDecimal value = new BigDecimal("1") {
            @Override
            public String toPlainString() {
                return "1 or 1=1";
            }

            @Override
            public String toString() {
                return "1 or 1=1";
            }
        };
        Filter filter = unvalidatedIsEqualTo("tenant", value);

        WhereClause whereClause = mapper.map(filter);

        assertThat(whereClause.parameters()).containsExactly("1 or 1=1");
        assertThat(whereClause.parameters().get(0)).isExactlyInstanceOf(String.class);
    }

    @Test
    void parameters_should_follow_placeholder_order() {
        Filter filter = metadataKey("a")
                .isEqualTo("1")
                .and(metadataKey("b").isGreaterThan(2L).or(metadataKey("c").isNotEqualTo(3.5)))
                .and(Filter.not(metadataKey("d").isIn(4, 5)))
                .and(new IsEqualTo("e", true));

        WhereClause whereClause = mapper.map(filter);

        assertThat(whereClause.sql())
                .isEqualTo("JSON_VALUE(metadata, '$.a') is not null and JSON_VALUE(metadata, '$.a') = ?"
                        + " and (JSON_VALUE(metadata, '$.b') > ?"
                        + " or (JSON_VALUE(metadata, '$.c') is null or JSON_VALUE(metadata, '$.c') != ?))"
                        + " and not (JSON_VALUE(metadata, '$.d') in (?,?))"
                        + " and JSON_VALUE(metadata, '$.e') is not null and JSON_VALUE(metadata, '$.e') = ?");
        assertThat(whereClause.parameters().subList(0, 3)).containsExactly("1", 2L, 3.5);
        assertThat(whereClause.parameters().subList(3, 5)).containsExactlyInAnyOrder(4, 5);
        assertThat(whereClause.parameters().get(5)).isEqualTo(true);
    }

    // The filter classes reject values like these, so they are returned from the overridden accessor
    // to check that the store handles them safely on its own.
    private static IsEqualTo unvalidatedIsEqualTo(String key, Object value) {
        return new IsEqualTo(key, "placeholder") {
            @Override
            public Object comparisonValue() {
                return value;
            }
        };
    }

    private static IsNotIn unvalidatedIsNotIn(String key, Object... values) {
        return new IsNotIn(key, List.of("placeholder")) {
            @Override
            public Collection<?> comparisonValues() {
                return List.of(values);
            }
        };
    }
}
