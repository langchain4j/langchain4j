package dev.langchain4j.store.embedding.pgvector;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import static org.assertj.core.api.Assertions.assertThat;

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
}
