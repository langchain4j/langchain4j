package dev.langchain4j.store.embedding.azure.search;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithRemovalIT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@EnabledIfEnvironmentVariable(named = "AZURE_SEARCH_ENDPOINT", matches = ".+")
public class AzureAiSearchEmbeddingStoreRemovalIT extends EmbeddingStoreWithRemovalIT {

    private static final Logger log = LoggerFactory.getLogger(AzureAiSearchEmbeddingStoreRemovalIT.class);

    private EmbeddingModel embeddingModel;

    private EmbeddingStore<TextSegment> embeddingStore;

    private String AZURE_SEARCH_ENDPOINT = System.getenv("AZURE_SEARCH_ENDPOINT");

    private String AZURE_SEARCH_KEY = System.getenv("AZURE_SEARCH_KEY");

    public AzureAiSearchEmbeddingStoreRemovalIT() {

        embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

        embeddingStore = AzureAiSearchEmbeddingStore.builder()
                .endpoint(AZURE_SEARCH_ENDPOINT)
                .apiKey(AZURE_SEARCH_KEY)
                .dimensions(embeddingModel.dimension())
                .build();
    }

    @BeforeEach
    void setUp() {
        clearStore();
    }

    private void clearStore() {
        AzureAiSearchEmbeddingStore azureAiSearchEmbeddingStore = (AzureAiSearchEmbeddingStore) embeddingStore;
        try {
            azureAiSearchEmbeddingStore.deleteIndex();
            azureAiSearchEmbeddingStore.createOrUpdateIndex(embeddingModel.dimension());
        } catch (RuntimeException e) {
            log.error("Failed to clean up the index. You should look at deleting it manually.", e);
        }
    }

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return embeddingStore;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return embeddingModel;
    }
}
