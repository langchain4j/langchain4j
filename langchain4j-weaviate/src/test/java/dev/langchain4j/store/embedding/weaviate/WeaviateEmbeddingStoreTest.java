package dev.langchain4j.store.embedding.weaviate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.RelevanceScore;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import java.util.HashMap;
import java.util.List;
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
    void should_convert_certainty_to_relevance_score_in_embedding_match() {
        // given - a Weaviate search result item with certainty (a raw cosine similarity)
        WeaviateEmbeddingStore store = WeaviateEmbeddingStore.builder()
                .scheme("http")
                .host("localhost")
                .build();

        Map<String, Object> additional = new HashMap<>();
        additional.put("certainty", 0.6);
        additional.put("id", "id1");
        additional.put("vector", List.of(1.0, 2.0, 3.0));
        Map<String, Object> item = new HashMap<>();
        item.put("_additional", additional);
        item.put(TEXT_FIELD, "hello");

        // when
        EmbeddingMatch<TextSegment> match = store.toEmbeddingMatch(item);

        // then - certainty is mapped into a relevance score in [0..1]
        assertThat(match.score()).isEqualTo(RelevanceScore.fromCosineSimilarity(0.6));
        assertThat(match.embeddingId()).isEqualTo("id1");
        assertThat(match.embedded().text()).isEqualTo("hello");
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
