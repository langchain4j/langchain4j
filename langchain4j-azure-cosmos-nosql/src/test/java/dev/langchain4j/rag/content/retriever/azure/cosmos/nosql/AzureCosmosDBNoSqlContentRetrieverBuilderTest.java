package dev.langchain4j.rag.content.retriever.azure.cosmos.nosql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.azure.core.credential.TokenCredential;
import com.azure.cosmos.CosmosAsyncClient;
import com.azure.cosmos.CosmosAsyncContainer;
import com.azure.cosmos.CosmosAsyncDatabase;
import com.azure.cosmos.models.CosmosContainerProperties;
import com.azure.cosmos.models.CosmosVectorDataType;
import com.azure.cosmos.models.CosmosVectorDistanceFunction;
import com.azure.cosmos.models.CosmosVectorEmbedding;
import com.azure.cosmos.models.CosmosVectorEmbeddingPolicy;
import com.azure.cosmos.models.CosmosVectorIndexSpec;
import com.azure.cosmos.models.CosmosVectorIndexType;
import com.azure.cosmos.models.IncludedPath;
import com.azure.cosmos.models.IndexingMode;
import com.azure.cosmos.models.IndexingPolicy;
import com.azure.cosmos.models.ThroughputProperties;
import dev.langchain4j.store.embedding.azure.cosmos.nosql.AzureCosmosDBSearchQueryType;
import dev.langchain4j.store.embedding.azure.cosmos.nosql.TestEmbeddingModel;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class AzureCosmosDBNoSqlContentRetrieverBuilderTest {

    private static final String DATABASE_NAME = "test_database_langchain_java";
    private static final String CONTAINER_NAME = "test_retriever_container";

    @Test
    void should_build_with_cosmos_async_client_without_endpoint_or_credentials() {
        CosmosAsyncClient cosmosAsyncClient = mockCosmosAsyncClient();

        AzureCosmosDBNoSqlContentRetriever contentRetriever = AzureCosmosDBNoSqlContentRetriever.builder()
                .cosmosAsyncClient(cosmosAsyncClient)
                .embeddingModel(new TestEmbeddingModel())
                .databaseName(DATABASE_NAME)
                .containerName(CONTAINER_NAME)
                .indexingPolicy(getIndexingPolicy())
                .cosmosVectorEmbeddingPolicy(getCosmosVectorEmbeddingPolicy())
                .searchQueryType(AzureCosmosDBSearchQueryType.VECTOR)
                .maxResults(10)
                .minScore(0.0)
                .build();

        assertThat(contentRetriever).isNotNull();
        verify(cosmosAsyncClient).createDatabaseIfNotExists(DATABASE_NAME);
    }

    @Test
    void should_prefer_cosmos_async_client_over_endpoint_and_credentials() {
        CosmosAsyncClient cosmosAsyncClient = mockCosmosAsyncClient();

        AzureCosmosDBNoSqlContentRetriever contentRetriever = AzureCosmosDBNoSqlContentRetriever.builder()
                .cosmosAsyncClient(cosmosAsyncClient)
                .endpoint("ignored")
                .apiKey("ignored")
                .tokenCredential(mock(TokenCredential.class))
                .embeddingModel(new TestEmbeddingModel())
                .databaseName(DATABASE_NAME)
                .containerName(CONTAINER_NAME)
                .indexingPolicy(getIndexingPolicy())
                .cosmosVectorEmbeddingPolicy(getCosmosVectorEmbeddingPolicy())
                .searchQueryType(AzureCosmosDBSearchQueryType.VECTOR)
                .maxResults(10)
                .minScore(0.0)
                .build();

        assertThat(contentRetriever).isNotNull();
        verify(cosmosAsyncClient).createDatabaseIfNotExists(DATABASE_NAME);
    }

    @Test
    void should_not_close_provided_cosmos_async_client() {
        CosmosAsyncClient cosmosAsyncClient = mockCosmosAsyncClient();

        AzureCosmosDBNoSqlContentRetriever contentRetriever = AzureCosmosDBNoSqlContentRetriever.builder()
                .cosmosAsyncClient(cosmosAsyncClient)
                .embeddingModel(new TestEmbeddingModel())
                .databaseName(DATABASE_NAME)
                .containerName(CONTAINER_NAME)
                .indexingPolicy(getIndexingPolicy())
                .cosmosVectorEmbeddingPolicy(getCosmosVectorEmbeddingPolicy())
                .searchQueryType(AzureCosmosDBSearchQueryType.VECTOR)
                .maxResults(10)
                .minScore(0.0)
                .build();

        contentRetriever.close();

        verify(cosmosAsyncClient, never()).close();
    }

    @Test
    void should_still_require_endpoint_when_cosmos_async_client_is_not_provided() {
        assertThatThrownBy(() -> AzureCosmosDBNoSqlContentRetriever.builder()
                        .embeddingModel(new TestEmbeddingModel())
                        .indexingPolicy(getIndexingPolicy())
                        .cosmosVectorEmbeddingPolicy(getCosmosVectorEmbeddingPolicy())
                        .searchQueryType(AzureCosmosDBSearchQueryType.VECTOR)
                        .maxResults(10)
                        .minScore(0.0)
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("endpoint cannot be null");
    }

    @Test
    void should_still_require_credentials_when_cosmos_async_client_is_not_provided() {
        assertThatThrownBy(() -> AzureCosmosDBNoSqlContentRetriever.builder()
                        .endpoint("https://example.documents.azure.com:443/")
                        .embeddingModel(new TestEmbeddingModel())
                        .indexingPolicy(getIndexingPolicy())
                        .cosmosVectorEmbeddingPolicy(getCosmosVectorEmbeddingPolicy())
                        .searchQueryType(AzureCosmosDBSearchQueryType.VECTOR)
                        .maxResults(10)
                        .minScore(0.0)
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("either keyCredential or tokenCredential must be set");
    }

    @Test
    void should_still_reject_both_credentials_when_cosmos_async_client_is_not_provided() {
        assertThatThrownBy(() -> AzureCosmosDBNoSqlContentRetriever.builder()
                        .endpoint("https://example.documents.azure.com:443/")
                        .apiKey("api-key")
                        .tokenCredential(mock(TokenCredential.class))
                        .embeddingModel(new TestEmbeddingModel())
                        .indexingPolicy(getIndexingPolicy())
                        .cosmosVectorEmbeddingPolicy(getCosmosVectorEmbeddingPolicy())
                        .searchQueryType(AzureCosmosDBSearchQueryType.VECTOR)
                        .maxResults(10)
                        .minScore(0.0)
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("either keyCredential or tokenCredential must be set");
    }

    private static CosmosAsyncClient mockCosmosAsyncClient() {
        CosmosAsyncClient cosmosAsyncClient = mock(CosmosAsyncClient.class);
        CosmosAsyncDatabase cosmosAsyncDatabase = mock(CosmosAsyncDatabase.class);
        CosmosAsyncContainer cosmosAsyncContainer = mock(CosmosAsyncContainer.class);

        when(cosmosAsyncClient.createDatabaseIfNotExists(DATABASE_NAME)).thenReturn(Mono.empty());
        when(cosmosAsyncClient.getDatabase(DATABASE_NAME)).thenReturn(cosmosAsyncDatabase);
        when(cosmosAsyncDatabase.createContainerIfNotExists(
                        any(CosmosContainerProperties.class), any(ThroughputProperties.class)))
                .thenReturn(Mono.empty());
        when(cosmosAsyncDatabase.getContainer(CONTAINER_NAME)).thenReturn(cosmosAsyncContainer);

        return cosmosAsyncClient;
    }

    private static IndexingPolicy getIndexingPolicy() {
        IndexingPolicy indexingPolicy = new IndexingPolicy();
        indexingPolicy.setIndexingMode(IndexingMode.CONSISTENT);
        IncludedPath includedPath = new IncludedPath("/*");
        indexingPolicy.setIncludedPaths(Collections.singletonList(includedPath));

        CosmosVectorIndexSpec cosmosVectorIndexSpec = new CosmosVectorIndexSpec();
        cosmosVectorIndexSpec.setPath("/embedding");
        cosmosVectorIndexSpec.setType(CosmosVectorIndexType.DISK_ANN.toString());
        indexingPolicy.setVectorIndexes(Collections.singletonList(cosmosVectorIndexSpec));

        return indexingPolicy;
    }

    private static CosmosVectorEmbeddingPolicy getCosmosVectorEmbeddingPolicy() {
        CosmosVectorEmbeddingPolicy vectorEmbeddingPolicy = new CosmosVectorEmbeddingPolicy();
        CosmosVectorEmbedding embedding = new CosmosVectorEmbedding();
        embedding.setPath("/embedding");
        embedding.setDataType(CosmosVectorDataType.FLOAT32);
        embedding.setEmbeddingDimensions(1536);
        embedding.setDistanceFunction(CosmosVectorDistanceFunction.COSINE);
        vectorEmbeddingPolicy.setCosmosVectorEmbeddings(List.of(embedding));
        return vectorEmbeddingPolicy;
    }
}
