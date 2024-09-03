package dev.langchain4j.store.embedding.chroma;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsGreaterThan;
import dev.langchain4j.store.embedding.filter.comparison.IsGreaterThanOrEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsIn;
import dev.langchain4j.store.embedding.filter.comparison.IsLessThan;
import dev.langchain4j.store.embedding.filter.comparison.IsLessThanOrEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsNotEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsNotIn;
import dev.langchain4j.store.embedding.filter.logical.And;
import dev.langchain4j.store.embedding.filter.logical.Or;
import java.util.HashSet;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ChromaMetadataFilterMapperTest {

    @Test
    void should_map_equal() {
        IsEqualTo filter = new IsEqualTo("key", "value");
        Map<String, Object> result = ChromaMetadataFilterMapper.map(filter);
        assertThat(result).isEqualTo(singletonMap("key", "value"));
    }

    @Test
    void should_map_not_equal() {
        IsNotEqualTo filter = new IsNotEqualTo("key", "value");
        Map<String, Object> result = ChromaMetadataFilterMapper.map(filter);
        assertThat(result).isEqualTo(singletonMap("key", singletonMap("$ne", "value")));
    }

    @Test
    void should_map_is_greater_than() {
        IsGreaterThan filter = new IsGreaterThan("key", 10);
        Map<String, Object> result = ChromaMetadataFilterMapper.map(filter);
        assertThat(result).isEqualTo(singletonMap("key", singletonMap("$gt", 10)));
    }

    @Test
    void should_map_is_greater_than_or_equal_to() {
        IsGreaterThanOrEqualTo filter = new IsGreaterThanOrEqualTo("key", 10);
        Map<String, Object> result = ChromaMetadataFilterMapper.map(filter);
        assertThat(result).isEqualTo(singletonMap("key", singletonMap("$gte", 10)));
    }

    @Test
    void should_map_is_less_than() {
        IsLessThan filter = new IsLessThan("key", 10);
        Map<String, Object> result = ChromaMetadataFilterMapper.map(filter);
        assertThat(result).isEqualTo(singletonMap("key", singletonMap("$lt", 10)));
    }

    @Test
    void should_map_is_less_than_or_equal_to() {
        IsLessThanOrEqualTo filter = new IsLessThanOrEqualTo("key", 10);
        Map<String, Object> result = ChromaMetadataFilterMapper.map(filter);
        assertThat(result).isEqualTo(singletonMap("key", singletonMap("$lte", 10)));
    }

    @Test
    void should_map_is_in() {
        IsIn filter = new IsIn("key", asList(1, 2, 3));
        Map<String, Object> result = ChromaMetadataFilterMapper.map(filter);
        assertThat(result).isEqualTo(singletonMap("key", singletonMap("$in", new HashSet<>(asList(1, 2, 3)))));
    }

    @Test
    void should_map_is_not_in() {
        IsNotIn filter = new IsNotIn("key", asList(1, 2, 3));
        Map<String, Object> result = ChromaMetadataFilterMapper.map(filter);
        assertThat(result).isEqualTo(singletonMap("key", singletonMap("$nin", new HashSet<>(asList(1, 2, 3)))));
    }

    @Test
    void should_map_and() {
        And filter = new And(new IsEqualTo("key1", "value1"), new IsEqualTo("key2", "value2"));
        Map<String, Object> result = ChromaMetadataFilterMapper.map(filter);
        assertThat(result)
            .isEqualTo(singletonMap("$and", asList(singletonMap("key1", "value1"), singletonMap("key2", "value2"))));
    }

    @Test
    void should_map_or() {
        Or filter = new Or(new IsEqualTo("key1", "value1"), new IsEqualTo("key2", "value2"));
        Map<String, Object> result = ChromaMetadataFilterMapper.map(filter);
        assertThat(result)
            .isEqualTo(singletonMap("$or", asList(singletonMap("key1", "value1"), singletonMap("key2", "value2"))));
    }
}
