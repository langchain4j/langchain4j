package dev.langchain4j.store.embedding.azure.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.azure.core.credential.AzureKeyCredential;
import com.azure.search.documents.indexes.models.SearchIndex;
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
}
