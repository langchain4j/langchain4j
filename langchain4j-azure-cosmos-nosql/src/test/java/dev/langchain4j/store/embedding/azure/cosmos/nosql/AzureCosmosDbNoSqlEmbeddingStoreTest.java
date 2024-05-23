package dev.langchain4j.store.embedding.azure.cosmos.nosql;

import com.azure.cosmos.ConsistencyLevel;
import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.models.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;

@EnabledIfEnvironmentVariable(named = "AZURE_COSMOS_HOST", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AZURE_COSMOS_MASTER_KEY", matches = ".+")
class AzureCosmosDbNoSqlEmbeddingStoreTest {

    private static final String DATABASE_NAME = "test_db";
    private static final String CONTAINER_NAME = "test_container";

    @Test
    void should_fail_if_cosmosClient_missing() {
        assertThrows(IllegalArgumentException.class, () -> {
            AzureCosmosDbNoSqlEmbeddingStore.builder()
                    .cosmosClient(null)
                    .build();
        });
    }

    @Test
    void should_fail_if_databaseName_collectionName_missing() {

        CosmosClient cosmosClient = new CosmosClientBuilder()
                .endpoint("HOST")
                .key("MASTER_KEY")
                .consistencyLevel(ConsistencyLevel.EVENTUAL)
                .contentResponseOnWriteEnabled(true)
                .buildClient();

        assertThrows(IllegalArgumentException.class, () -> AzureCosmosDbNoSqlEmbeddingStore.builder()
                .cosmosClient(cosmosClient)
                .databaseName(null)
                .build());

        assertThrows(IllegalArgumentException.class, () -> AzureCosmosDbNoSqlEmbeddingStore.builder()
                .cosmosClient(cosmosClient)
                .databaseName("")
                .build());

        assertThrows(IllegalArgumentException.class, () -> AzureCosmosDbNoSqlEmbeddingStore.builder()
                .cosmosClient(cosmosClient)
                .databaseName("test_database")
                .containerName(null)
                .build());

        assertThrows(IllegalArgumentException.class, () -> AzureCosmosDbNoSqlEmbeddingStore.builder()
                .cosmosClient(cosmosClient)
                .databaseName("test_database")
                .containerName("")
                .build());
    }

    @Test
    void should_fail_if_cosmosVectorEmbeddingPolicy_missing() {
        CosmosClient cosmosClient = new CosmosClientBuilder()
                .endpoint("HOST")
                .key("MASTER_KEY")
                .consistencyLevel(ConsistencyLevel.EVENTUAL)
                .contentResponseOnWriteEnabled(true)
                .buildClient();

        CosmosVectorEmbeddingPolicy cosmosVectorEmbeddingPolicy = new CosmosVectorEmbeddingPolicy();

        assertThrows(IllegalArgumentException.class, () -> AzureCosmosDbNoSqlEmbeddingStore.builder()
                .cosmosClient(cosmosClient)
                .databaseName(DATABASE_NAME)
                .containerName(CONTAINER_NAME)
                .cosmosVectorEmbeddingPolicy(null)
                .build());


        cosmosVectorEmbeddingPolicy.setCosmosVectorEmbeddings(null);
        assertThrows(IllegalArgumentException.class, () -> AzureCosmosDbNoSqlEmbeddingStore.builder()
                .cosmosClient(cosmosClient)
                .databaseName(DATABASE_NAME)
                .containerName(CONTAINER_NAME)
                .cosmosVectorEmbeddingPolicy(cosmosVectorEmbeddingPolicy)
                .build());

        cosmosVectorEmbeddingPolicy.setCosmosVectorEmbeddings(new ArrayList<>());
        assertThrows(IllegalArgumentException.class, () -> AzureCosmosDbNoSqlEmbeddingStore.builder()
                .cosmosClient(cosmosClient)
                .databaseName(DATABASE_NAME)
                .containerName(CONTAINER_NAME)
                .cosmosVectorEmbeddingPolicy(cosmosVectorEmbeddingPolicy)
                .build());
    }

    @Test
    void should_fail_if_cosmosVectorIndexes_missing() {
        CosmosClient cosmosClient = new CosmosClientBuilder()
                .endpoint("HOST")
                .key("MASTER_KEY")
                .consistencyLevel(ConsistencyLevel.EVENTUAL)
                .contentResponseOnWriteEnabled(true)
                .buildClient();

        CosmosVectorEmbeddingPolicy cosmosVectorEmbeddingPolicy = populateCosmosVectorEmbeddingPolicy();

        assertThrows(IllegalArgumentException.class, () -> AzureCosmosDbNoSqlEmbeddingStore.builder()
                .cosmosClient(cosmosClient)
                .databaseName(DATABASE_NAME)
                .containerName(CONTAINER_NAME)
                .cosmosVectorEmbeddingPolicy(cosmosVectorEmbeddingPolicy)
                .cosmosVectorIndexes(null)
                .build());

        List<CosmosVectorIndexSpec> cosmosVectorIndexes = new ArrayList<CosmosVectorIndexSpec>();
        assertThrows(IllegalArgumentException.class, () -> AzureCosmosDbNoSqlEmbeddingStore.builder()
                .cosmosClient(cosmosClient)
                .databaseName(DATABASE_NAME)
                .containerName(CONTAINER_NAME)
                .cosmosVectorEmbeddingPolicy(cosmosVectorEmbeddingPolicy)
                .cosmosVectorIndexes(cosmosVectorIndexes)
                .build());
    }

    private CosmosVectorEmbeddingPolicy populateCosmosVectorEmbeddingPolicy() {
        CosmosVectorEmbeddingPolicy policy = new CosmosVectorEmbeddingPolicy();
        CosmosVectorEmbedding embedding1 = new CosmosVectorEmbedding();
        embedding1.setPath("/embedding");
        embedding1.setDataType(CosmosVectorDataType.FLOAT32);
        embedding1.setDimensions(128L);
        embedding1.setDistanceFunction(CosmosVectorDistanceFunction.COSINE);
        policy.setCosmosVectorEmbeddings(Collections.singletonList(embedding1));
        return policy;
    }

}
