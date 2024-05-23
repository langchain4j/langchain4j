package dev.langchain4j.store.embedding.azure.search;

import com.azure.core.credential.AzureKeyCredential;
import com.azure.search.documents.indexes.models.SearchIndex;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class AzureAiSearchEmbeddingStoreTest {

    @Test
    public void testConstructorParameters() {
        String endpoint = "http://localhost";
        AzureKeyCredential keyCredential = new AzureKeyCredential("TEST");
        int dimensions = 1536;
        SearchIndex index = new SearchIndex("TEST");
        String indexName = "TEST";

        // Test empty endpoint
        try {
            new AzureAiSearchEmbeddingStore(null, keyCredential, false, dimensions, null);
            fail("Expected IllegalArgumentException to be thrown");
        } catch (IllegalArgumentException e) {
            assertEquals("endpoint cannot be null", e.getMessage());
        }

        // Test index and indexName should not both be defined
        try {
            new AzureAiSearchEmbeddingStore(endpoint, keyCredential, false, index, indexName);
            fail("Expected IllegalArgumentException to be thrown");
        } catch (IllegalArgumentException e) {
            assertEquals("index and indexName cannot be both defined", e.getMessage());
        }
    }
}
