package dev.langchain4j.store.embedding.qdrant;

import dev.langchain4j.store.embedding.filter.comparison.*;
import io.qdrant.client.grpc.Points;
import dev.langchain4j.store.embedding.filter.Filter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Arrays;

class QdrantFilterConverterTest {

    @Test
    void testIsEqualToFilter() {
        Filter filter = new IsEqualTo("num-value", 5);
        Points.Filter convertedFilter = QdrantFilterConverter.convertExpression(filter);
        assertNotNull(convertedFilter);
        assertEquals(1, convertedFilter.getMustCount());
        assertEquals("num-value", convertedFilter.getMust(0).getField().getKey());
        assertEquals(5, convertedFilter.getMust(0).getField().getMatch().getInteger());

        filter = new IsEqualTo("str-value", "value");
        convertedFilter = QdrantFilterConverter.convertExpression(filter);
        assertNotNull(convertedFilter);
        assertEquals(1, convertedFilter.getMustCount());
        assertEquals("str-value", convertedFilter.getMust(0).getField().getKey());
        assertEquals("value", convertedFilter.getMust(0).getField().getMatch().getKeyword());

        filter = new IsEqualTo("bool-value", true);
        convertedFilter = QdrantFilterConverter.convertExpression(filter);
        assertNotNull(convertedFilter);
        assertEquals(1, convertedFilter.getMustCount());
        assertEquals("bool-value", convertedFilter.getMust(0).getField().getKey());
        assertEquals(true, convertedFilter.getMust(0).getField().getMatch().getBoolean());
    }

    @Test
    void testIsNotEqualToFilter() {
        Filter filter = new IsNotEqualTo("num-value", 5);
        Points.Filter convertedFilter = QdrantFilterConverter.convertExpression(filter);
        assertNotNull(convertedFilter);
        assertEquals(1, convertedFilter.getMustCount());
        assertEquals("num-value", convertedFilter.getMust(0).getFilter().getMustNot(0).getField().getKey());
        assertEquals(5, convertedFilter.getMust(0).getFilter().getMustNot(0).getField().getMatch().getInteger());

        filter = new IsNotEqualTo("str-value", "value");
        convertedFilter = QdrantFilterConverter.convertExpression(filter);
        assertNotNull(convertedFilter);
        assertEquals(1, convertedFilter.getMustCount());
        assertEquals("str-value", convertedFilter.getMust(0).getFilter().getMustNot(0).getField().getKey());
        assertEquals("value", convertedFilter.getMust(0).getFilter().getMustNot(0).getField().getMatch().getKeyword());

        filter = new IsNotEqualTo("bool-value", true);
        convertedFilter = QdrantFilterConverter.convertExpression(filter);
        assertNotNull(convertedFilter);
        assertEquals(1, convertedFilter.getMustCount());
        assertEquals("bool-value", convertedFilter.getMust(0).getFilter().getMustNot(0).getField().getKey());
        assertEquals(true, convertedFilter.getMust(0).getFilter().getMustNot(0).getField().getMatch().getBoolean());
    }

    @Test
    void testIsGreaterThanFilter() {
        Filter filter = new IsGreaterThan("key", 1);
        Points.Filter convertedFilter = QdrantFilterConverter.convertExpression(filter);

        assertNotNull(convertedFilter);
        assertEquals(1, convertedFilter.getMustCount());
        assertEquals(convertedFilter.getMust(0).getField().getRange().getGt(), 1);
    }

    @Test
    void testIsLessThanFilter() {
        Filter filter = new IsLessThan("key", 10);
        Points.Filter convertedFilter = QdrantFilterConverter.convertExpression(filter);

        assertNotNull(convertedFilter);
        assertEquals(1, convertedFilter.getMustCount());
        assertEquals(convertedFilter.getMust(0).getField().getRange().getLt(), 10);
    }

    @Test
    void testIsGreaterThanOrEqualToFilter() {
        Filter filter = new IsGreaterThanOrEqualTo("key", 1);
        Points.Filter convertedFilter = QdrantFilterConverter.convertExpression(filter);

        assertNotNull(convertedFilter);
        assertEquals(1, convertedFilter.getMustCount());
        assertEquals(convertedFilter.getMust(0).getField().getRange().getGte(), 1);
    }

    @Test
    void testIsLessThanOrEqualToFilter() {
        Filter filter = new IsLessThanOrEqualTo("key", 10);
        Points.Filter convertedFilter = QdrantFilterConverter.convertExpression(filter);

        assertNotNull(convertedFilter);
        assertEquals(1, convertedFilter.getMustCount());
        assertEquals(convertedFilter.getMust(0).getField().getRange().getLte(), 10);
    }

    @Test
    void testInFilter() {
        Filter filter = new IsIn("key", Arrays.asList(1, 2, 3));
        Points.Filter convertedFilter = QdrantFilterConverter.convertExpression(filter);
        assertNotNull(convertedFilter);
        assertEquals(1, convertedFilter.getMustCount());
        assertEquals(3, convertedFilter.getMust(0).getField().getMatch().getIntegers().getIntegersCount());

        filter = new IsIn("key", Arrays.asList("a", "b", "c"));
        convertedFilter = QdrantFilterConverter.convertExpression(filter);
        assertNotNull(convertedFilter);
        assertEquals(1, convertedFilter.getMustCount());
        assertEquals(3, convertedFilter.getMust(0).getField().getMatch().getKeywords().getStringsCount());
    }

    @Test
    void testNInFilter() {
        Filter filter = new IsNotIn("key", Arrays.asList(1, 2, 3, 4));
        Points.Filter convertedFilter = QdrantFilterConverter.convertExpression(filter);
        assertNotNull(convertedFilter);
        assertEquals(1, convertedFilter.getMustCount());
        assertEquals(4, convertedFilter.getMust(0).getField().getMatch().getExceptIntegers().getIntegersCount());

        filter = new IsNotIn("key", Arrays.asList("a", "b", "c", "k"));
        convertedFilter = QdrantFilterConverter.convertExpression(filter);
        assertNotNull(convertedFilter);
        assertEquals(1, convertedFilter.getMustCount());
        assertEquals(4, convertedFilter.getMust(0).getField().getMatch().getExceptKeywords().getStringsCount());
    }
}
