package dev.langchain4j.store.embedding.oracle;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import org.junit.jupiter.api.Test;

class SQLFilterTest {

    @Test
    void uniformTypeIsInGeneratesNativeInClause() {
        String sql = SQLFilters.create(MetadataFilterBuilder.metadataKey("x").isIn(1, 2), (key, type) -> key)
                .toSQL();
        // Before the fix this was "(NVL(x = ?, false) OR NVL(x = ?, false))"
        assertThat(sql).isEqualTo("NVL(x IN (?, ?), false)");
    }

    @Test
    void uniformTypeIsNotInGeneratesNativeNotInClause() {
        String sql = SQLFilters.create(MetadataFilterBuilder.metadataKey("x").isNotIn(1, 2), (key, type) -> key)
                .toSQL();
        assertThat(sql).isEqualTo("NVL(x NOT IN (?, ?), true)");
    }
}
