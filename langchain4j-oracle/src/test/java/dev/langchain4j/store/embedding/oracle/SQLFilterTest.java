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

    @Test
    void benignKeyIsMappedToQuotedJsonPath() {
        EmbeddingTable table = EmbeddingTable.builder().name("vectors").build();

        String sql = SQLFilters.create(
                        MetadataFilterBuilder.metadataKey("tenant").isEqualTo(1), table::mapMetadataKey)
                .toSQL();

        assertThat(sql)
                .isEqualTo("NVL(JSON_VALUE(metadata, '$.\"tenant\"' RETURNING NUMBER NULL ON ERROR) = ?, false)");
    }

    @Test
    void jsonPathMetacharactersInKeyAreTreatedAsLiteral() {
        EmbeddingTable table = EmbeddingTable.builder().name("vectors").build();

        // The key is embedded as a quoted member name ($."<key>"), so JSON path metacharacters (. [ ] *) are literal
        // characters of the key rather than being interpreted by the JSON path engine.
        String sql = SQLFilters.create(
                        MetadataFilterBuilder.metadataKey("a.b*[0]").isEqualTo(1), table::mapMetadataKey)
                .toSQL();

        assertThat(sql)
                .isEqualTo("NVL(JSON_VALUE(metadata, '$.\"a.b*[0]\"' RETURNING NUMBER NULL ON ERROR) = ?, false)");
    }

    @Test
    void sqlInjectionViaKeyIsEscaped() {
        EmbeddingTable table = EmbeddingTable.builder().name("vectors").build();

        // A crafted key that tries to break out of the JSON path literal and inject "OR 1=1".
        String craftedKey = "tenant' RETURNING NUMBER NULL ON ERROR) = 1 OR 1=1 OR JSON_VALUE(metadata, '$.ignored";

        String sql = SQLFilters.create(
                        MetadataFilterBuilder.metadataKey(craftedKey).isEqualTo(1), table::mapMetadataKey)
                .toSQL();

        // Every injected single quote is doubled, so the crafted key stays inside the JSON path string literal and the
        // "OR 1=1" never becomes a standalone SQL boolean.
        assertThat(sql)
                .isEqualTo("NVL(JSON_VALUE(metadata, '$.\"tenant'' RETURNING NUMBER NULL ON ERROR) = 1 OR 1=1 OR "
                        + "JSON_VALUE(metadata, ''$.ignored\"' RETURNING NUMBER NULL ON ERROR) = ?, false)");
    }

    @Test
    void jsonPathInjectionViaKeyDoubleQuoteIsEscaped() {
        EmbeddingTable table = EmbeddingTable.builder().name("vectors").build();

        // A crafted key that tries to terminate the quoted member name to inject a JSON path predicate.
        String sql = SQLFilters.create(
                        MetadataFilterBuilder.metadataKey("x\"?(@==1)").isEqualTo(1), table::mapMetadataKey)
                .toSQL();

        // The double quote is backslash-escaped, so it cannot close the member name.
        assertThat(sql)
                .isEqualTo("NVL(JSON_VALUE(metadata, '$.\"x\\\"?(@==1)\"' RETURNING NUMBER NULL ON ERROR) = ?, false)");
    }

    @Test
    void sqlInjectionViaKeyInListIsEscaped() {
        EmbeddingTable table = EmbeddingTable.builder().name("vectors").build();

        String sql = SQLFilters.create(
                        MetadataFilterBuilder.metadataKey("x' OR 1=1 --").isIn(1, 2), table::mapMetadataKey)
                .toSQL();

        assertThat(sql)
                .isEqualTo("NVL(JSON_VALUE(metadata, '$.\"x'' OR 1=1 --\"' RETURNING NUMBER NULL ON ERROR)"
                        + " IN (?, ?), false)");
    }

    @Test
    void sqlInjectionViaKeyIsEscaped_clobBranch() {
        EmbeddingTable table = EmbeddingTable.builder().name("vectors").build();

        // A String comparison value routes through the CLOB / DBMS_LOB.COMPARE branch of SQLComparisonFilter.
        String sql = SQLFilters.create(
                        MetadataFilterBuilder.metadataKey("x' OR 1=1 --").isEqualTo("v"), table::mapMetadataKey)
                .toSQL();

        assertThat(sql)
                .isEqualTo("NVL(DBMS_LOB.COMPARE(JSON_VALUE(metadata, '$.\"x'' OR 1=1 --\"' RETURNING CLOB"
                        + " NULL ON ERROR), ?) = 0, false)");
    }
}
