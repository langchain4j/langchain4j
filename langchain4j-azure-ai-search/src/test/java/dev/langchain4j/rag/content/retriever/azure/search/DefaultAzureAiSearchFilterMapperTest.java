package dev.langchain4j.rag.content.retriever.azure.search;

import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.*;
import dev.langchain4j.store.embedding.filter.logical.And;
import dev.langchain4j.store.embedding.filter.logical.Or;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

class DefaultAzureAiSearchFilterMapperTest {

    private final AzureAiSearchFilterMapper mapper = new DefaultAzureAiSearchFilterMapper();
    
    @Test
    void map_nullFilter() {
        String result = mapper.map(null);
        assertThat(result).isEmpty();
    }

    @Test
    void map_handlesIsGreaterThan() {
        IsGreaterThan isGreaterThanFilter = new IsGreaterThan("key1", "value1");
        String result = mapper.map(isGreaterThanFilter);
        assertThat(result).isEqualTo("metadata/attributes/any(k: k/key eq 'key1' and k/value gt 'value1')");
    }

    @Test
    void map_handlesIsGreaterThanOrEqualTo() {
        IsGreaterThanOrEqualTo isGreaterThanOrEqualToFilter = new IsGreaterThanOrEqualTo("key1", "value1");
        String result = mapper.map(isGreaterThanOrEqualToFilter);
        assertThat(result).isEqualTo("metadata/attributes/any(k: k/key eq 'key1' and k/value ge 'value1')");
    }

    @Test
    void map_handlesIsLessThan() {
        IsLessThan isLessThanFilter = new IsLessThan("key1", "value1");
        String result = mapper.map(isLessThanFilter);
        assertThat(result).isEqualTo("metadata/attributes/any(k: k/key eq 'key1' and k/value lt 'value1')");
    }

    @Test
    void map_handlesIsLessThanOrEqualTo() {
        IsLessThanOrEqualTo isLessThanOrEqualToFilter = new IsLessThanOrEqualTo("key1", "value1");
        String result = mapper.map(isLessThanOrEqualToFilter);
        assertThat(result).isEqualTo("metadata/attributes/any(k: k/key eq 'key1' and k/value le 'value1')");
    }

    @Test
    void map_handlesIsIn() {
        IsIn isInFilter = new IsIn("key1", Arrays.asList("value1", "value2"));
        String result = mapper.map(isInFilter);
        assertThat(result).isEqualTo("metadata/attributes/any(k: k/key eq 'key1' and search.in(k/value, ('value1, value2')))");
    }

    @Test
    void map_handlesIsNotIn() {
        IsNotIn isNotInFilter = new IsNotIn("key1", Arrays.asList("value1", "value2"));
        String result = mapper.map(isNotInFilter);
        assertThat(result).isEqualTo("(not metadata/attributes/any(k: k/key eq 'key1' and search.in(k/value, ('value1, value2'))))");
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
        assertThat(result).isEqualTo("(metadata/attributes/any(k: k/key eq 'key1' and k/value eq 'value1') and ((not metadata/attributes/any(k: k/key eq 'key2' and search.in(k/value, ('value2, value3')))) or metadata/attributes/any(k: k/key eq 'key3' and k/value gt '100')))");
    }

    @Test
    void mapComparisonFilter_throwsExceptionForUnsupportedFilter() {
        Filter unsupportedFilter = new Filter() {
            @Override
            public boolean test(Object object) {
                return false;
            }
        };
        assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> mapper.map(unsupportedFilter));
    }
}
