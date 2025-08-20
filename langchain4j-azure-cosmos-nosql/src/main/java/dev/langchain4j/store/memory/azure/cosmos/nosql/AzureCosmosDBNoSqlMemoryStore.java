package dev.langchain4j.store.memory.azure.cosmos.nosql;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.internal.ValidationUtils.ensureTrue;
import static java.util.Collections.singletonList;

import com.azure.core.credential.AzureKeyCredential;
import com.azure.core.credential.TokenCredential;
import com.azure.cosmos.CosmosAsyncClient;
import com.azure.cosmos.CosmosAsyncContainer;
import com.azure.cosmos.CosmosAsyncDatabase;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.implementation.guava25.collect.ImmutableList;
import com.azure.cosmos.models.CosmosContainerProperties;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.ExcludedPath;
import com.azure.cosmos.models.IncludedPath;
import com.azure.cosmos.models.IndexingMode;
import com.azure.cosmos.models.IndexingPolicy;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.models.SqlParameter;
import com.azure.cosmos.models.SqlQuerySpec;
import com.azure.cosmos.models.ThroughputProperties;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.store.embedding.azure.cosmos.nosql.AzureCosmosDBNoSqlRuntimeException;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AzureCosmosDBNoSqlMemoryStore implements ChatMemoryStore {
    protected static final String USER_AGENT = "LangChain4J-CDBNoSql-MemoryStore-Java";
    protected static final String DEFAULT_DATABASE_NAME = "default_db";
    protected static final String DEFAULT_CONTAINER_NAME = "default_container";
    protected static final Integer DEFAULT_THROUGHPUT = 400;
    protected static final String DEFAULT_PARTITION_KEY_PATH = "/id";

    private static final Logger logger = LoggerFactory.getLogger(AzureCosmosDBNoSqlMemoryStore.class);

    private CosmosAsyncClient cosmosClient;
    private String databaseName;
    private String containerName;
    private String partitionKeyPath;
    private Integer vectorStoreThroughput;
    private CosmosAsyncContainer container;

    AzureCosmosDBNoSqlMemoryStore(
            String endpoint,
            TokenCredential tokenCredential,
            String databaseName,
            String containerName,
            Integer vectorStoreThroughput) {
        this(endpoint, null, tokenCredential, databaseName, containerName, vectorStoreThroughput);
    }

    AzureCosmosDBNoSqlMemoryStore(
            String endpoint,
            AzureKeyCredential keyCredential,
            String databaseName,
            String containerName,
            Integer vectorStoreThroughput) {
        this(endpoint, keyCredential, null, databaseName, containerName, vectorStoreThroughput);
    }

    AzureCosmosDBNoSqlMemoryStore(
            String endpoint,
            AzureKeyCredential keyCredential,
            TokenCredential tokenCredential,
            String databaseName,
            String containerName,
            Integer vectorStoreThroughput) {
        ensureNotNull(endpoint, "%s", "cosmosClient cannot be null or empty for Azure CosmosDB NoSql Embedding Store.");
        try {
            if (keyCredential != null) {
                this.cosmosClient = new CosmosClientBuilder()
                        .endpoint(endpoint)
                        .credential(keyCredential)
                        .userAgentSuffix(USER_AGENT)
                        .buildAsyncClient();
            } else {
                this.cosmosClient = new CosmosClientBuilder()
                        .endpoint(endpoint)
                        .credential(tokenCredential)
                        .userAgentSuffix(USER_AGENT)
                        .buildAsyncClient();
            }

        } catch (Exception e) {
            logger.error("Error creating cosmosClient: {}", e.getMessage());
        }

        this.databaseName = getOrDefault(databaseName, DEFAULT_DATABASE_NAME);
        this.containerName = getOrDefault(containerName, DEFAULT_CONTAINER_NAME);

        try {
            this.cosmosClient.createDatabaseIfNotExists(this.databaseName).block();
        } catch (Exception e) {
            // likely failed due to RBAC, so database is assumed to be already created
            // (and if not, it will fail later)
            logger.error("Error creating database: {}", e.getMessage());
        }

        this.partitionKeyPath = DEFAULT_PARTITION_KEY_PATH;
        this.vectorStoreThroughput = getOrDefault(vectorStoreThroughput, DEFAULT_THROUGHPUT);

        CosmosContainerProperties collectionDefinition =
                new CosmosContainerProperties(this.containerName, this.partitionKeyPath);
        IndexingPolicy indexingPolicy = getIndexingPolicy();
        collectionDefinition.setIndexingPolicy(indexingPolicy);

        ThroughputProperties throughputProperties =
                ThroughputProperties.createManualThroughput(this.vectorStoreThroughput);
        CosmosAsyncDatabase cosmosAsyncDatabase = this.cosmosClient.getDatabase(this.databaseName);
        cosmosAsyncDatabase
                .createContainerIfNotExists(collectionDefinition, throughputProperties)
                .block();
        this.container = cosmosAsyncDatabase.getContainer(this.containerName);
    }

    private IndexingPolicy getIndexingPolicy() {
        IndexingPolicy indexingPolicy = new IndexingPolicy();
        indexingPolicy.setIndexingMode(IndexingMode.CONSISTENT);
        ExcludedPath excludedPath = new ExcludedPath("/*");
        indexingPolicy.setExcludedPaths(singletonList(excludedPath));
        IncludedPath includedPath1 = new IncludedPath("/metadata/?");
        IncludedPath includedPath2 = new IncludedPath("/content/?");
        indexingPolicy.setIncludedPaths(ImmutableList.of(includedPath1, includedPath2));
        return indexingPolicy;
    }

    @Override
    public List<ChatMessage> getMessages(final Object memoryId) {
        try {
            String query = "SELECT * FROM c WHERE c.id = @id";
            List<SqlParameter> parameters = new ArrayList<>();
            parameters.add(new SqlParameter("@id", memoryId));
            SqlQuerySpec sqlQuerySpec = new SqlQuerySpec(query, parameters);
            return Objects.requireNonNull(this.container
                            .queryItems(sqlQuerySpec, ChatMessage.class)
                            .byPage(1)
                            .blockFirst())
                    .getResults();
        } catch (Exception e) {
            throw new AzureCosmosDBNoSqlRuntimeException("Exception while fetching documents: {}", e);
        }
    }

    @Override
    public void updateMessages(final Object memoryId, final List<ChatMessage> messages) {
        try {
            deleteMessages(memoryId);
            this.container
                    .upsertItem(messages.get(0), new PartitionKey(memoryId), new CosmosItemRequestOptions())
                    .block();
        } catch (Exception e) {
            throw new AzureCosmosDBNoSqlRuntimeException("Exception while updating documents: {}", e);
        }
    }

    @Override
    public void deleteMessages(final Object memoryId) {
        try {
            this.container
                    .deleteItem(memoryId.toString(), new PartitionKey(memoryId))
                    .block();
        } catch (Exception e) {
            throw new AzureCosmosDBNoSqlRuntimeException("Exception while deleting documents: {}", e);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String endpoint;
        private AzureKeyCredential keyCredential;
        private TokenCredential tokenCredential;
        private String databaseName;
        private String containerName;
        private Integer vectorStoreThroughput;

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

        public Builder vectorStoreThroughput(int vectorStoreThroughput) {
            this.vectorStoreThroughput = vectorStoreThroughput;
            return this;
        }

        public AzureCosmosDBNoSqlMemoryStore build() {
            ensureNotNull(endpoint, "endpoint");
            ensureTrue(
                    keyCredential != null || tokenCredential != null, "either apiKey or tokenCredential must be set");

            if (keyCredential != null) {
                return new AzureCosmosDBNoSqlMemoryStore(
                        this.endpoint,
                        this.keyCredential,
                        this.databaseName,
                        this.containerName,
                        this.vectorStoreThroughput);
            } else {
                return new AzureCosmosDBNoSqlMemoryStore(
                        this.endpoint,
                        this.tokenCredential,
                        this.databaseName,
                        this.containerName,
                        this.vectorStoreThroughput);
            }
        }
    }
}
