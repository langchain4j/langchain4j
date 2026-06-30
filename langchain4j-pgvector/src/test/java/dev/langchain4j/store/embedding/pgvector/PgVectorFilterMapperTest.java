package dev.langchain4j.store.embedding.pgvector;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.store.embedding.filter.Filter;
import org.junit.jupiter.api.Test;

class PgVectorFilterMapperTest {

    private final JSONFilterMapper jsonMapper = new JSONFilterMapper("metadata");
    private final ColumnFilterMapper columnMapper = new ColumnFilterMapper();

    @Test
    void mapNotEqual_shouldWrapNullCheckInParentheses_jsonMapper() {
        Filter filter = metadataKey("id").isNotEqualTo("A");

        String sql = jsonMapper.map(filter);

        assertThat(sql).isEqualTo("((metadata->>'id')::text is null or (metadata->>'id')::text != 'A')");
    }

    @Test
    void mapNotIn_shouldWrapNullCheckInParentheses_jsonMapper() {
        Filter filter = metadataKey("id").isNotIn("A", "B", "C");

        String sql = jsonMapper.map(filter);

        assertThat(sql).isEqualTo("(metadata->>'id' is null or metadata->>'id' not in ('A','B','C'))");
    }

    @Test
    void mapNotEqual_shouldWrapNullCheckInParentheses_columnMapper() {
        Filter filter = metadataKey("id").isNotEqualTo("A");

        String sql = columnMapper.map(filter);

        assertThat(sql).isEqualTo("(id::text is null or id::text != 'A')");
    }

    @Test
    void mapNotIn_shouldWrapNullCheckInParentheses_columnMapper() {
        Filter filter = metadataKey("id").isNotIn("A", "B", "C");

        String sql = columnMapper.map(filter);

        assertThat(sql).isEqualTo("(id is null or id not in ('A','B','C'))");
    }

    @Test
    void notIn_combinedWithAnd_shouldPreserveAndPrecedence() {
        Filter filter =
                metadataKey("type").isEqualTo("user").and(metadataKey("id").isNotIn("A", "B"));

        String sql = jsonMapper.map(filter);

        assertThat(sql)
                .isEqualTo(
                        "(metadata->>'type')::text is not null and (metadata->>'type')::text = 'user' and (metadata->>'id' is null or metadata->>'id' not in ('A','B'))");
    }

    @Test
    void notEqual_combinedWithAnd_shouldPreserveAndPrecedence() {
        Filter filter =
                metadataKey("type").isEqualTo("user").and(metadataKey("id").isNotEqualTo("A"));

        String sql = jsonMapper.map(filter);

        assertThat(sql)
                .isEqualTo(
                        "(metadata->>'type')::text is not null and (metadata->>'type')::text = 'user' and ((metadata->>'id')::text is null or (metadata->>'id')::text != 'A')");
    }

    @Test
    void sqlInjectionViaKey_shouldBeEscaped_jsonMapper() {
        // A crafted key that tries to break out of the ->>'...' string literal and inject "OR 1=1".
        Filter filter = metadataKey("x') IS NOT NULL OR 1=1 OR (metadata->>'y").isEqualTo("no-such-value");

        String sql = jsonMapper.map(filter);

        // Every injected single quote is doubled, so the crafted key stays inside the JSON key
        // string literal and the "OR 1=1" never becomes a standalone SQL boolean.
        assertThat(sql)
                .isEqualTo("(metadata->>'x'') IS NOT NULL OR 1=1 OR (metadata->>''y')::text is not null"
                        + " and (metadata->>'x'') IS NOT NULL OR 1=1 OR (metadata->>''y')::text = 'no-such-value'");
    }

    @Test
    void sqlInjectionViaKeyInList_shouldBeEscaped_jsonMapper() {
        Filter filter = metadataKey("x' OR 1=1 --").isIn("a", "b");

        String sql = jsonMapper.map(filter);

        assertThat(sql).isEqualTo("metadata->>'x'' OR 1=1 --' in ('a','b')");
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

        String sql = jsonMapper.map(filter);

        assertThat(sql)
                .isEqualTo("(metadata->>'path')::text is not null"
                        + " and position('a.b' in (metadata->>'path')::text) > 0");
    }

    @Test
    void mapContains_shouldUseLiteralSubstringNotRegex_columnMapper() {
        Filter filter = metadataKey("path").containsString("a.b");

        String sql = columnMapper.map(filter);

        assertThat(sql).isEqualTo("path::text is not null and position('a.b' in path::text) > 0");
    }

    @Test
    void mapContains_shouldEscapeSingleQuoteInValue_jsonMapper() {
        Filter filter = metadataKey("name").containsString("O'Brien");

        String sql = jsonMapper.map(filter);

        assertThat(sql)
                .isEqualTo("(metadata->>'name')::text is not null"
                        + " and position('O''Brien' in (metadata->>'name')::text) > 0");
    }
}
