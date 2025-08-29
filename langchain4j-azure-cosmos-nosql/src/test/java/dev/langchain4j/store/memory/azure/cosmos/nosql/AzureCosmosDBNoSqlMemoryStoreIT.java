package dev.langchain4j.store.memory.azure.cosmos.nosql;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@EnabledIfEnvironmentVariable(named = "AZURE_COSMOS_HOST", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AZURE_COSMOS_MASTER_KEY", matches = ".+")
class AzureCosmosDBNoSqlMemoryStoreIT extends AzureCosmosDBNoSqlMemoryStoreTest {

    private static final String AZURE_COSMOS_HOST = System.getenv("AZURE_COSMOS_HOST");
    private static final String AZURE_COSMOS_MASTER_KEY = System.getenv("AZURE_COSMOS_MASTER_KEY");

    @Override
    AzureCosmosDBNoSqlMemoryStore createMemoryStore() {
        return AzureCosmosDBNoSqlMemoryStore.builder()
                .endpoint(AZURE_COSMOS_HOST)
                .apiKey(AZURE_COSMOS_MASTER_KEY)
                .databaseName(DATABASE_NAME)
                .containerName(CONTAINER_NAME)
                .build();
    }
}
