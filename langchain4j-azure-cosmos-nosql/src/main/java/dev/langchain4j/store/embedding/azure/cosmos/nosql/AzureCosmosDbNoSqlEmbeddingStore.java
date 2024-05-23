package dev.langchain4j.store.embedding.azure.cosmos.nosql;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.models.*;
import com.azure.cosmos.util.CosmosPagedIterable;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.Builder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static dev.langchain4j.internal.Utils.*;
import static dev.langchain4j.internal.ValidationUtils.ensureTrue;
import static dev.langchain4j.store.embedding.azure.cosmos.nosql.MappingUtils.toNoSqlDbDocument;
import static java.util.Collections.singletonList;

/**
 * You can read more about vector search using Azure Cosmos DB NoSQL
 * <a href="https://aka.ms/CosmosVectorSearch">here</a>.
 */
public class AzureCosmosDbNoSqlEmbeddingStore implements EmbeddingStore<TextSegment> {

    private static final Logger log = LoggerFactory.getLogger(AzureCosmosDbNoSqlEmbeddingStore.class);

    private final CosmosClient cosmosClient;
    private final String databaseName;
    private final String containerName;
    private final CosmosVectorEmbeddingPolicy cosmosVectorEmbeddingPolicy;
    private final List<CosmosVectorIndexSpec> cosmosVectorIndexes;
    private final CosmosContainerProperties containerProperties;
    private final String embeddingKey;
    private final CosmosDatabase database;
    private final CosmosContainer container;

    @Builder
    public AzureCosmosDbNoSqlEmbeddingStore(CosmosClient cosmosClient,
                                            String databaseName,
                                            String containerName,
                                            CosmosVectorEmbeddingPolicy cosmosVectorEmbeddingPolicy,
                                            List<CosmosVectorIndexSpec> cosmosVectorIndexes,
                                            CosmosContainerProperties containerProperties) {
        this.cosmosClient = cosmosClient;
        this.databaseName = databaseName;
        this.containerName = containerName;
        this.cosmosVectorEmbeddingPolicy = cosmosVectorEmbeddingPolicy;
        this.cosmosVectorIndexes = cosmosVectorIndexes;
        this.containerProperties = containerProperties;

        if (cosmosClient == null) {
            throw new IllegalArgumentException("cosmosClient cannot be null or empty for Azure CosmosDB NoSql Embedding Store.");
        }

        if (isNullOrBlank(databaseName) || isNullOrBlank(containerName)) {
            throw new IllegalArgumentException("databaseName and containerName needs to be provided.");
        }

        if (cosmosVectorEmbeddingPolicy == null || cosmosVectorEmbeddingPolicy.getVectorEmbeddings() == null ||
                cosmosVectorEmbeddingPolicy.getVectorEmbeddings().isEmpty()) {
            throw new IllegalArgumentException("cosmosVectorEmbeddingPolicy cannot be null or empty for Azure CosmosDB NoSql Embedding Store.");
        }

        if (cosmosVectorIndexes == null || cosmosVectorIndexes.isEmpty()) {
            throw new IllegalArgumentException("cosmosVectorIndexes cannot be null or empty for Azure CosmosDB NoSql Embedding Store.");
        }

        this.cosmosClient.createDatabaseIfNotExists(this.databaseName);
        this.database = this.cosmosClient.getDatabase(this.databaseName);

        containerProperties.setVectorEmbeddingPolicy(this.cosmosVectorEmbeddingPolicy);
        containerProperties.getIndexingPolicy().setVectorIndexes(this.cosmosVectorIndexes);

        this.database.createContainerIfNotExists(this.containerProperties);
        this.container = this.database.getContainer(this.containerName);

        this.embeddingKey = this.cosmosVectorEmbeddingPolicy.getVectorEmbeddings().get(0).getPath().substring(1);
    }

    @Override
    public String add(Embedding embedding) {
        String id = randomUUID();
        add(id, embedding);
        return id;
    }

    @Override
    public void add(String id, Embedding embedding) {
        addInternal(id, embedding, null);
    }

    @Override
    public String add(Embedding embedding, TextSegment textSegment) {
        String id = randomUUID();
        addInternal(id, embedding, textSegment);
        return id;
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings) {
        List<String> ids = embeddings.stream()
                .map(ignored -> randomUUID())
                .collect(Collectors.toList());
        addAllInternal(ids, embeddings, null);
        return ids;
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings, List<TextSegment> embedded) {
        List<String> ids = embeddings.stream()
                .map(ignored -> randomUUID())
                .collect(Collectors.toList());
        addAllInternal(ids, embeddings, embedded);
        return ids;
    }

    @Override
    public List<EmbeddingMatch<TextSegment>> findRelevant(Embedding referenceEmbedding, int maxResults, double minScore) {
        String referenceEmbeddingString = referenceEmbedding.vectorAsList().stream()
                .map(Object::toString)
                .collect(Collectors.joining(","));

        String query = String.format("SELECT TOP %d c.id, c.%s, c.text, c.metadata, VectorDistance(c.%s,[%s]) AS score FROM c ORDER By " +
                "VectorDistance(c.%s,[%s])", maxResults, embeddingKey, embeddingKey, referenceEmbeddingString, embeddingKey, referenceEmbeddingString);

        CosmosPagedIterable<AzureCosmosDbNoSqlMatchedDocument> results = this.container.queryItems(query,
                new CosmosQueryRequestOptions(), AzureCosmosDbNoSqlMatchedDocument.class);

        if (!results.stream().findAny().isPresent()) {
            return new ArrayList<>();
        }
        return results.stream()
                .map(MappingUtils::toEmbeddingMatch)
                .collect(Collectors.toList());
    }

    private void addInternal(String id, Embedding embedding, TextSegment embedded) {
        addAllInternal(singletonList(id), singletonList(embedding), embedded == null ? null : singletonList(embedded));
    }

    private void addAllInternal(List<String> ids, List<Embedding> embeddings, List<TextSegment> embedded) {
        if (isNullOrEmpty(ids) || isNullOrEmpty(embeddings)) {
            log.info("do not add empty embeddings to Azure CosmosDB NoSQL");
            return;
        }

        ensureTrue(ids.size() == embeddings.size(), "ids size is not equal to embeddings size");
        ensureTrue(embedded == null || embeddings.size() == embedded.size(), "embeddings size is not equal to embedded size");

        List<CosmosItemOperation> operations = new ArrayList<>(ids.size());
        for (int i = 0; i < ids.size(); i++) {
            operations.add(CosmosBulkOperations.getCreateItemOperation(
                    toNoSqlDbDocument(ids.get(i), embeddings.get(i), embedded == null ? null : embedded.get(i)),
                    new PartitionKey(ids.get(i))));
        }

        this.container.executeBulkOperations(operations);
    }
}
