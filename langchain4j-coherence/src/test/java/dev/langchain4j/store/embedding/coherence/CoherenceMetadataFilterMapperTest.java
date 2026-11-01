package dev.langchain4j.store.embedding.coherence;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import static org.assertj.core.api.Assertions.assertThat;

import com.oracle.coherence.ai.DocumentChunk;
import dev.langchain4j.store.embedding.filter.Filter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CoherenceMetadataFilterMapperTest {

    @ParameterizedTest
    @ValueSource(strings = {"isbn", "is_active", "getting_started", "isEmbedded"})
    void should_match_metadata_key_starting_with_bean_accessor_prefix(String key) {
        // given
        Map<String, Object> metadata = metadata(key, "expected");

        // then
        assertThat(matches(metadataKey(key).isEqualTo("expected"), metadata)).isTrue();
        assertThat(matches(metadataKey(key).isEqualTo("other"), metadata)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"size", "empty", "class", "values"})
    void should_match_metadata_key_clashing_with_map_method_name(String key) {
        // given
        Map<String, Object> metadata = metadata(key, "expected", "city", "Munich");

        // then
        assertThat(matches(metadataKey(key).isEqualTo("expected"), metadata)).isTrue();
        assertThat(matches(metadataKey(key).isEqualTo("other"), metadata)).isFalse();
    }

    @Test
    void should_not_compare_map_method_result_with_numeric_filter() {
        // given a metadata key named after a Map method, holding a number
        Map<String, Object> metadata = metadata("size", 1, "city", "Munich");

        // then the filter must see the metadata value 1, not the entry count 2
        assertThat(matches(metadataKey("size").isGreaterThan(1), metadata)).isFalse();
        assertThat(matches(metadataKey("size").isEqualTo(1), metadata)).isTrue();
    }

    @Test
    void should_match_in_and_not_in_filters_on_prefixed_key() {
        // given
        Map<String, Object> metadata = metadata("is_active", "yes");

        // then
        assertThat(matches(metadataKey("is_active").isIn("yes", "no"), metadata))
                .isTrue();
        assertThat(matches(metadataKey("is_active").isNotIn(List.of("no")), metadata))
                .isTrue();
        assertThat(matches(metadataKey("is_active").isIn("no"), metadata)).isFalse();
    }

    @Test
    void should_match_ordinary_metadata_keys() {
        // given
        Map<String, Object> metadata = metadata("name", "book", "city", "Munich", "age", 42);

        // then
        assertThat(matches(metadataKey("name").isEqualTo("book"), metadata)).isTrue();
        assertThat(matches(metadataKey("name").isEqualTo("magazine"), metadata)).isFalse();
        assertThat(matches(metadataKey("city").isNotEqualTo("Berlin"), metadata))
                .isTrue();
        assertThat(matches(metadataKey("age").isGreaterThanOrEqualTo(42), metadata))
                .isTrue();
        assertThat(matches(metadataKey("age").isLessThan(42), metadata)).isFalse();
    }

    @Test
    void should_not_match_key_that_is_absent_from_metadata() {
        // given
        Map<String, Object> metadata = metadata("name", "book");

        // then
        assertThat(matches(metadataKey("isbn").isEqualTo("978-1"), metadata)).isFalse();
        assertThat(matches(metadataKey("size").isEqualTo("large"), metadata)).isFalse();
    }

    private static boolean matches(Filter filter, Map<String, Object> metadata) {
        return CoherenceMetadataFilterMapper.map(filter).evaluate(new DocumentChunk("text", metadata));
    }

    private static Map<String, Object> metadata(Object... keysAndValues) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            metadata.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return metadata;
    }
}
