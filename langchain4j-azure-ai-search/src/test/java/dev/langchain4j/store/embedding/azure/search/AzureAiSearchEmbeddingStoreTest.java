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
}
