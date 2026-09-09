package dev.langchain4j.store.embedding.azure.search;

import static dev.langchain4j.store.embedding.azure.search.AbstractAzureAiSearchEmbeddingStore.metadataFrom;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.azure.core.credential.AzureKeyCredential;
import com.azure.search.documents.indexes.models.SearchIndex;
import dev.langchain4j.data.document.Metadata;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AzureAiSearchEmbeddingStoreTest {

    String endpoint = "http://localhost";
    AzureKeyCredential keyCredential = new AzureKeyCredential("TEST");
    int dimensions = 1536;
    SearchIndex index = new SearchIndex("TEST");
    String indexName = "TEST";

    @Test
    void empty_endpoint_should_not_be_allowed() {
        try {
            new AzureAiSearchEmbeddingStore(null, keyCredential, false, dimensions, null, null);
            fail("Expected IllegalArgumentException to be thrown");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).isEqualTo("endpoint cannot be null");
        }
    }

    @Test
    void provided_index_name_should_be_used_when_the_index_is_not_created() {
        AzureAiSearchEmbeddingStore store =
                new AzureAiSearchEmbeddingStore(endpoint, keyCredential, false, index, null, null);

        assertThat(store.searchClient.getIndexName()).isEqualTo(index.getName());
    }

    @Test
    void default_index_name_should_be_used_when_no_index_is_provided() {
        AzureAiSearchEmbeddingStore store =
                new AzureAiSearchEmbeddingStore(endpoint, keyCredential, false, dimensions, null, null);

        assertThat(store.searchClient.getIndexName()).isEqualTo("vectorsearch");
    }

    @Test
    void index_and_index_name_should_not_both_be_defined() {
        try {
            new AzureAiSearchEmbeddingStore(endpoint, keyCredential, false, index, indexName, null);
            fail("Expected IllegalArgumentException to be thrown");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).isEqualTo("index and indexName cannot be both defined");
        }
    }

    @Test
    void metadata_from_complex_field_should_map_attributes() {
        Object rawMetadata = Map.of(
                "source",
                "doc.pdf",
                "attributes",
                List.of(Map.of("key", "source", "value", "doc.pdf"), Map.of("key", "page", "value", "3")));

        Metadata metadata = metadataFrom(rawMetadata);

        assertThat(metadata.toMap()).containsOnly(Map.entry("source", "doc.pdf"), Map.entry("page", "3"));
    }

    @Test
    void metadata_from_string_field_should_return_empty_metadata() {
        Metadata metadata = metadataFrom("arbitrary string from a bring-your-own index");

        assertThat(metadata.toMap()).isEmpty();
    }

    @Test
    void metadata_from_null_should_return_empty_metadata() {
        Metadata metadata = metadataFrom(null);

        assertThat(metadata.toMap()).isEmpty();
    }

    @Test
    void metadata_without_attributes_field_should_return_empty_metadata() {
        Metadata metadata = metadataFrom(Map.of("source", "doc.pdf"));

        assertThat(metadata.toMap()).isEmpty();
    }

    @Test
    void metadata_with_non_list_attributes_should_return_empty_metadata() {
        Metadata metadata = metadataFrom(Map.of("attributes", "not-a-list"));

        assertThat(metadata.toMap()).isEmpty();
    }

    @Test
    void metadata_with_non_map_attribute_entry_should_skip_it() {
        Object rawMetadata = Map.of("attributes", List.of("not-a-map", Map.of("key", "page", "value", "3")));

        Metadata metadata = metadataFrom(rawMetadata);

        assertThat(metadata.toMap()).containsOnly(Map.entry("page", "3"));
    }

    @Test
    void metadata_attribute_with_null_key_or_value_should_be_skipped() {
        Map<String, Object> nullKey = new HashMap<>();
        nullKey.put("key", null);
        nullKey.put("value", "orphan");
        Map<String, Object> nullValue = new HashMap<>();
        nullValue.put("key", "orphan");
        nullValue.put("value", null);
        Object rawMetadata = Map.of("attributes", List.of(nullKey, nullValue, Map.of("key", "page", "value", "3")));

        Metadata metadata = metadataFrom(rawMetadata);

        assertThat(metadata.toMap()).containsOnly(Map.entry("page", "3"));
    }

    @Test
    void metadata_attribute_with_blank_key_should_be_skipped() {
        Object rawMetadata = Map.of(
                "attributes", List.of(Map.of("key", " ", "value", "orphan"), Map.of("key", "page", "value", "3")));

        Metadata metadata = metadataFrom(rawMetadata);

        assertThat(metadata.toMap()).containsOnly(Map.entry("page", "3"));
    }

    @Test
    void metadata_attribute_with_non_string_value_should_be_coerced() {
        Object rawMetadata = Map.of("attributes", List.of(Map.of("key", "page", "value", 3)));

        Metadata metadata = metadataFrom(rawMetadata);

        assertThat(metadata.toMap()).containsOnly(Map.entry("page", "3"));
    }

    @Test
    void builder_configures_metadata_field_names() {
        AzureAiSearchEmbeddingStore store = AzureAiSearchEmbeddingStore.builder()
                .endpoint(endpoint)
                .apiKey("test")
                .createOrUpdateIndex(false)
                .dimensions(dimensions)
                .metadataFieldNames(List.of("sourcepage", "topic"))
                .build();

        assertThat(store.metadataFieldNames).containsExactly("sourcepage", "topic");
    }

    @Test
    void builder_treats_null_metadata_field_names_as_none() {
        AzureAiSearchEmbeddingStore store = AzureAiSearchEmbeddingStore.builder()
                .endpoint(endpoint)
                .apiKey("test")
                .createOrUpdateIndex(false)
                .dimensions(dimensions)
                .metadataFieldNames(null)
                .build();

        assertThat(store.metadataFieldNames).isEmpty();
    }

    @Test
    void copies_allowlisted_top_level_fields_into_metadata() {
        Map<String, Object> searchDocument = new HashMap<>();
        searchDocument.put("id", "doc-1");
        searchDocument.put("content", "the document text");
        searchDocument.put("content_vector", List.of(0.1f, 0.2f));
        searchDocument.put("sourcepage", "guide.pdf");
        searchDocument.put("topic", "Ambulatory");
        searchDocument.put("unlisted", "ignored");

        Metadata metadata = metadataFrom(searchDocument, List.of("sourcepage", "topic"));

        assertThat(metadata.toMap())
                .containsOnly(Map.entry("sourcepage", "guide.pdf"), Map.entry("topic", "Ambulatory"));
    }

    @Test
    void preserves_supported_value_types_for_allowlisted_fields() {
        UUID ref = UUID.fromString("00000000-0000-0000-0000-000000000001");
        Map<String, Object> searchDocument = new HashMap<>();
        searchDocument.put("pages", 12);
        searchDocument.put("score", 4.5d);
        searchDocument.put("ref", ref);

        Metadata metadata = metadataFrom(searchDocument, List.of("pages", "score", "ref"));

        assertThat(metadata.toMap())
                .containsOnly(Map.entry("pages", 12), Map.entry("score", 4.5d), Map.entry("ref", ref));
    }

    @Test
    void skips_absent_null_unsupported_and_blank_name_allowlisted_fields() {
        Map<String, Object> searchDocument = new HashMap<>();
        searchDocument.put("present", "value");
        searchDocument.put("nullField", null);
        searchDocument.put("vector", List.of(0.1f));
        searchDocument.put("flag", true);
        searchDocument.put(" ", "value-under-blank-key");

        Metadata metadata =
                metadataFrom(searchDocument, List.of("present", "nullField", "vector", "flag", "absent", " "));

        assertThat(metadata.toMap()).containsOnly(Map.entry("present", "value"));
    }

    @Test
    void merges_complex_attributes_with_allowlisted_fields() {
        Map<String, Object> searchDocument = new HashMap<>();
        searchDocument.put("metadata", Map.of("attributes", List.of(Map.of("key", "source", "value", "doc.pdf"))));
        searchDocument.put("topic", "Ambulatory");

        Metadata metadata = metadataFrom(searchDocument, List.of("topic"));

        assertThat(metadata.toMap()).containsOnly(Map.entry("source", "doc.pdf"), Map.entry("topic", "Ambulatory"));
    }

    @Test
    void allowlisted_field_takes_precedence_over_complex_metadata_on_key_collision() {
        Map<String, Object> searchDocument = new HashMap<>();
        searchDocument.put("metadata", Map.of("attributes", List.of(Map.of("key", "topic", "value", "from-complex"))));
        searchDocument.put("topic", "from-root");

        Metadata metadata = metadataFrom(searchDocument, List.of("topic"));

        assertThat(metadata.toMap()).containsOnly(Map.entry("topic", "from-root"));
    }
}
