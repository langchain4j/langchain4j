package dev.langchain4j.store.embedding.pgvector;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsGreaterThan;
import dev.langchain4j.store.embedding.filter.comparison.IsGreaterThanOrEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsIn;
import dev.langchain4j.store.embedding.filter.comparison.IsLessThan;
import dev.langchain4j.store.embedding.filter.comparison.IsLessThanOrEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsNotEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsNotIn;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PgVectorFilterMapperTest {

    private final JSONFilterMapper jsonMapper = new JSONFilterMapper("metadata");
    private final ColumnFilterMapper columnMapper = new ColumnFilterMapper();

    @Test
    void mapNotEqual_shouldWrapNullCheckInParentheses_jsonMapper() {
        Filter filter = metadataKey("id").isNotEqualTo("A");

        WhereClause whereClause = jsonMapper.map(filter);

        assertThat(whereClause.sql()).isEqualTo("((metadata->>'id')::text is null or (metadata->>'id')::text != ?)");
        assertThat(whereClause.parameters()).containsExactly("A");
    }

    @Test
    void mapNotIn_shouldWrapNullCheckInParentheses_jsonMapper() {
        Filter filter = metadataKey("id").isNotIn("A", "B", "C");

        WhereClause whereClause = jsonMapper.map(filter);

        assertThat(whereClause.sql()).isEqualTo("(metadata->>'id' is null or metadata->>'id' not in (?,?,?))");
        assertThat(whereClause.parameters()).containsExactlyInAnyOrder("A", "B", "C");
    }

    @Test
    void mapNotEqual_shouldWrapNullCheckInParentheses_columnMapper() {
        Filter filter = metadataKey("id").isNotEqualTo("A");

        WhereClause whereClause = columnMapper.map(filter);

        assertThat(whereClause.sql()).isEqualTo("(id::text is null or id::text != ?)");
        assertThat(whereClause.parameters()).containsExactly("A");
    }

    @Test
    void mapNotIn_shouldWrapNullCheckInParentheses_columnMapper() {
        Filter filter = metadataKey("id").isNotIn("A", "B", "C");

        WhereClause whereClause = columnMapper.map(filter);

        assertThat(whereClause.sql()).isEqualTo("(id is null or id not in (?,?,?))");
        assertThat(whereClause.parameters()).containsExactlyInAnyOrder("A", "B", "C");
    }

    @Test
    void notIn_combinedWithAnd_shouldPreserveAndPrecedence() {
        Filter filter =
                metadataKey("type").isEqualTo("user").and(metadataKey("id").isNotIn("A", "B"));

        WhereClause whereClause = jsonMapper.map(filter);

        assertThat(whereClause.sql())
                .isEqualTo(
                        "(metadata->>'type')::text is not null and (metadata->>'type')::text = ? and (metadata->>'id' is null or metadata->>'id' not in (?,?))");
        assertThat(whereClause.parameters()).hasSize(3).startsWith("user").contains("A", "B");
    }

    @Test
    void notEqual_combinedWithAnd_shouldPreserveAndPrecedence() {
        Filter filter =
                metadataKey("type").isEqualTo("user").and(metadataKey("id").isNotEqualTo("A"));

        WhereClause whereClause = jsonMapper.map(filter);

        assertThat(whereClause.sql())
                .isEqualTo(
                        "(metadata->>'type')::text is not null and (metadata->>'type')::text = ? and ((metadata->>'id')::text is null or (metadata->>'id')::text != ?)");
        assertThat(whereClause.parameters()).containsExactly("user", "A");
    }

    @Test
    void sqlInjectionViaKey_shouldBeEscaped_jsonMapper() {
        // A crafted key that tries to break out of the ->>'...' string literal and inject "OR 1=1".
        Filter filter = metadataKey("x') IS NOT NULL OR 1=1 OR (metadata->>'y").isEqualTo("no-such-value");

        WhereClause whereClause = jsonMapper.map(filter);

        // Every injected single quote is doubled, so the crafted key stays inside the JSON key
        // string literal and the "OR 1=1" never becomes a standalone SQL boolean.
        assertThat(whereClause.sql())
                .isEqualTo("(metadata->>'x'') IS NOT NULL OR 1=1 OR (metadata->>''y')::text is not null"
                        + " and (metadata->>'x'') IS NOT NULL OR 1=1 OR (metadata->>''y')::text = ?");
        assertThat(whereClause.parameters()).containsExactly("no-such-value");
    }

    @Test
    void sqlInjectionViaKeyInList_shouldBeEscaped_jsonMapper() {
        Filter filter = metadataKey("x' OR 1=1 --").isIn("a", "b");

        WhereClause whereClause = jsonMapper.map(filter);

        assertThat(whereClause.sql()).isEqualTo("metadata->>'x'' OR 1=1 --' in (?,?)");
    }

    @Test
    void sqlInjectionViaKey_shouldBeRejected_columnMapper() {
        // In COLUMN_PER_KEY mode the key is a bare identifier; an unsafe key must be rejected, not
        // concatenated into the SQL as raw text (which would be a SQL injection vector).
        Filter filter = metadataKey("1=1 OR true --").isEqualTo("v");

        assertThatThrownBy(() -> columnMapper.map(filter)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sqlInjectionViaKeyInList_shouldBeRejected_columnMapper() {
        Filter filter = metadataKey("id) OR (1=1").isIn("a", "b");

        assertThatThrownBy(() -> columnMapper.map(filter)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void mapContains_shouldUseLiteralSubstringNotRegex_jsonMapper() {
        // A value with regex metacharacters must be matched literally (ContainsString is str.contains),
        // not interpreted as a POSIX regular expression by the "~" operator.
        Filter filter = metadataKey("path").containsString("a.b");

        WhereClause whereClause = jsonMapper.map(filter);

        assertThat(whereClause.sql())
                .isEqualTo("(metadata->>'path')::text is not null"
                        + " and position(? in (metadata->>'path')::text) > 0");
        assertThat(whereClause.parameters()).containsExactly("a.b");
    }

    @Test
    void mapContains_shouldUseLiteralSubstringNotRegex_columnMapper() {
        Filter filter = metadataKey("path").containsString("a.b");

        WhereClause whereClause = columnMapper.map(filter);

        assertThat(whereClause.sql()).isEqualTo("path::text is not null and position(? in path::text) > 0");
        assertThat(whereClause.parameters()).containsExactly("a.b");
    }

    @Test
    void mapContains_shouldBindValueWithSingleQuote_jsonMapper() {
        Filter filter = metadataKey("name").containsString("O'Brien");

        WhereClause whereClause = jsonMapper.map(filter);

        assertThat(whereClause.sql())
                .isEqualTo("(metadata->>'name')::text is not null"
                        + " and position(? in (metadata->>'name')::text) > 0");
        assertThat(whereClause.parameters()).containsExactly("O'Brien");
    }

    @Test
    void typedValue_shouldBeBoundAsTextAndCastByKey_jsonMapper() {
        Filter filter = metadataKey("age").isGreaterThan(18);

        WhereClause whereClause = jsonMapper.map(filter);

        assertThat(whereClause.sql()).isEqualTo("(metadata->>'age')::int > ?");
        assertThat(whereClause.parameters()).containsExactly("18");
    }

    @Test
    void nestedCollectionValue_shouldBeBound_jsonMapper() {
        // A loosely typed value, e.g. a JSON array inside the array parsed by Jackson, is bound as one text value
        Filter filter = unvalidatedIsIn("tag", List.of("x') or 1=1 --"));

        WhereClause whereClause = jsonMapper.map(filter);

        assertThat(whereClause.sql()).isEqualTo("metadata->>'tag' in (?)");
        assertThat(whereClause.parameters()).containsExactly("[x') or 1=1 --]");
    }

    @Test
    void charSequenceValue_shouldBeBound_columnMapper() {
        Filter filter = unvalidatedIsNotIn("tag", new StringBuilder("x') or 1=1 --"));

        WhereClause whereClause = columnMapper.map(filter);

        assertThat(whereClause.sql()).isEqualTo("(tag is null or tag not in (?))");
        assertThat(whereClause.parameters()).containsExactly("x') or 1=1 --");
    }

    @Test
    void mapValue_shouldBeBound_jsonMapper() {
        Filter filter = unvalidatedIsEqualTo("tenant", Map.of("x') or 1=1 --", ""));

        WhereClause whereClause = jsonMapper.map(filter);

        // there is no SQL type for a map, so the database rejects the cast; the value itself is only bound
        assertThat(whereClause.sql())
                .isEqualTo("(metadata->>'tenant')::null is not null and (metadata->>'tenant')::null = ?");
        assertThat(whereClause.parameters()).containsExactly("{x') or 1=1 --=}");
    }

    @Test
    void byteAndShortValues_shouldBeCastToInt_jsonMapper() {
        Filter filter = new IsGreaterThan("age", (byte) 1).and(new IsLessThan("age", (short) 3));

        WhereClause whereClause = jsonMapper.map(filter);

        assertThat(whereClause.sql()).isEqualTo("(metadata->>'age')::int > ? and (metadata->>'age')::int < ?");
        assertThat(whereClause.parameters()).containsExactly("1", "3");
    }

    @Test
    void bigIntegerAndBigDecimalValues_shouldBeCastToNumeric_jsonMapper() {
        Filter filter = new IsEqualTo("count", new BigInteger("12345678901234567890"))
                .and(new IsGreaterThanOrEqualTo("price", new BigDecimal("9.99")));

        WhereClause whereClause = jsonMapper.map(filter);

        assertThat(whereClause.sql())
                .isEqualTo("(metadata->>'count')::numeric is not null and (metadata->>'count')::numeric = ?"
                        + " and (metadata->>'price')::numeric >= ?");
        assertThat(whereClause.parameters()).containsExactly("12345678901234567890", "9.99");
    }

    @Test
    void widenedNumberValues_shouldBeCastByKey_columnMapper() {
        Filter filter = new IsGreaterThan("age", (short) 18)
                .and(new IsLessThanOrEqualTo("price", new BigDecimal("9.99")))
                .and(new IsNotEqualTo("count", new BigInteger("7")));

        WhereClause whereClause = columnMapper.map(filter);

        assertThat(whereClause.sql())
                .isEqualTo("age::int > ? and price::numeric <= ?"
                        + " and (count::numeric is null or count::numeric != ?)");
        assertThat(whereClause.parameters()).containsExactly("18", "9.99", "7");
    }

    @Test
    void parameters_shouldFollowPlaceholderOrder() {
        Filter filter = metadataKey("a")
                .isEqualTo("1")
                .and(metadataKey("b").isGreaterThan(2L).or(metadataKey("c").isNotEqualTo(3.5)));

        WhereClause whereClause = jsonMapper.map(filter);

        assertThat(whereClause.sql())
                .isEqualTo("(metadata->>'a')::text is not null and (metadata->>'a')::text = ?"
                        + " and ((metadata->>'b')::bigint > ?"
                        + " or ((metadata->>'c')::float8 is null or (metadata->>'c')::float8 != ?))");
        assertThat(whereClause.parameters()).containsExactly("1", "2", "3.5");
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

    private static IsIn unvalidatedIsIn(String key, Object... values) {
        return new IsIn(key, List.of("placeholder")) {
            @Override
            public Collection<?> comparisonValues() {
                return List.of(values);
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
