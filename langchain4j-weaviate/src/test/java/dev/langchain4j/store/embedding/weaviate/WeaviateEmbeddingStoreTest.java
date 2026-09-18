package dev.langchain4j.store.embedding.weaviate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WeaviateEmbeddingStoreTest {

    private static final String TEXT_FIELD = "text";
    private static final String METADATA_FIELD = "_metadata";

    private static Map<String, Object> rootModeProperties() {
        // Mirrors what buildObject() stores in root metadata mode: text, the Boolean index flags,
        // and metadata entries flattened into the root of the object.
        Map<String, Object> properties = new HashMap<>();
        properties.put(TEXT_FIELD, "hello");
        properties.put("indexFilterable", true);
        properties.put("indexSearchable", true);
        properties.put("key", "value");
        return properties;
    }

    private static Map<String, Object> nestedModeProperties() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("key", "value");
        Map<String, Object> properties = new HashMap<>();
        properties.put(TEXT_FIELD, "hello");
        properties.put("indexFilterable", true);
        properties.put("indexSearchable", true);
        properties.put(METADATA_FIELD, metadata);
        return properties;
    }

    @Test
    void should_match_root_mode_when_filter_matches() {
        Filter filter = new IsEqualTo("key", "value");

        boolean matched = WeaviateEmbeddingStore.matchesFilter(rootModeProperties(), "", TEXT_FIELD, filter);

        assertThat(matched).isTrue();
    }

    @Test
    void should_not_match_root_mode_when_filter_does_not_match() {
        Filter filter = new IsEqualTo("key", "other");

        boolean matched = WeaviateEmbeddingStore.matchesFilter(rootModeProperties(), "", TEXT_FIELD, filter);

        assertThat(matched).isFalse();
    }

    @Test
    void should_match_nested_mode_when_filter_matches() {
        Filter filter = new IsEqualTo("key", "value");

        boolean matched =
                WeaviateEmbeddingStore.matchesFilter(nestedModeProperties(), METADATA_FIELD, TEXT_FIELD, filter);

        assertThat(matched).isTrue();
    }

    @Test
    void should_not_match_nested_mode_when_filter_does_not_match() {
        Filter filter = new IsEqualTo("key", "other");

        boolean matched =
                WeaviateEmbeddingStore.matchesFilter(nestedModeProperties(), METADATA_FIELD, TEXT_FIELD, filter);

        assertThat(matched).isFalse();
    }

    @Test
    void should_not_match_nested_mode_when_metadata_field_is_missing() {
        Map<String, Object> properties = new HashMap<>();
        properties.put(TEXT_FIELD, "hello");
        Filter filter = new IsEqualTo("key", "value");

        boolean matched = WeaviateEmbeddingStore.matchesFilter(properties, METADATA_FIELD, TEXT_FIELD, filter);

        assertThat(matched).isFalse();
    }

    @Test
    void should_not_throw_when_root_properties_contain_boolean_index_flags() {
        Filter filter = new IsEqualTo("key", "value");

        // Boolean index flags are not supported Metadata value types; matchesFilter must strip them
        // before constructing Metadata, otherwise new Metadata(...) throws IllegalArgumentException.
        assertThatCode(() -> WeaviateEmbeddingStore.matchesFilter(rootModeProperties(), "", TEXT_FIELD, filter))
                .doesNotThrowAnyException();
    }
}
