package dev.langchain4j.store.embedding.azure.search;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithRemovalIT;
import org.awaitility.core.ThrowingRunnable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static dev.langchain4j.internal.Utils.randomUUID;

@EnabledIfEnvironmentVariable(named = "AZURE_SEARCH_ENDPOINT", matches = ".+")
public class AzureAiSearchEmbeddingStoreRemovalIT extends EmbeddingStoreWithRemovalIT {

    private static final Logger log = LoggerFactory.getLogger(AzureAiSearchEmbeddingStoreRemovalIT.class);

    private final EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    private final AzureAiSearchEmbeddingStore embeddingStore = AzureAiSearchEmbeddingStore.builder()
            .endpoint(System.getenv("AZURE_SEARCH_ENDPOINT"))
            .apiKey(System.getenv("AZURE_SEARCH_KEY"))
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
}
