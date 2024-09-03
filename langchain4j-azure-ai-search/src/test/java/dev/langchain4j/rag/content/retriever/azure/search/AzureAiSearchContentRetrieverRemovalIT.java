package dev.langchain4j.rag.content.retriever.azure.search;

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
import static dev.langchain4j.rag.content.retriever.azure.search.AzureAiSearchQueryType.HYBRID;

@EnabledIfEnvironmentVariable(named = "AZURE_SEARCH_ENDPOINT", matches = ".+")
public class AzureAiSearchContentRetrieverRemovalIT extends EmbeddingStoreWithRemovalIT {

    private static final Logger log = LoggerFactory.getLogger(AzureAiSearchContentRetrieverRemovalIT.class);

    private final EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    private final AzureAiSearchContentRetriever contentRetrieverWithVector = AzureAiSearchContentRetriever.builder()
            .endpoint(System.getenv("AZURE_SEARCH_ENDPOINT"))
            .apiKey(System.getenv("AZURE_SEARCH_KEY"))
            .indexName(randomUUID())
            .dimensions(embeddingModel.dimension())
            .embeddingModel(embeddingModel)
            .queryType(HYBRID)
            .build();

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return this.contentRetrieverWithVector;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return this.embeddingModel;
    }

    @BeforeEach
    void beforeEach() throws InterruptedException {
        Thread.sleep(2_000);
    }

    @AfterEach
    void afterEach() {
        try {
            contentRetrieverWithVector.deleteIndex();
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
