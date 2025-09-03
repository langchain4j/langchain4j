package dev.langchain4j.store.embedding.azure.cosmos.nosql;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIT;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "AZURE_COSMOS_HOST", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AZURE_COSMOS_MASTER_KEY", matches = ".+")
public class AzureCosmosDbNoSqlEmbeddingStoreIT extends EmbeddingStoreIT {

    private static final String DATABASE_NAME = "test_database_langchain_java";
    private static final String CONTAINER_NAME = "test_embedding_container";

    private final EmbeddingModel embeddingModel = new TestEmbeddingModel();
    private final AzureCosmosDbNoSqlEmbeddingStore embeddingStore = AzureCosmosDbNoSqlEmbeddingStore.builder()
            .endpoint(System.getenv("AZURE_COSMOS_HOST"))
            .apiKey(System.getenv("AZURE_COSMOS_MASTER_KEY"))
            .databaseName(DATABASE_NAME)
            .containerName(CONTAINER_NAME)
            .build();

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return embeddingStore;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return embeddingModel;
    }

    @AfterEach
    void afterEach() {
        embeddingStore.deleteContainer();
    }
}
