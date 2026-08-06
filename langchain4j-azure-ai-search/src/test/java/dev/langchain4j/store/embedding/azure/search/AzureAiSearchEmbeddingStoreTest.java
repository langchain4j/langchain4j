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
    void dimensions_should_not_be_required_when_the_index_is_not_created() {
        AzureAiSearchEmbeddingStore store = AzureAiSearchEmbeddingStore.builder()
                .endpoint(endpoint)
                .apiKey("TEST")
                .createOrUpdateIndex(false)
                .indexName(indexName)
                .build();

        assertThat(store).isNotNull();
    }

    @Test
    void dimensions_should_still_be_required_when_the_index_is_created() {
        try {
            AzureAiSearchEmbeddingStore.builder()
                    .endpoint(endpoint)
                    .apiKey("TEST")
                    .createOrUpdateIndex(true)
                    .indexName(indexName)
                    .build();
            fail("Expected IllegalArgumentException to be thrown");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage())
                    .isEqualTo("either dimensions or index must be set when createOrUpdateIndex is true");
        }
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
