package dev.langchain4j.store.embedding.astradb;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.store.embedding.filter.comparison.ContainsString;
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
import io.stargate.sdk.data.domain.query.Filter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AstraDbFilterMapperTest {

    @Test
    void should_map_equal() {
        assertThat(AstraDbFilterMapper.map(new IsEqualTo("key", "value")).getFilter())
                .isEqualTo(singletonMap("key", "value"));
    }

    @Test
    void should_map_not_equal() {
        assertThat(AstraDbFilterMapper.map(new IsNotEqualTo("key", "value")).getFilter())
                .isEqualTo(singletonMap("key", singletonMap("$ne", "value")));
    }

    @Test
    void should_map_greater_than() {
        assertThat(AstraDbFilterMapper.map(new IsGreaterThan("age", 18)).getFilter())
                .isEqualTo(singletonMap("age", singletonMap("$gt", 18)));
    }

    @Test
    void should_map_greater_than_or_equal_to() {
        assertThat(AstraDbFilterMapper.map(new IsGreaterThanOrEqualTo("age", 18))
                        .getFilter())
                .isEqualTo(singletonMap("age", singletonMap("$gte", 18)));
    }

    @Test
    void should_map_less_than() {
        assertThat(AstraDbFilterMapper.map(new IsLessThan("age", 65)).getFilter())
                .isEqualTo(singletonMap("age", singletonMap("$lt", 65)));
    }

    @Test
    void should_map_less_than_or_equal_to() {
        assertThat(AstraDbFilterMapper.map(new IsLessThanOrEqualTo("age", 65)).getFilter())
                .isEqualTo(singletonMap("age", singletonMap("$lte", 65)));
    }

    @Test
    void should_map_in_filters() {
        Map<String, Object> inFilter = AstraDbFilterMapper.map(new IsIn("status", asList("active", "pending")))
                .getFilter();
        assertThat(operatorValues(inFilter, "status", "$in")).containsExactlyInAnyOrder("active", "pending");

        Map<String, Object> notInFilter = AstraDbFilterMapper.map(new IsNotIn("status", asList("deleted", "archived")))
                .getFilter();
        assertThat(operatorValues(notInFilter, "status", "$nin")).containsExactlyInAnyOrder("deleted", "archived");
    }

    @Test
    void should_map_logical_filters() {
        Filter andFilter =
                AstraDbFilterMapper.map(new And(new IsEqualTo("status", "active"), new IsGreaterThan("age", 18)));

        assertThat(andFilter.getFilter())
                .isEqualTo(singletonMap(
                        "$and",
                        asList(singletonMap("status", "active"), singletonMap("age", singletonMap("$gt", 18)))));

        Filter orFilter =
                AstraDbFilterMapper.map(new Or(new IsEqualTo("status", "active"), new IsEqualTo("status", "pending")));

        assertThat(orFilter.getFilter())
                .isEqualTo(singletonMap(
                        "$or", asList(singletonMap("status", "active"), singletonMap("status", "pending"))));

        Filter notFilter = AstraDbFilterMapper.map(new Not(new IsEqualTo("status", "inactive")));

        assertThat(notFilter.getFilter()).isEqualTo(singletonMap("$not", singletonMap("status", "inactive")));

        Filter notRangeFilter = AstraDbFilterMapper.map(new Not(new IsGreaterThan("age", 18)));

        assertThat(notRangeFilter.getFilter())
                .isEqualTo(singletonMap("$not", singletonMap("age", singletonMap("$gt", 18))));

        Filter notAndFilter = AstraDbFilterMapper.map(
                new Not(new And(new IsEqualTo("status", "inactive"), new IsLessThan("age", 18))));

        assertThat(notAndFilter.getFilter())
                .isEqualTo(singletonMap(
                        "$not",
                        singletonMap(
                                "$and",
                                asList(
                                        singletonMap("status", "inactive"),
                                        singletonMap("age", singletonMap("$lt", 18))))));
    }

    @Test
    void should_escape_field_names_for_filters() {
        assertThat(AstraDbFilterMapper.map(new IsLessThan("costs.price.usd", 300))
                        .getFilter())
                .isEqualTo(singletonMap("costs&.price&.usd", singletonMap("$lt", 300)));
        assertThat(AstraDbFilterMapper.map(new IsEqualTo("areas.r&d", "active")).getFilter())
                .isEqualTo(singletonMap("areas&.r&&d", "active"));
    }

    @Test
    void should_throw_for_contains_string() {
        assertThatThrownBy(() -> AstraDbFilterMapper.map(new ContainsString("key", "value")))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("Unsupported filter type");
    }

    private static List<Object> operatorValues(Map<String, Object> filter, String field, String operator) {
        Object operatorFilter = filter.get(field);
        assertThat(operatorFilter).isInstanceOf(Map.class);

        Map<?, ?> operators = (Map<?, ?>) operatorFilter;
        Object values = operators.get(operator);
        assertThat(values).isInstanceOf(List.class);

        return new ArrayList<>((List<?>) values);
    }
}
