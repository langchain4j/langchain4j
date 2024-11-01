package dev.langchain4j.store.embedding.redis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.search.schemafields.NumericField;
import redis.clients.jedis.search.schemafields.SchemaField;
import redis.clients.jedis.search.schemafields.TagField;
import redis.clients.jedis.search.schemafields.TextField;

import java.util.HashMap;
import java.util.Map;

import static dev.langchain4j.store.embedding.filter.Filter.not;
import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RedisMetadataFilterMapperTest {

    RedisMetadataFilterMapper mapper;

    @BeforeEach
    void beforeEach() {
        Map<String, SchemaField> schemaFieldMap = createSchemaField();
        mapper = new RedisMetadataFilterMapper(schemaFieldMap);
    }

    @Test
    void should_map_equal() {
        assertThat(mapper.mapToFilter(metadataKey("age").isEqualTo(20)))
            .isEqualTo("@age:[20]");
        assertThat(mapper.mapToFilter(metadataKey("name").isEqualTo("Klaus")))
            .isEqualTo("@name:{Klaus}");
        assertThat(mapper.mapToFilter(metadataKey("country").isEqualTo("German")))
            .isEqualTo("@country:\"German\"");
    }

    @Test
    void should_map_not_equal() {
        assertThat(mapper.mapToFilter(metadataKey("age").isNotEqualTo(20)))
            .isEqualTo("(-@age:[20])");
        assertThat(mapper.mapToFilter(metadataKey("name").isNotEqualTo("Klaus")))
            .isEqualTo("(-@name:{Klaus})");
        assertThat(mapper.mapToFilter(metadataKey("country").isNotEqualTo("German")))
            .isEqualTo("(-@country:\"German\")");
    }

    @Test
    void should_map_greater_than() {
        assertThat(mapper.mapToFilter(metadataKey("age").isGreaterThan(20)))
            .isEqualTo("@age:[(20 inf]");

        // Text and Tag should throw exception

        assertThatThrownBy(() -> mapper.mapToFilter(metadataKey("name").isGreaterThan("aaa")))
            .isInstanceOf(UnsupportedOperationException.class)
            .hasMessageContaining("Redis do not support non-Numeric range search");

        assertThatThrownBy(() -> mapper.mapToFilter(metadataKey("country").isGreaterThan("bbb")))
            .isInstanceOf(UnsupportedOperationException.class)
            .hasMessageContaining("Redis do not support non-Numeric range search");
    }

    @Test
    void should_map_greater_than_or_equal() {
        assertThat(mapper.mapToFilter(metadataKey("age").isGreaterThanOrEqualTo(20)))
            .isEqualTo("@age:[20 inf]");

        // Text and Tag should throw exception

        assertThatThrownBy(() -> mapper.mapToFilter(metadataKey("name").isGreaterThanOrEqualTo("aaa")))
            .isInstanceOf(UnsupportedOperationException.class)
            .hasMessageContaining("Redis do not support non-Numeric range search");

        assertThatThrownBy(() -> mapper.mapToFilter(metadataKey("country").isGreaterThanOrEqualTo("bbb")))
            .isInstanceOf(UnsupportedOperationException.class)
            .hasMessageContaining("Redis do not support non-Numeric range search");
    }

    @Test
    void should_map_less_than() {
        assertThat(mapper.mapToFilter(metadataKey("age").isLessThan(20)))
            .isEqualTo("@age:[-inf (20]");

        // Text and Tag should throw exception

        assertThatThrownBy(() -> mapper.mapToFilter(metadataKey("name").isLessThan("aaa")))
            .isInstanceOf(UnsupportedOperationException.class)
            .hasMessageContaining("Redis do not support non-Numeric range search");

        assertThatThrownBy(() -> mapper.mapToFilter(metadataKey("country").isLessThan("bbb")))
            .isInstanceOf(UnsupportedOperationException.class)
            .hasMessageContaining("Redis do not support non-Numeric range search");
    }

    @Test
    void should_map_less_than_or_equal() {
        assertThat(mapper.mapToFilter(metadataKey("age").isLessThanOrEqualTo(20)))
            .isEqualTo("@age:[-inf 20]");

        // Text and Tag should throw exception

        assertThatThrownBy(() -> mapper.mapToFilter(metadataKey("name").isLessThanOrEqualTo("aaa")))
            .isInstanceOf(UnsupportedOperationException.class)
            .hasMessageContaining("Redis do not support non-Numeric range search");

        assertThatThrownBy(() -> mapper.mapToFilter(metadataKey("country").isLessThanOrEqualTo("bbb")))
            .isInstanceOf(UnsupportedOperationException.class)
            .hasMessageContaining("Redis do not support non-Numeric range search");
    }

    @Test
    void should_map_in() {

        // Numeric should throw exception
        assertThatThrownBy(() -> mapper.mapToFilter(metadataKey("age").isIn(20)))
            .isInstanceOf(UnsupportedOperationException.class)
            .hasMessageContaining("Redis do not support NumericType \"in\" search");

        // Single in

        assertThat(mapper.mapToFilter(metadataKey("name").isIn("Klaus")))
            .isEqualTo("@name:{Klaus}");
        assertThat(mapper.mapToFilter(metadataKey("country").isIn("German")))
            .isEqualTo("@country:(\"German\")");

        // Multi in

        assertThat(mapper.mapToFilter(metadataKey("name").isIn("Klaus", "Martin")))
            .isEqualTo("@name:{Martin | Klaus}");
        assertThat(mapper.mapToFilter(metadataKey("country").isIn("German", "Japan")))
            .isEqualTo("@country:(\"Japan\" | \"German\")");
    }

    @Test
    void should_map_not_in() {

        // Numeric should throw exception
        assertThatThrownBy(() -> mapper.mapToFilter(metadataKey("age").isNotIn(20)))
            .isInstanceOf(UnsupportedOperationException.class)
            .hasMessageContaining("Redis do not support NumericType \"in\" search");

        // Single in

        assertThat(mapper.mapToFilter(metadataKey("name").isNotIn("Klaus")))
            .isEqualTo("(-@name:{Klaus})");
        assertThat(mapper.mapToFilter(metadataKey("country").isNotIn("German")))
            .isEqualTo("(-@country:(\"German\"))");

        // Multi in

        assertThat(mapper.mapToFilter(metadataKey("name").isNotIn("Klaus", "Martin")))
            .isEqualTo("(-@name:{Martin | Klaus})");
        assertThat(mapper.mapToFilter(metadataKey("country").isNotIn("German", "Japan")))
            .isEqualTo("(-@country:(\"Japan\" | \"German\"))");
    }

    @Test
    void should_map_and() {

        assertThat(mapper.mapToFilter(metadataKey("age").isEqualTo(20).and(metadataKey("name").isEqualTo("Klaus"))))
            .isEqualTo("(@age:[20] @name:{Klaus})");

        assertThat(mapper.mapToFilter(metadataKey("country").isEqualTo("German").and(metadataKey("name").isEqualTo("Klaus"))))
            .isEqualTo("(@country:\"German\" @name:{Klaus})");

        assertThat(mapper.mapToFilter(metadataKey("age").isEqualTo(20).and(metadataKey("name").isEqualTo("Klaus")).and(metadataKey("country").isEqualTo("German"))))
            .isEqualTo("((@age:[20] @name:{Klaus}) @country:\"German\")");
    }

    @Test
    void should_map_or() {

        assertThat(mapper.mapToFilter(metadataKey("age").isEqualTo(20).or(metadataKey("name").isEqualTo("Klaus"))))
            .isEqualTo("(@age:[20] | @name:{Klaus})");

        assertThat(mapper.mapToFilter(metadataKey("country").isEqualTo("German").or(metadataKey("name").isEqualTo("Klaus"))))
            .isEqualTo("(@country:\"German\" | @name:{Klaus})");

        assertThat(mapper.mapToFilter(metadataKey("age").isEqualTo(20).or(metadataKey("name").isEqualTo("Klaus")).or(metadataKey("country").isEqualTo("German"))))
            .isEqualTo("((@age:[20] | @name:{Klaus}) | @country:\"German\")");
    }

    @Test
    void should_map_not() {
        assertThat(mapper.mapToFilter(not(metadataKey("age").isEqualTo(20))))
            .isEqualTo("(-@age:[20])");

        assertThat(mapper.mapToFilter(not(metadataKey("name").isEqualTo("Klaus"))))
            .isEqualTo("(-@name:{Klaus})");

        assertThat(mapper.mapToFilter(not(metadataKey("country").isEqualTo("German"))))
            .isEqualTo("(-@country:\"German\")");
    }

    @Test
    void should_map_anything() {

        // age equal 20 and name not equal Klaus and country in [German, America]
        assertThat(mapper.mapToFilter(metadataKey("age").isEqualTo(20).and(metadataKey("name").isEqualTo("Klaus")).and(metadataKey("country").isIn("German", "America"))))
            .isEqualTo("((@age:[20] @name:{Klaus}) @country:(\"America\" | \"German\"))");

        // age greater than 20 or name is not in [Klaus, Martin]
        assertThat(mapper.mapToFilter(metadataKey("age").isGreaterThan(20).or(metadataKey("name").isNotIn("Klaus", "Martin"))))
            .isEqualTo("(@age:[(20 inf] | (-@name:{Martin | Klaus}))");

        // TODO: add more complex test case
    }

    private Map<String, SchemaField> createSchemaField() {
        Map<String, SchemaField> schemaFieldMap = new HashMap<>();

        schemaFieldMap.put("age", NumericField.of("$.age").as("age"));
        schemaFieldMap.put("name", TagField.of("$.name").as("name"));
        schemaFieldMap.put("country", TextField.of("$.country").as("country"));

        return schemaFieldMap;
    }
}
