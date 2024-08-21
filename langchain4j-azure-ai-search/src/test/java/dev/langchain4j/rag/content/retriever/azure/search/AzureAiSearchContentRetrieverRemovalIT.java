package dev.langchain4j.rag.content.retriever.azure.search;

import com.azure.core.credential.AzureKeyCredential;
import com.azure.search.documents.indexes.SearchIndexClient;
import com.azure.search.documents.indexes.SearchIndexClientBuilder;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithRemovalIT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static dev.langchain4j.store.embedding.azure.search.AbstractAzureAiSearchEmbeddingStore.DEFAULT_INDEX_NAME;

@EnabledIfEnvironmentVariable(named = "AZURE_SEARCH_ENDPOINT", matches = ".+")
public class AzureAiSearchContentRetrieverRemovalIT extends EmbeddingStoreWithRemovalIT {

    private static final Logger log = LoggerFactory.getLogger(AzureAiSearchContentRetrieverRemovalIT.class);

    private EmbeddingModel embeddingModel;

    private AzureAiSearchContentRetriever contentRetrieverWithVector;

    public AzureAiSearchContentRetrieverRemovalIT() {
        embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

        SearchIndexClient searchIndexClient = new SearchIndexClientBuilder()
                .endpoint(System.getenv("AZURE_SEARCH_ENDPOINT"))
                .credential(new AzureKeyCredential(System.getenv("AZURE_SEARCH_KEY")))
                .buildClient();

        searchIndexClient.deleteIndex(DEFAULT_INDEX_NAME);

        contentRetrieverWithVector =  createContentRetriever(AzureAiSearchQueryType.VECTOR);
    }

    private AzureAiSearchContentRetriever createContentRetriever(AzureAiSearchQueryType azureAiSearchQueryType) {
        return AzureAiSearchContentRetriever.builder()
                .endpoint(System.getenv("AZURE_SEARCH_ENDPOINT"))
                .apiKey(System.getenv("AZURE_SEARCH_KEY"))
                .dimensions(embeddingModel.dimension())
                .embeddingModel(embeddingModel)
                .queryType(azureAiSearchQueryType)
                .maxResults(3)
                .minScore(0.0)
                .build();
    }

    @BeforeEach
    void setUp() {
        clearStore();
    }

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return this.contentRetrieverWithVector;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return this.embeddingModel;
    }

    protected void clearStore() {
        log.debug("Deleting the search index");
        AzureAiSearchContentRetriever azureAiSearchContentRetriever = contentRetrieverWithVector;
        try {
            azureAiSearchContentRetriever.deleteIndex();
            azureAiSearchContentRetriever.createOrUpdateIndex(embeddingModel.dimension());
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
