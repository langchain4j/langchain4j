package dev.langchain4j.store.embedding.oracle;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import dev.langchain4j.store.embedding.filter.comparison.*;
import java.sql.*;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import oracle.jdbc.OracleType;
import org.junit.jupiter.api.Test;

/**
 * Verifies that {@link SQLFilter} behaves as specified in its JavaDoc. The
 * {@link dev.langchain4j.store.embedding.EmbeddingStoreWithFilteringIT} test already covers many cases. This test class
 * covers some additional behavior which that test might not check for.
 */
public class SQLFilterIT {

    /**
     * Verifies the case where an IsIn and IsNot in filter has a comparison values of different object classes. This
     * forces {@link SQLFilters} to split up IN or NOT IN conditions, replacing them with OR conditions. Simplified
     * example: "x IN (0,1)" would become "x = 0 OR x = 1"
     */
    @Test
    public void testNonUniformCollection() {
        Embedding embedding0 = TestData.randomEmbedding();
        Embedding embedding1 = TestData.randomEmbedding();

        TextSegment textSegment0 = TestData.randomTextSegment();
        TextSegment textSegment1 = TestData.randomTextSegment();

        textSegment0.metadata().put("x", Long.MAX_VALUE);
        textSegment1.metadata().put("x", Long.MAX_VALUE - 1);

        // Comparison values of different object types. Note that it contains value matching the "x" value of
        // textSegment0's metadata.
        Collection<?> comparisonValues = Stream.of(
                        Integer.MIN_VALUE,
                        textSegment0.metadata().getLong("x"),
                        Float.MIN_VALUE,
                        Double.MAX_VALUE,
                        "test")
                .collect(Collectors.toList());
        IsIn isIn = new IsIn("x", comparisonValues);
        IsNotIn isNotIn = new IsNotIn("x", comparisonValues);

        OracleEmbeddingStore oracleEmbeddingStore = CommonTestOperations.newEmbeddingStore();

        List<String> ids = oracleEmbeddingStore.addAll(
                Arrays.asList(embedding0, embedding1), Arrays.asList(textSegment0, textSegment1));

        List<EmbeddingMatch<TextSegment>> matches0 = oracleEmbeddingStore
                .search(EmbeddingSearchRequest.builder()
                        .queryEmbedding(embedding0)
                        .minScore(0d)
                        .maxResults(2)
                        .filter(isIn) // <-- IS IN filter matches textSegment0
                        .build())
                .matches();

        assertThat(matches0.size()).as(matches0.toString()).isEqualTo(1);
        assertThat(matches0.get(0).embeddingId()).isEqualTo(ids.get(0));
        assertThat(matches0.get(0).embedding()).isEqualTo(embedding0);
        assertThat(matches0.get(0).embedded()).isEqualTo(textSegment0);

        List<EmbeddingMatch<TextSegment>> matches1 = oracleEmbeddingStore
                .search(EmbeddingSearchRequest.builder()
                        .queryEmbedding(embedding0)
                        .minScore(0d)
                        .maxResults(2)
                        .filter(isNotIn) // <-- IS NOT IN filter matches textSegment1
                        .build())
                .matches();

        assertThat(matches1.size()).as(matches1.toString()).isEqualTo(1);
        assertThat(matches1.get(0).embeddingId()).isEqualTo(ids.get(1));
        assertThat(matches1.get(0).embedding()).isEqualTo(embedding1);
        assertThat(matches1.get(0).embedded()).isEqualTo(textSegment1);
    }

    /**
     * Verifies the SQLType that is resolved for each Java object type, along with the SQL expression returned by
     * {@link SQLFilter#toSQL()}.
     */
    @Test
    public void testSQLType() throws SQLException {

        assertThat(SQLFilters.create(new IsEqualTo("x", Integer.MIN_VALUE), (key, type) -> {
                            assertThat(key).isEqualTo("x");
                            assertThat(type).isEqualTo(OracleType.NUMBER);
                            return key;
                        })
                        .toSQL())
                .isEqualTo("NVL(x = ?, false)");
        assertThat(SQLFilters.create(new IsNotEqualTo("x", Long.MAX_VALUE), (key, type) -> {
                            assertThat(key).isEqualTo("x");
                            assertThat(type).isEqualTo(OracleType.NUMBER);
                            return key;
                        })
                        .toSQL())
                .isEqualTo("NVL(x <> ?, true)");
        assertThat(SQLFilters.create(new IsGreaterThan("x", Float.MAX_VALUE), (key, type) -> {
                            assertThat(key).isEqualTo("x");
                            assertThat(type).isEqualTo(OracleType.BINARY_FLOAT); // REAL is 32-bit floating point
                            return key;
                        })
                        .toSQL())
                .isEqualTo("NVL(x > ?, false)");
        assertThat(SQLFilters.create(new IsLessThan("x", Double.MIN_VALUE), (key, type) -> {
                            assertThat(key).isEqualTo("x");
                            assertThat(type).isEqualTo(OracleType.BINARY_DOUBLE); // REAL is 64-bit floating point
                            return key;
                        })
                        .toSQL())
                .isEqualTo("NVL(x < ?, false)");
        assertThat(SQLFilters.create(MetadataFilterBuilder.metadataKey("x").isIn("a", "b"), (key, type) -> {
                            assertThat(key).isEqualTo("x");
                            assertThat(type).isEqualTo(OracleType.CLOB);
                            return key;
                        })
                        .toSQL())
                .isEqualTo("(NVL(DBMS_LOB.COMPARE(x, ?) = 0, false) OR NVL(DBMS_LOB.COMPARE(x, ?) = 0, false))");
        // CLOB is lossless for all Java Strings (assuming the database character set is UTF-8)
        assertThat(SQLFilters.create(MetadataFilterBuilder.metadataKey("x").isNotIn("c", "d"), (key, type) -> {
                            assertThat(key).isEqualTo("x");
                            assertThat(type).isEqualTo(OracleType.CLOB);
                            return key;
                        })
                        .toSQL())
                .isEqualTo("NOT((NVL(DBMS_LOB.COMPARE(x, ?) = 0, false) OR NVL(DBMS_LOB.COMPARE(x, ?) = 0, false)))");
        // CLOB is lossless for all Java Strings (assuming the database character set is UTF-8)

    }
}
