package dev.langchain4j.store.embedding.azure.cosmos.nosql;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.internal.ValidationUtils.ensureTrue;

import com.azure.core.credential.AzureKeyCredential;
import com.azure.core.credential.TokenCredential;
import com.azure.cosmos.models.CosmosFullTextPolicy;
import com.azure.cosmos.models.CosmosVectorEmbeddingPolicy;
import com.azure.cosmos.models.IndexingPolicy;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.retriever.azure.cosmos.nosql.AzureCosmosDBNoSqlFilterMapper;
import dev.langchain4j.store.embedding.EmbeddingStore;

/**
 * Implementation of {@link EmbeddingStore} that uses Azure Cosmos DB NoSQL API for storing and retrieving embeddings.
 * This store provides vector search capabilities using Cosmos DB's vector search functionality.
 * <p>
 * You can read more about vector search using Azure Cosmos DB NoSQL
 * <a href="https://aka.ms/CosmosVectorSearch">here</a>.
 */
public class AzureCosmosDbNoSqlEmbeddingStore extends AbstractAzureCosmosDBNoSqlEmbeddingStore
        implements EmbeddingStore<TextSegment> {

    public AzureCosmosDbNoSqlEmbeddingStore(
            String endpoint,
            AzureKeyCredential keyCredential,
            String databaseName,
            String containerName,
            String partitionKeyPath,
            IndexingPolicy indexingPolicy,
            CosmosVectorEmbeddingPolicy cosmosVectorEmbeddingPolicy,
            CosmosFullTextPolicy cosmosFullTextPolicy,
            Integer vectorStoreThroughput,
            AzureCosmosDBSearchQueryType azureCosmosDBSearchQueryType,
            AzureCosmosDBNoSqlFilterMapper filterMapper) {
        this.initialize(
                endpoint,
                keyCredential,
                null,
                databaseName,
                containerName,
                partitionKeyPath,
                indexingPolicy,
                cosmosVectorEmbeddingPolicy,
                cosmosFullTextPolicy,
                vectorStoreThroughput,
                azureCosmosDBSearchQueryType,
                filterMapper);
    }

    public AzureCosmosDbNoSqlEmbeddingStore(
            String endpoint,
            TokenCredential tokenCredential,
            String databaseName,
            String containerName,
            String partitionKeyPath,
            IndexingPolicy indexingPolicy,
            CosmosVectorEmbeddingPolicy cosmosVectorEmbeddingPolicy,
            CosmosFullTextPolicy cosmosFullTextPolicy,
            Integer vectorStoreThroughput,
            AzureCosmosDBSearchQueryType azureCosmosDBSearchQueryType,
            AzureCosmosDBNoSqlFilterMapper filterMappe) {
        this.initialize(
                endpoint,
                null,
                tokenCredential,
                databaseName,
                containerName,
                partitionKeyPath,
                indexingPolicy,
                cosmosVectorEmbeddingPolicy,
                cosmosFullTextPolicy,
                vectorStoreThroughput,
                azureCosmosDBSearchQueryType,
                filterMapper);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String endpoint;
        private TokenCredential tokenCredential;
        private AzureKeyCredential keyCredential;
        private String databaseName;
        private String containerName;
        private String partitionKeyPath;
        private IndexingPolicy indexingPolicy;
        private CosmosVectorEmbeddingPolicy cosmosVectorEmbeddingPolicy;
        private CosmosFullTextPolicy cosmosFullTextPolicy;
        private Integer vectorStoreThroughput;
        private AzureCosmosDBSearchQueryType searchQueryType;
        private AzureCosmosDBNoSqlFilterMapper filterMapper;

        /**
         * Sets the Cosmos DB endpoint.
         *
         * @param endpoint the Cosmos DB endpoint
         * @return this builder instance
         */
        public Builder endpoint(String endpoint) {
            this.endpoint = endpoint;
            return this;
        }

        /**
         * Sets the Azure AI Search API key.
         *
         * @param apiKey The Azure AI Search API key.
         * @return builder
         */
        public Builder apiKey(String apiKey) {
            this.keyCredential = new AzureKeyCredential(apiKey);
            return this;
        }

        /**
         * Used to authenticate to Azure OpenAI with Azure Active Directory credentials.
         *
         * @param tokenCredential the credentials to authenticate with Azure Active Directory
         * @return builder
         */
        public Builder tokenCredential(TokenCredential tokenCredential) {
            this.tokenCredential = tokenCredential;
            return this;
        }

        /**
         * Sets the database name.
         *
         * @param databaseName the database name
         * @return this builder instance
         */
        public Builder databaseName(String databaseName) {
            this.databaseName = databaseName;
            return this;
        }

        /**
         * Sets the container name.
         *
         * @param containerName the container name
         * @return this builder instance
         */
        public Builder containerName(String containerName) {
            this.containerName = containerName;
            return this;
        }

        public Builder partitionKeyPath(String partitionKeyPath) {
            this.partitionKeyPath = partitionKeyPath;
            return this;
        }

        public Builder indexingPolicy(IndexingPolicy indexingPolicy) {
            this.indexingPolicy = indexingPolicy;
            return this;
        }

        public Builder cosmosVectorEmbeddingPolicy(CosmosVectorEmbeddingPolicy cosmosVectorEmbeddingPolicy) {
            this.cosmosVectorEmbeddingPolicy = cosmosVectorEmbeddingPolicy;
            return this;
        }

        public Builder cosmosFullTextPolicy(String cosmosFullTextPolicy) {
            this.cosmosFullTextPolicy = new CosmosFullTextPolicy();
            return this;
        }

        public Builder vectorStoreThroughput(int vectorStoreThroughput) {
            this.vectorStoreThroughput = vectorStoreThroughput;
            return this;
        }

        public Builder searchQueryType(AzureCosmosDBSearchQueryType searchQueryType) {
            this.searchQueryType = searchQueryType;
            return this;
        }

        public Builder filterMapper(AzureCosmosDBNoSqlFilterMapper filterMapper) {
            this.filterMapper = filterMapper;
            return this;
        }

        /**
         * Builds a new {@link AzureCosmosDbNoSqlEmbeddingStore} instance with the configured properties.
         *
         * @return a new AzureCosmosDbNoSqlEmbeddingStore instance
         */
        public AzureCosmosDbNoSqlEmbeddingStore build() {
            ensureNotNull(endpoint, "endpoint");
            ensureTrue(
                    keyCredential != null || tokenCredential != null, "either apiKey or tokenCredential must be set");

            if (keyCredential != null) {
                return new AzureCosmosDbNoSqlEmbeddingStore(
                        this.endpoint,
                        this.keyCredential,
                        this.databaseName,
                        this.containerName,
                        this.partitionKeyPath,
                        this.indexingPolicy,
                        this.cosmosVectorEmbeddingPolicy,
                        this.cosmosFullTextPolicy,
                        this.vectorStoreThroughput,
                        this.searchQueryType,
                        this.filterMapper);
            } else {
                return new AzureCosmosDbNoSqlEmbeddingStore(
                        this.endpoint,
                        this.tokenCredential,
                        this.databaseName,
                        this.containerName,
                        this.partitionKeyPath,
                        this.indexingPolicy,
                        this.cosmosVectorEmbeddingPolicy,
                        this.cosmosFullTextPolicy,
                        this.vectorStoreThroughput,
                        this.searchQueryType,
                        this.filterMapper);
            }
        }
    }
}
