package dev.langchain4j.store.embedding.weaviate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import io.weaviate.client.v1.data.model.WeaviateObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
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

    @Test
    void should_read_every_page_until_a_page_is_not_full() {
        List<List<WeaviateObject>> pages = List.of(pageOf("a", "b"), pageOf("c", "d"), pageOf("e"));
        AtomicInteger requests = new AtomicInteger();
        List<String> requestedAfters = new ArrayList<>();
        List<String> consumedIds = new ArrayList<>();

        WeaviateEmbeddingStore.forEachObjectPage(
                2,
                (limit, after) -> {
                    assertThat(limit).isEqualTo(2);
                    requestedAfters.add(after);
                    int index = requests.getAndIncrement();
                    return index < pages.size() ? pages.get(index) : List.of();
                },
                objects -> objects.forEach(object -> consumedIds.add(object.getId())));

        // One request per page, and each one follows the id of the last object of the previous page.
        assertThat(requests.get()).isEqualTo(3);
        assertThat(requestedAfters).containsExactly((String) null, "b", "d");
        assertThat(consumedIds).containsExactly("a", "b", "c", "d", "e");
    }

    @Test
    void should_follow_the_cursor_once_more_when_the_last_page_is_full() {
        List<List<WeaviateObject>> pages = List.of(pageOf("a", "b"), pageOf("c", "d"));
        AtomicInteger requests = new AtomicInteger();
        List<String> consumedIds = new ArrayList<>();

        WeaviateEmbeddingStore.forEachObjectPage(
                2,
                (limit, after) -> {
                    int index = requests.getAndIncrement();
                    return index < pages.size() ? pages.get(index) : List.of();
                },
                objects -> objects.forEach(object -> consumedIds.add(object.getId())));

        // A full page cannot be the last one, so one more request is needed to find out that the class ended.
        assertThat(requests.get()).isEqualTo(3);
        assertThat(consumedIds).containsExactly("a", "b", "c", "d");
    }

    @Test
    void should_do_nothing_when_the_class_is_empty() {
        AtomicInteger requests = new AtomicInteger();
        List<List<WeaviateObject>> consumed = new ArrayList<>();

        WeaviateEmbeddingStore.forEachObjectPage(
                2,
                (limit, after) -> {
                    requests.incrementAndGet();
                    return List.of();
                },
                consumed::add);

        assertThat(requests.get()).isEqualTo(1);
        assertThat(consumed).isEmpty();
    }

    @Test
    void should_stop_when_the_client_returns_no_objects() {
        // Result.getResult() is null when the request failed.
        AtomicInteger requests = new AtomicInteger();
        List<List<WeaviateObject>> consumed = new ArrayList<>();

        WeaviateEmbeddingStore.forEachObjectPage(
                2,
                (limit, after) -> {
                    requests.incrementAndGet();
                    return null;
                },
                consumed::add);

        assertThat(requests.get()).isEqualTo(1);
        assertThat(consumed).isEmpty();
    }

    private static WeaviateObject objectWithId(String id) {
        return WeaviateObject.builder().id(id).build();
    }

    private static List<WeaviateObject> pageOf(String... ids) {
        List<WeaviateObject> objects = new ArrayList<>();
        for (String id : ids) {
            objects.add(objectWithId(id));
        }
        return objects;
    }
}
