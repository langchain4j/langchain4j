package dev.langchain4j.store.embedding.astradb;

import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsNotEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsGreaterThan;
import dev.langchain4j.store.embedding.filter.comparison.IsGreaterThanOrEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsLessThan;
import dev.langchain4j.store.embedding.filter.comparison.IsLessThanOrEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsIn;
import dev.langchain4j.store.embedding.filter.comparison.IsNotIn;
import dev.langchain4j.store.embedding.filter.logical.And;
import dev.langchain4j.store.embedding.filter.logical.Not;
import dev.langchain4j.store.embedding.filter.logical.Or;
import io.stargate.sdk.data.domain.query.Filter;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AstraDbFilterMapperTest {

    @Test
    void should_map_equal() {
        IsEqualTo filter = new IsEqualTo("age", 25);
        Filter result = AstraDbFilterMapper.map(filter);
        assertThat(result.getFilter()).isEqualTo(Map.of("age", 25));
    }

    @Test
    void should_map_not_equal() {
        IsNotEqualTo filter = new IsNotEqualTo("age", 25);
        Filter result = AstraDbFilterMapper.map(filter);
        assertThat(result.getFilter()).isEqualTo(Map.of("age", Map.of("$ne", 25)));
    }

    @Test
    void should_map_greater_than() {
        IsGreaterThan filter = new IsGreaterThan("age", 25);
        Filter result = AstraDbFilterMapper.map(filter);
        assertThat(result.getFilter()).isEqualTo(Map.of("age", Map.of("$gt", 25)));
    }

    @Test
    void should_map_greater_than_or_equal_to() {
        IsGreaterThanOrEqualTo filter = new IsGreaterThanOrEqualTo("age", 25);
        Filter result = AstraDbFilterMapper.map(filter);
        assertThat(result.getFilter()).isEqualTo(Map.of("age", Map.of("$gte", 25)));
    }

    @Test
    void should_map_less_than() {
        IsLessThan filter = new IsLessThan("age", 25);
        Filter result = AstraDbFilterMapper.map(filter);
        assertThat(result.getFilter()).isEqualTo(Map.of("age", Map.of("$lt", 25)));
    }

    @Test
    void should_map_less_than_or_equal_to() {
        IsLessThanOrEqualTo filter = new IsLessThanOrEqualTo("age", 25);
        Filter result = AstraDbFilterMapper.map(filter);
        assertThat(result.getFilter()).isEqualTo(Map.of("age", Map.of("$lte", 25)));
    }

    @Test
    void should_map_in() {
        IsIn filter = new IsIn("status", Arrays.asList("active", "pending"));
        Filter result = AstraDbFilterMapper.map(filter);
        assertThat(result.getFilter()).containsKey("status");
        Map<String, Object> statusMap = (Map<String, Object>) result.getFilter().get("status");
        assertThat(statusMap).containsKey("$in");
        assertThat((List<Object>) statusMap.get("$in")).containsExactlyInAnyOrder("active", "pending");
    }
    
    @Test
    void should_map_not_in() {
        IsNotIn filter = new IsNotIn("status", Arrays.asList("deleted", "archived"));
        Filter result = AstraDbFilterMapper.map(filter);
        assertThat(result.getFilter()).containsKey("status");
        Map<String, Object> statusMap = (Map<String, Object>) result.getFilter().get("status");
        assertThat(statusMap).containsKey("$nin");
        assertThat((List<Object>) statusMap.get("$nin")).containsExactlyInAnyOrder("deleted", "archived");
    }

    @Test
    void should_map_and() {
        IsGreaterThan left = new IsGreaterThan("age", 18);
        IsLessThan right = new IsLessThan("age", 65);
        And filter = new And(left, right);
        Filter result = AstraDbFilterMapper.map(filter);
        assertThat(result.getFilter()).containsKey("$and");
        List<?> andList = (List<?>) result.getFilter().get("$and");
        assertThat(andList).hasSize(2);
    }

    @Test
    void should_map_or() {
        IsEqualTo left = new IsEqualTo("status", "active");
        IsEqualTo right = new IsEqualTo("status", "pending");
        Or filter = new Or(left, right);
        Filter result = AstraDbFilterMapper.map(filter);
        assertThat(result.getFilter()).containsKey("$or");
        List<?> orList = (List<?>) result.getFilter().get("$or");
        assertThat(orList).hasSize(2);
    }

    @Test
    void should_map_not() {
        IsEqualTo inner = new IsEqualTo("status", "inactive");
        Not filter = new Not(inner);
        Filter result = AstraDbFilterMapper.map(filter);
        assertThat(result.getFilter()).containsKey("$not");
    }

    @Test
    void should_throw_for_unknown_filter_type() {
        assertThatThrownBy(() -> AstraDbFilterMapper.map(new dev.langchain4j.store.embedding.filter.Filter() {
            @Override
            public boolean test(Object object) {
                return false;
            }
        }))
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining("Unsupported filter type");
    }
}
