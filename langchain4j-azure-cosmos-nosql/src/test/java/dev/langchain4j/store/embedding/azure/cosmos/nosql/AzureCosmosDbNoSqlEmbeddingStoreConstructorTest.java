package dev.langchain4j.store.embedding.azure.cosmos.nosql;

import static org.assertj.core.api.Assertions.assertThat;

import com.azure.core.credential.AzureKeyCredential;
import com.azure.core.credential.TokenCredential;
import com.azure.cosmos.models.CosmosFullTextPolicy;
import com.azure.cosmos.models.CosmosVectorEmbeddingPolicy;
import com.azure.cosmos.models.IndexingPolicy;
import dev.langchain4j.rag.content.retriever.azure.cosmos.nosql.AzureCosmosDBNoSqlFilterMapper;
import dev.langchain4j.store.embedding.filter.Filter;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

/**
 * Verifies that both constructors hand the caller-supplied filter mapper over to
 * {@link AbstractAzureCosmosDBNoSqlEmbeddingStore#initialize}. Connecting to Cosmos DB is avoided by
 * overriding {@code initialize}, so no credentials are required.
 */
class AzureCosmosDbNoSqlEmbeddingStoreConstructorTest {

    private static final AzureCosmosDBNoSqlFilterMapper CUSTOM_MAPPER = new AzureCosmosDBNoSqlFilterMapper() {
        @Override
        public String map(Filter filter) {
            return "custom";
        }
    };

    @Test
    void should_pass_filter_mapper_from_token_credential_constructor() {
        RecordingStore store = new RecordingStore("https://localhost", (TokenCredential) request -> Mono.empty());

        assertThat(store.receivedFilterMapper).isSameAs(CUSTOM_MAPPER);
    }

    @Test
    void should_pass_filter_mapper_from_key_credential_constructor() {
        RecordingStore store = new RecordingStore("https://localhost", new AzureKeyCredential("test-key"));

        assertThat(store.receivedFilterMapper).isSameAs(CUSTOM_MAPPER);
    }

    /** Records what the constructor forwards instead of opening a Cosmos DB client. */
    private static class RecordingStore extends AzureCosmosDbNoSqlEmbeddingStore {

        private AzureCosmosDBNoSqlFilterMapper receivedFilterMapper;

        RecordingStore(String endpoint, TokenCredential tokenCredential) {
            super(
                    endpoint,
                    tokenCredential,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    AzureCosmosDBSearchQueryType.VECTOR,
                    CUSTOM_MAPPER);
        }

        RecordingStore(String endpoint, AzureKeyCredential keyCredential) {
            super(
                    endpoint,
                    keyCredential,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    AzureCosmosDBSearchQueryType.VECTOR,
                    CUSTOM_MAPPER);
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
