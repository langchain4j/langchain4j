package dev.langchain4j.store.embedding.azure.search;

import com.azure.core.credential.AzureKeyCredential;
import com.azure.search.documents.indexes.SearchIndexClient;
import com.azure.search.documents.indexes.SearchIndexClientBuilder;
import com.azure.search.documents.indexes.models.SearchField;
import com.azure.search.documents.indexes.models.SearchFieldDataType;
import com.azure.search.documents.indexes.models.SearchIndex;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithFilteringIT;
import org.awaitility.core.ThrowingRunnable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static dev.langchain4j.internal.Utils.randomUUID;
import static dev.langchain4j.store.embedding.azure.search.AbstractAzureAiSearchEmbeddingStore.DEFAULT_FIELD_ID;
import static dev.langchain4j.store.embedding.azure.search.AbstractAzureAiSearchEmbeddingStore.DEFAULT_INDEX_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

@EnabledIfEnvironmentVariable(named = "AZURE_SEARCH_ENDPOINT", matches = ".+")
public class AzureAiSearchEmbeddingStoreIT extends EmbeddingStoreWithFilteringIT {

    private static final Logger log = LoggerFactory.getLogger(AzureAiSearchEmbeddingStoreIT.class);

    private static final String AZURE_SEARCH_ENDPOINT = System.getenv("AZURE_SEARCH_ENDPOINT");
    private static final String AZURE_SEARCH_KEY = System.getenv("AZURE_SEARCH_KEY");

    private final EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    private final AzureAiSearchEmbeddingStore embeddingStore = AzureAiSearchEmbeddingStore.builder()
            .endpoint(AZURE_SEARCH_ENDPOINT)
            .apiKey(AZURE_SEARCH_KEY)
            .indexName(randomUUID())
            .dimensions(embeddingModel.dimension())
            .build();

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return embeddingStore;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return embeddingModel;
    }

    @BeforeEach
    void beforeEach() throws InterruptedException {
        Thread.sleep(2_000);
    }

    @AfterEach
    void afterEach() {
        try {
            embeddingStore.deleteIndex();
        } catch (RuntimeException e) {
            log.error("Failed to delete the index. You should look at deleting it manually.", e);
        }
    }

    @Override
    protected void awaitUntilAsserted(ThrowingRunnable assertion) {
        super.awaitUntilAsserted(assertion);
        try {
            Thread.sleep(1000); // TODO figure out why this is needed and remove this hack
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected boolean assertEmbedding() {
        return false; // TODO remove this hack after https://github.com/langchain4j/langchain4j/issues/1617 is closed
    }

    @Test
    public void when_an_index_is_provided_its_name_should_be_used() {
        String providedIndexName = "provided-index";
        // Clear the index before running tests
        SearchIndexClient searchIndexClient = new SearchIndexClientBuilder()
                .endpoint(AZURE_SEARCH_ENDPOINT)
                .credential(new AzureKeyCredential(AZURE_SEARCH_KEY))
                .buildClient();
        try {
            searchIndexClient.deleteIndex(providedIndexName);
        } catch (Exception e) {
            // The index didn't exist, so we can ignore the exception
        }

        // Run the tests
        List<SearchField> fields = new ArrayList<>();
        fields.add(new SearchField(DEFAULT_FIELD_ID, SearchFieldDataType.STRING)
                .setKey(true)
                .setFilterable(true));
        SearchIndex providedIndex = new SearchIndex(providedIndexName).setFields(fields);
        AzureAiSearchEmbeddingStore store =
                new AzureAiSearchEmbeddingStore(AZURE_SEARCH_ENDPOINT,
                        new AzureKeyCredential(AZURE_SEARCH_KEY), true, providedIndex, null, null);

        assertEquals(providedIndexName, store.searchClient.getIndexName());

        try {
            new AzureAiSearchEmbeddingStore(AZURE_SEARCH_ENDPOINT,
                    new AzureKeyCredential(AZURE_SEARCH_KEY), true, providedIndex, "ANOTHER_INDEX_NAME", null);

            fail("Expected IllegalArgumentException to be thrown");
        } catch (IllegalArgumentException e) {
            assertEquals("index and indexName cannot be both defined", e.getMessage());
        }

        // Clear index
        searchIndexClient.deleteIndex(providedIndexName);
    }

    @Test
    public void when_an_index_is_not_provided_the_default_name_is_used() {

        AzureAiSearchEmbeddingStore store = new AzureAiSearchEmbeddingStore(
                AZURE_SEARCH_ENDPOINT,
                new AzureKeyCredential(AZURE_SEARCH_KEY),
                false,
                null,
                null,
                null
        );

        assertEquals(DEFAULT_INDEX_NAME, store.searchClient.getIndexName());
    }
}
