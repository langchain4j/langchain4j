package dev.langchain4j.store.embedding.neo4j;

import static dev.langchain4j.store.embedding.neo4j.Neo4jFilterMapper.UNSUPPORTED_FILTER_TYPE_ERROR;
import static java.util.AbstractMap.SimpleEntry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsGreaterThan;
import dev.langchain4j.store.embedding.filter.comparison.IsGreaterThanOrEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsIn;
import dev.langchain4j.store.embedding.filter.comparison.IsLessThan;
import dev.langchain4j.store.embedding.filter.comparison.IsLessThanOrEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsNotEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsNotIn;
import dev.langchain4j.store.embedding.filter.logical.And;
import dev.langchain4j.store.embedding.filter.logical.Not;
import dev.langchain4j.store.embedding.filter.logical.Or;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class Neo4jFilterMapperTest {

    @Test
    void should_map_equal() {
        IsEqualTo filter = new IsEqualTo("key", "value");
        final SimpleEntry<String, Map> result = new Neo4jFilterMapper().map(filter);
        assertThat(result.getKey()).isEqualTo("n.key = $param_1");
        assertThat(result.getValue()).isEqualTo(Map.of("param_1", "value"));
    }

    @Test
    void should_map_not_equal() {
        IsNotEqualTo filter = new IsNotEqualTo("key", "value");
        final SimpleEntry<String, Map> result = new Neo4jFilterMapper().map(filter);
        assertThat(result.getKey()).isEqualTo("n.key <> $param_1");
        assertThat(result.getValue()).isEqualTo(Map.of("param_1", "value"));
    }

    @Test
    void should_map_is_greater_than() {
        IsGreaterThan filter = new IsGreaterThan("key", 10);
        final SimpleEntry<String, Map> result = new Neo4jFilterMapper().map(filter);
        assertThat(result.getKey()).isEqualTo("n.key > $param_1");
        assertThat(result.getValue()).isEqualTo(Map.of("param_1", 10));
    }

    @Test
    void should_map_is_greater_than_or_equal_to() {
        IsGreaterThanOrEqualTo filter = new IsGreaterThanOrEqualTo("key", 10);
        final SimpleEntry<String, Map> result = new Neo4jFilterMapper().map(filter);
        assertThat(result.getKey()).isEqualTo("n.key >= $param_1");
        assertThat(result.getValue()).isEqualTo(Map.of("param_1", 10));
    }

    @Test
    void should_map_is_less_than() {
        IsLessThan filter = new IsLessThan("key", 10);
        final SimpleEntry<String, Map> result = new Neo4jFilterMapper().map(filter);
        assertThat(result.getKey()).isEqualTo("n.key < $param_1");
        assertThat(result.getValue()).isEqualTo(Map.of("param_1", 10));
    }

    @Test
    void should_map_is_less_than_or_equal_to() {
        IsLessThanOrEqualTo filter = new IsLessThanOrEqualTo("key", 10);
        final SimpleEntry<String, Map> result = new Neo4jFilterMapper().map(filter);
        assertThat(result.getKey()).isEqualTo("n.key <= $param_1");
        assertThat(result.getValue()).isEqualTo(Map.of("param_1", 10));
    }

    @Test
    void should_map_is_in() {
        final Set<Integer> value = Set.of(1, 2, 3);
        IsIn filter = new IsIn("key", value);
        final SimpleEntry<String, Map> result = new Neo4jFilterMapper().map(filter);
        assertThat(result.getKey()).isEqualTo("n.key IN $param_1");
        assertThat(result.getValue()).isEqualTo(Map.of("param_1", value));
    }

    @Test
    void should_map_is_not_in() {
        final Set<Integer> value = Set.of(1, 2, 3);
        IsNotIn filter = new IsNotIn("key", value);
        final SimpleEntry<String, Map> result = new Neo4jFilterMapper().map(filter);
        assertThat(result.getKey()).isEqualTo("NOT (n.key IN $param_1)");
        assertThat(result.getValue()).isEqualTo(Map.of("param_1", value));
    }

    @Test
    void should_map_and() {
        And filter = new And(new IsEqualTo("key1", "value1"), new IsEqualTo("key2", "value2"));
        final SimpleEntry<String, Map> result = new Neo4jFilterMapper().map(filter);
        assertThat(result.getKey()).isEqualTo("(n.key1 = $param_1) AND (n.key2 = $param_2)");
        assertThat(result.getValue()).isEqualTo(Map.of("param_1", "value1", "param_2", "value2"));
    }

    @Test
    void should_map_or() {
        Or filter = new Or(new IsEqualTo("key1", "value1"), new IsEqualTo("key2", "value2"));
        final SimpleEntry<String, Map> result = new Neo4jFilterMapper().map(filter);
        assertThat(result.getKey()).isEqualTo("(n.key1 = $param_1) OR (n.key2 = $param_2)");
        assertThat(result.getValue()).isEqualTo(Map.of("param_1", "value1", "param_2", "value2"));
    }

    @Test
    void should_map_or_not_and() {
        final Set<String> valueKey3 = Set.of("1", "2");
        Or filter = new Or(
                new And(new IsEqualTo("key1", "value1"), new IsGreaterThan("key2", "value2")),
                new Not(new And(new IsIn("key3", valueKey3), new IsLessThan("key4", "value4"))));
        final SimpleEntry<String, Map> result = new Neo4jFilterMapper().map(filter);
        assertThat(result.getKey())
                .isEqualTo(
                        "((n.key1 = $param_1) AND (n.key2 > $param_2)) OR (NOT ((n.key3 IN $param_3) AND (n.key4 < $param_4)))");
        assertThat(result.getValue())
                .isEqualTo(Map.of("param_1", "value1", "param_2", "value2", "param_3", valueKey3, "param_4", "value4"));
    }

    @Test
    void should_correctly_sanitize_key() {
        IsEqualTo filter = new IsEqualTo("k\\ ` ey", "value");
        final SimpleEntry<String, Map> result = new Neo4jFilterMapper().map(filter);
        assertThat(result.getKey()).isEqualTo("n.`k\\ `` ey` = $param_1");
        assertThat(result.getValue()).isEqualTo(Map.of("param_1", "value"));
    }

    @Test
    void should_throws_unsupported_filter_error() {
        MockFilter filter = new MockFilter();
        try {
            new Neo4jFilterMapper().map(filter);
            fail("Should fail due to unsupported filter");
        } catch (Exception e) {
            assertThat(e.getMessage()).contains(UNSUPPORTED_FILTER_TYPE_ERROR);
        }
    }

    private static class MockFilter implements Filter {

        @Override
        public boolean test(final Object object) {
            return false;
        }

        @Override
        public Filter and(final Filter filter) {
            return Filter.super.and(filter);
        }

        @Override
        public Filter or(final Filter filter) {
            return Filter.super.or(filter);
        }
    }
}
