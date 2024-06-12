package dev.langchain4j.store.embedding.azure.search;

import com.azure.core.credential.AzureKeyCredential;
import com.azure.search.documents.indexes.models.SearchIndex;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

@EnabledIfEnvironmentVariable(named = "AZURE_SEARCH_ENDPOINT", matches = ".+")
public class AzureAiSearchEmbeddingStoreIT extends EmbeddingStoreIT {

    private static final Logger log = LoggerFactory.getLogger(AzureAiSearchEmbeddingStoreIT.class);

    private EmbeddingModel embeddingModel;

    private EmbeddingStore<TextSegment> embeddingStore;

    private int dimensions;

    private String AZURE_SEARCH_ENDPOINT = System.getenv("AZURE_SEARCH_ENDPOINT");

    private String AZURE_SEARCH_KEY = System.getenv("AZURE_SEARCH_KEY");

    public AzureAiSearchEmbeddingStoreIT() {

        embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();
        dimensions = embeddingModel.embed("test").content().vector().length;

        embeddingStore =  AzureAiSearchEmbeddingStore.builder()
                .endpoint(AZURE_SEARCH_ENDPOINT)
                .apiKey(AZURE_SEARCH_KEY)
                .dimensions(dimensions)
                .build();
    }

    @BeforeEach
    void setUp() {
        clearStore();
    }

    @Test
    public void when_an_index_is_provided_its_name_should_be_used() {
        SearchIndex providedIndex = new SearchIndex("PROVIDED_INDEX");
        AzureAiSearchEmbeddingStore store =
                new AzureAiSearchEmbeddingStore(AZURE_SEARCH_ENDPOINT,
                        new AzureKeyCredential(AZURE_SEARCH_KEY), false, providedIndex, null);

        assertEquals(providedIndex.getName(), store.searchClient.getIndexName());
    }


    @Test
    public void when_an_index_is_provided_it_cannot_be_created() {
        try {
            SearchIndex providedIndex = new SearchIndex("PROVIDED_INDEX");
            AzureAiSearchEmbeddingStore store =
                    new AzureAiSearchEmbeddingStore(AZURE_SEARCH_ENDPOINT,
                            new AzureKeyCredential(AZURE_SEARCH_KEY), true, providedIndex, null);

            fail("Expected IllegalArgumentException to be thrown");
        } catch (IllegalArgumentException e) {
            assertEquals("createOrUpdateIndex is true: if the index is created or updated, then the index cannot also be provided", e.getMessage());
        }
    }

    @Test
    void test_add_embeddings_and_find_relevant() {
        String content1 = "banana";
        String content2 = "computer";
        String content3 = "apple";
        String content4 = "pizza";
        String content5 = "strawberry";
        String content6 = "chess";
        List<String> contents = asList(content1, content2, content3, content4, content5, content6);

        for (String content : contents) {
            TextSegment textSegment = TextSegment.from(content);
            Embedding embedding = embeddingModel.embed(content).content();
            embeddingStore.add(embedding, textSegment);
        }

        awaitUntilPersisted();

        Embedding relevantEmbedding = embeddingModel.embed("fruit").content();
        List<EmbeddingMatch<TextSegment>> relevant = embeddingStore.findRelevant(relevantEmbedding, 3);
        assertThat(relevant).hasSize(3);
        assertThat(relevant.get(0).embedding()).isNotNull();
        assertThat(relevant.get(0).embedded().text()).isIn(content1, content3, content5);
        log.info("#1 relevant item: {}", relevant.get(0).embedded().text());
        assertThat(relevant.get(1).embedding()).isNotNull();
        assertThat(relevant.get(1).embedded().text()).isIn(content1, content3, content5);
        log.info("#2 relevant item: {}", relevant.get(1).embedded().text());
        assertThat(relevant.get(2).embedding()).isNotNull();
        assertThat(relevant.get(2).embedded().text()).isIn(content1, content3, content5);
        log.info("#3 relevant item: {}", relevant.get(2).embedded().text());
    }

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return embeddingStore;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return embeddingModel;
    }

    @Override
    protected void clearStore() {
        AzureAiSearchEmbeddingStore azureAiSearchEmbeddingStore = (AzureAiSearchEmbeddingStore) embeddingStore;
        try {
            azureAiSearchEmbeddingStore.deleteIndex();
            azureAiSearchEmbeddingStore.createOrUpdateIndex(dimensions);
        } catch (RuntimeException e) {
            log.error("Failed to clean up the index. You should look at deleting it manually.", e);
        }
    }

    @Override
    protected void awaitUntilPersisted() {
        try {
            Thread.sleep(1_000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
