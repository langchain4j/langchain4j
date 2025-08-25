package dev.langchain4j.store.embedding.azure.cosmos.nosql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "AZURE_COSMOS_HOST", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AZURE_COSMOS_MASTER_KEY", matches = ".+")
class AzureCosmosDbNoSqlEmbeddingStoreTest {

    private static final String DATABASE_NAME = "test_database_langchain_java";
    private static final String CONTAINER_NAME = "test_embedding_container";

    @Test
    void should_fail_if_endpoint_missing() {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> {
            AzureCosmosDbNoSqlEmbeddingStore.builder().endpoint(null).build();
        });
    }

    @Test
    void should_fail_if_apiKey_tokenCredential_missing() {

        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> AzureCosmosDbNoSqlEmbeddingStore.builder()
                        .endpoint(System.getenv("AZURE_COSMOS_HOST"))
                        .apiKey(null)
                        .build());

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> AzureCosmosDbNoSqlEmbeddingStore.builder()
                        .endpoint(System.getenv("AZURE_COSMOS_HOST"))
                        .tokenCredential(null)
                        .build());
    }

    @Test
    void should_build_successfully_with_valid_parameters() {
        AzureCosmosDbNoSqlEmbeddingStore store = AzureCosmosDbNoSqlEmbeddingStore.builder()
                .endpoint(System.getenv("AZURE_COSMOS_HOST"))
                .apiKey(System.getenv("AZURE_COSMOS_MASTER_KEY"))
                .databaseName(DATABASE_NAME)
                .containerName(CONTAINER_NAME)
                .searchQueryType(AzureCosmosDBSearchQueryType.VECTOR)
                .build();

        assertThat(store).isNotNull();
    }
}
