package dev.langchain4j.store.embedding.azure.search;

import com.azure.core.credential.AzureKeyCredential;
import com.azure.search.documents.indexes.models.SearchIndex;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class AzureAiSearchEmbeddingStoreTest {

    String endpoint = "http://localhost";
    AzureKeyCredential keyCredential = new AzureKeyCredential("TEST");
    int dimensions = 1536;
    SearchIndex index = new SearchIndex("TEST");
    String indexName = "TEST";

    @Test
    public void empty_endpoint_should_not_be_allowed() {
        try {
            new AzureAiSearchEmbeddingStore(null, keyCredential, false, dimensions, null, null);
            fail("Expected IllegalArgumentException to be thrown");
        } catch (IllegalArgumentException e) {
            assertEquals("endpoint cannot be null", e.getMessage());
        }
    }

    @Test
    public void index_and_index_name_should_not_both_be_defined() {
        try {
            new AzureAiSearchEmbeddingStore(endpoint, keyCredential, false, index, indexName, null);
            fail("Expected IllegalArgumentException to be thrown");
        } catch (IllegalArgumentException e) {
            assertEquals("index and indexName cannot be both defined", e.getMessage());
        }
    }
}
