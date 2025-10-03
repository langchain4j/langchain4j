package dev.langchain4j.rag.content.retriever.azure.cosmos.nosql;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import com.azure.core.credential.BasicAuthenticationCredential;
import com.azure.core.credential.TokenCredential;
import dev.langchain4j.store.embedding.azure.cosmos.nosql.AzureCosmosDBSearchQueryType;
import dev.langchain4j.store.embedding.azure.cosmos.nosql.TestEmbeddingModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "AZURE_COSMOS_HOST", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AZURE_COSMOS_MASTER_KEY", matches = ".+")
class AzureCosmosDBNoSqlContentRetrieverTest {

    private static final String ENDPOINT = "https://example.documents.azure.com:443/";
    private static final String DB = "test_db";
    private static final String CONTAINER = "test_container";

    @Test
    void shouldFailIfEndpointNull() {
        assertThatThrownBy(() -> AzureCosmosDBNoSqlContentRetriever.builder()
                        .endpoint(null)
                        .apiKey("dummy")
                        .embeddingModel(new TestEmbeddingModel())
                        .databaseName(DB)
                        .containerName(CONTAINER)
                        .partitionKeyPath("/id")
                        .searchQueryType(AzureCosmosDBSearchQueryType.VECTOR)
                        .maxResults(10)
                        .minScore(0.0)
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("endpoint cannot be null");
    }

    @Test
    void shouldFailIfBothCredentialsPresent() {
        TokenCredential token = new BasicAuthenticationCredential("u", "p");
        assertThatThrownBy(() -> AzureCosmosDBNoSqlContentRetriever.builder()
                        .endpoint(ENDPOINT)
                        .apiKey("dummy")
                        .tokenCredential(token)
                        .embeddingModel(new TestEmbeddingModel())
                        .databaseName(DB)
                        .containerName(CONTAINER)
                        .partitionKeyPath("/id")
                        .searchQueryType(AzureCosmosDBSearchQueryType.VECTOR)
                        .maxResults(10)
                        .minScore(0.0)
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("either keyCredential or tokenCredential must be set");
    }

    @Test
    void shouldFailIfNoCredential() {
        assertThatThrownBy(() -> AzureCosmosDBNoSqlContentRetriever.builder()
                        .endpoint(ENDPOINT)
                        .embeddingModel(new TestEmbeddingModel())
                        .databaseName(DB)
                        .containerName(CONTAINER)
                        .partitionKeyPath("/id")
                        .searchQueryType(AzureCosmosDBSearchQueryType.VECTOR)
                        .maxResults(10)
                        .minScore(0.0)
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("either keyCredential or tokenCredential must be set");
    }
}
