package dev.langchain4j.store.embedding.oracle;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import dev.langchain4j.store.embedding.filter.comparison.*;
import oracle.jdbc.OracleType;
import org.junit.jupiter.api.Test;

import java.sql.*;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        Collection<?> comparisonValues =
                Stream.of(
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
                Arrays.asList(embedding0, embedding1),
                Arrays.asList(textSegment0, textSegment1));

        List<EmbeddingMatch<TextSegment>> matches0 =
                oracleEmbeddingStore.search(EmbeddingSearchRequest.builder()
                    .queryEmbedding(embedding0)
                    .minScore(0d)
                    .maxResults(2)
                    .filter(isIn) // <-- IS IN filter matches textSegment0
                    .build())
                    .matches();

        assertEquals(1, matches0.size(), matches0.toString());
        assertEquals(ids.get(0), matches0.get(0).embeddingId());
        assertEquals(embedding0, matches0.get(0).embedding());
        assertEquals(textSegment0, matches0.get(0).embedded());

        List<EmbeddingMatch<TextSegment>> matches1 =
                oracleEmbeddingStore.search(EmbeddingSearchRequest.builder()
                                .queryEmbedding(embedding0)
                                .minScore(0d)
                                .maxResults(2)
                                .filter(isNotIn) // <-- IS NOT IN filter matches textSegment1
                                .build())
                        .matches();

        assertEquals(1, matches1.size(), matches1.toString());
        assertEquals(ids.get(1), matches1.get(0).embeddingId());
        assertEquals(embedding1, matches1.get(0).embedding());
        assertEquals(textSegment1, matches1.get(0).embedded());
    }

    /**
     * Verifies the SQLType that is resolved for each Java object type, along with the SQL expression returned by
     * {@link SQLFilter#toSQL()}.
     */
    @Test
    public void testSQLType() throws SQLException {

        assertEquals(
                "NVL(x = ?, false)",
                SQLFilters.create(new IsEqualTo("x", Integer.MIN_VALUE), (key, type) -> {
                    assertEquals("x", key);
                    assertEquals(OracleType.NUMBER, type);
                    return key;
                }).toSQL());
        verifyLosslessConversion(OracleType.NUMBER, Integer.MIN_VALUE);

        assertEquals(
                "NVL(x <> ?, true)",
                SQLFilters.create(new IsNotEqualTo("x", Long.MAX_VALUE), (key, type) -> {
                    assertEquals("x", key);
                    assertEquals(OracleType.NUMBER, type);
                    return key;
                }).toSQL());
        verifyLosslessConversion(OracleType.NUMBER, Long.MAX_VALUE);

        assertEquals(
                "NVL(x > ?, false)",
                SQLFilters.create(new IsGreaterThan("x", Float.MAX_VALUE), (key, type) -> {
                    assertEquals("x", key);
                    assertEquals(OracleType.BINARY_FLOAT, type); // REAL is 32-bit floating point
                    return key;
                }).toSQL());
        verifyLosslessConversion(OracleType.BINARY_FLOAT, Float.MAX_VALUE);

        assertEquals(
                "NVL(x < ?, false)",
                SQLFilters.create(new IsLessThan("x", Double.MIN_VALUE), (key, type) -> {
                    assertEquals("x", key);
                    assertEquals(OracleType.BINARY_DOUBLE, type); // FLOAT is 64-bit floating point
                    return key;
                }).toSQL());
        verifyLosslessConversion(OracleType.BINARY_DOUBLE, Double.MIN_VALUE);

        assertEquals(
                "(NVL(DBMS_LOB.COMPARE(x, ?) = 0, false) OR NVL(DBMS_LOB.COMPARE(x, ?) = 0, false))",
                SQLFilters.create(MetadataFilterBuilder.metadataKey("x").isIn("a", "b"), (key, type) -> {
                    assertEquals("x", key);
                    assertEquals(OracleType.CLOB, type);
                    return key;
                }).toSQL());
        // CLOB is lossless for all Java Strings (assuming the database character set is UTF-8)

        assertEquals(
                "NOT((NVL(DBMS_LOB.COMPARE(x, ?) = 0, false) OR NVL(DBMS_LOB.COMPARE(x, ?) = 0, false)))",
                SQLFilters.create(MetadataFilterBuilder.metadataKey("x").isNotIn("c", "d"), (key, type) -> {
                    assertEquals("x", key);
                    assertEquals(OracleType.CLOB, type);
                    return key;
                }).toSQL());
        // CLOB is lossless for all Java Strings (assuming the database character set is UTF-8)
    }

    /**
     * Verifies that a Java to SQL conversion is lossless. A PreparedStatement converts Java objects into SQL data
     * types, and a ResultSet converts SQL data types into Java objects. A conversion is lossless if, after converting a
     * Java object into SQL data, that SQL data can be converted back into a Java object which is equal to the original,
     * according to {@link Object#equals(Object)}.
     *
     * @param sqlType A SQL data type. Not null.
     * @param javaObject Java object to convert into the SQL data type. Not null.
     */
    private void verifyLosslessConversion(SQLType sqlType, Object javaObject) throws SQLException {
        try (Connection connection = CommonTestOperations.getDataSource().getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement("SELECT ? FROM sys.dual")) {

            preparedStatement.setObject(1, javaObject, sqlType);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                assertTrue(resultSet.next());
                assertEquals(javaObject, resultSet.getObject(1, javaObject.getClass()));
            }
        }
    }

}