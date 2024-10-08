package dev.langchain4j.rag.content.retriever.azure.search;

import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.*;
import dev.langchain4j.store.embedding.filter.logical.And;
import dev.langchain4j.store.embedding.filter.logical.Or;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefaultAzureAiSearchFilterMapperTest {

    private final AzureAiSearchFilterMapper mapper = new DefaultAzureAiSearchFilterMapper();
    
    @Test
    void map_nullFilter() {
        String result = mapper.map(null);
        assertEquals("", result);
    }

    @Test
    void map_handlesIsGreaterThan() {
        IsGreaterThan isGreaterThanFilter = new IsGreaterThan("key1", "value1");
        String result = mapper.map(isGreaterThanFilter);
        assertEquals("metadata/attributes/any(k: k/key eq 'key1' and k/value gt 'value1')", result);
    }

    @Test
    void map_handlesIsGreaterThanOrEqualTo() {
        IsGreaterThanOrEqualTo isGreaterThanOrEqualToFilter = new IsGreaterThanOrEqualTo("key1", "value1");
        String result = mapper.map(isGreaterThanOrEqualToFilter);
        assertEquals("metadata/attributes/any(k: k/key eq 'key1' and k/value ge 'value1')", result);
    }

    @Test
    void map_handlesIsLessThan() {
        IsLessThan isLessThanFilter = new IsLessThan("key1", "value1");
        String result = mapper.map(isLessThanFilter);
        assertEquals("metadata/attributes/any(k: k/key eq 'key1' and k/value lt 'value1')", result);
    }

    @Test
    void map_handlesIsLessThanOrEqualTo() {
        IsLessThanOrEqualTo isLessThanOrEqualToFilter = new IsLessThanOrEqualTo("key1", "value1");
        String result = mapper.map(isLessThanOrEqualToFilter);
        assertEquals("metadata/attributes/any(k: k/key eq 'key1' and k/value le 'value1')", result);
    }

    @Test
    void map_handlesIsIn() {
        IsIn isInFilter = new IsIn("key1", Arrays.asList("value1", "value2"));
        String result = mapper.map(isInFilter);
        assertEquals("metadata/attributes/any(k: k/key eq 'key1' and search.in(k/value, ('value1, value2')))", result);
    }

    @Test
    void map_handlesIsNotIn() {
        IsNotIn isNotInFilter = new IsNotIn("key1", Arrays.asList("value1", "value2"));
        String result = mapper.map(isNotInFilter);
        assertEquals("(not metadata/attributes/any(k: k/key eq 'key1' and search.in(k/value, ('value1, value2'))))", result);
    }

    @Test
    void map_handlesComplexFilter() {
        And filter = new And(
                new IsEqualTo("key1", "value1"),
                new Or(
                        new IsNotIn("key2", Arrays.asList("value2", "value3")),
                        new IsGreaterThan("key3", "100"))
        );
        String result = mapper.map(filter);
        assertEquals("(metadata/attributes/any(k: k/key eq 'key1' and k/value eq 'value1') and ((not metadata/attributes/any(k: k/key eq 'key2' and search.in(k/value, ('value2, value3')))) or metadata/attributes/any(k: k/key eq 'key3' and k/value gt '100')))", result);
    }

    @Test
    void mapComparisonFilter_throwsExceptionForUnsupportedFilter() {
        Filter unsupportedFilter = new Filter() {
            @Override
            public boolean test(Object object) {
                return false;
            }
        };
        assertThrows(IllegalArgumentException.class, () -> mapper.map(unsupportedFilter));
    }
}