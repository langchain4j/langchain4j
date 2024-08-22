package dev.langchain4j.store.embedding.oracle;

import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import dev.langchain4j.store.embedding.filter.comparison.*;
import org.junit.jupiter.api.Test;

import java.sql.JDBCType;
import java.util.Collection;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that {@link SQLFilter} behaves as specified in its JavaDoc. The
 * {@link dev.langchain4j.store.embedding.EmbeddingStoreWithFilteringIT} test already covers many cases. This test class
 * covers some additional behavior which that test might not check for.
 */
public class SQLFilterTest {

    /**
     * Verifies the case where an IsIn and IsNot in filter has a comparison values of different object classes.
     */
    @Test
    public void testNonUniformCollection() {
        Collection<?> objects = Stream.of(
                Integer.MIN_VALUE, Long.MAX_VALUE, Float.MIN_VALUE, Double.MAX_VALUE, "test")
                .collect(Collectors.toList());

        verifyNonUniformCollectionException(new IsIn("x", objects));
        verifyNonUniformCollectionException(new IsNotIn("x", objects));
    }

    private void verifyNonUniformCollectionException(Filter filter) {
        String message = assertThrows(
                IllegalArgumentException.class,
                () -> SQLFilters.create(filter, (key, type) -> ""))
                .getMessage();

        Collection<?> objects = filter instanceof IsIn
                ? ((IsIn)filter).comparisonValues()
                : ((IsNotIn)filter).comparisonValues(); // <-- Need to add a new case if testing a new Filter class

        for (Object object : objects) {
            assertTrue(message.contains(object == null ? "null" : object.getClass().getSimpleName()));
        }
    }

    /**
     * Verifies the SQLType that is resolved for each Java object type, along with the SQL expression returned by
     * {@link SQLFilter#toSQL()}.
     */
    @Test
    public void testSQLType() {
        assertEquals(
                "NVL(x = ?, false)",
                SQLFilters.create(new IsEqualTo("x", Integer.MIN_VALUE), (key, type) -> {
                    assertEquals("x", key);
                    assertEquals(JDBCType.INTEGER, type);
                    return key;
                }).toSQL());

        assertEquals(
                "NVL(x <> ?, true)",
                SQLFilters.create(new IsNotEqualTo("x", Long.MAX_VALUE), (key, type) -> {
                    assertEquals("x", key);
                    assertEquals(JDBCType.NUMERIC, type);
                    return key;
                }).toSQL());

        assertEquals(
                "NVL(x > ?, false)",
                SQLFilters.create(new IsGreaterThan("x", 0.0f), (key, type) -> {
                    assertEquals("x", key);
                    assertEquals(JDBCType.REAL, type); // REAL is 32-bit floating point
                    return key;
                }).toSQL());

        assertEquals(
                "NVL(x < ?, false)",
                SQLFilters.create(new IsLessThan("x", 0.0d), (key, type) -> {
                    assertEquals("x", key);
                    assertEquals(JDBCType.FLOAT, type); // FLOAT is 64-bit floating point
                    return key;
                }).toSQL());

        assertEquals(
                "NVL(x IN (?, ?), false)",
                SQLFilters.create(MetadataFilterBuilder.metadataKey("x").isIn(0, 1), (key, type) -> {
                    assertEquals("x", key);
                    assertEquals(JDBCType.INTEGER, type);
                    return key;
                }).toSQL());

        assertEquals(
                "NVL(x NOT IN (?, ?), true)",
                SQLFilters.create(MetadataFilterBuilder.metadataKey("x").isNotIn("a", "b"), (key, type) -> {
                    assertEquals("x", key);
                    assertEquals(JDBCType.VARCHAR, type);
                    return key;
                }).toSQL());
    }
}