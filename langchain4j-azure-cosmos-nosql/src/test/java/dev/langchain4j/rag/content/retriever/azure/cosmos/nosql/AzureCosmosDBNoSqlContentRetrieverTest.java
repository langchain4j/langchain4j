package dev.langchain4j.rag.content.retriever.azure.cosmos.nosql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import com.azure.core.credential.AzureKeyCredential;
import com.azure.core.credential.BasicAuthenticationCredential;
import com.azure.core.credential.TokenCredential;
import com.azure.cosmos.models.CosmosFullTextPolicy;
import com.azure.cosmos.models.CosmosVectorEmbeddingPolicy;
import com.azure.cosmos.models.IndexingPolicy;
import dev.langchain4j.store.embedding.azure.cosmos.nosql.AzureCosmosDBSearchQueryType;
import dev.langchain4j.store.embedding.azure.cosmos.nosql.TestEmbeddingModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldPassCustomFilterMapperToStore(boolean useTokenCredential) {
        AzureCosmosDBNoSqlFilterMapper mapper = filter -> "custom";
        AzureCosmosDBNoSqlContentRetriever.Builder builder = AzureCosmosDBNoSqlContentRetriever.builder()
                .endpoint(ENDPOINT)
                .embeddingModel(new TestEmbeddingModel())
                .maxResults(10)
                .minScore(0.0)
                .searchQueryType(AzureCosmosDBSearchQueryType.VECTOR)
                .filterMapper(mapper);
        if (useTokenCredential) {
            builder.tokenCredential(new BasicAuthenticationCredential("u", "p"));
        } else {
            builder.apiKey("dummy");
        }

        RecordingRetriever retriever = new RecordingRetriever(builder);

        assertThat(retriever.receivedFilterMapper).isSameAs(mapper);
    }

    private static class RecordingRetriever extends AzureCosmosDBNoSqlContentRetriever {

        private AzureCosmosDBNoSqlFilterMapper receivedFilterMapper;

        RecordingRetriever(Builder builder) {
            super(builder);
        }

        @Override
        protected void initialize(
                String endpoint,
                AzureKeyCredential keyCredential,
                TokenCredential tokenCredential,
                String databaseName,
                String containerName,
                String partitionKeyPath,
                IndexingPolicy indexingPolicy,
                CosmosVectorEmbeddingPolicy cosmosVectorEmbeddingPolicy,
                CosmosFullTextPolicy cosmosFullTextPolicy,
                Integer vectorStoreThroughput,
                AzureCosmosDBSearchQueryType searchQueryType,
                AzureCosmosDBNoSqlFilterMapper filterMapper) {
            this.receivedFilterMapper = filterMapper;
        }
    }
}
