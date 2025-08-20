package dev.langchain4j.store.embedding.azure.cosmos.nosql;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.Utils.randomUUID;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.store.embedding.azure.cosmos.nosql.MappingUtils.toNoSqlDbDocument;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

import com.azure.core.credential.AzureKeyCredential;
import com.azure.core.credential.TokenCredential;
import com.azure.cosmos.CosmosAsyncClient;
import com.azure.cosmos.CosmosAsyncContainer;
import com.azure.cosmos.CosmosAsyncDatabase;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosException;
import com.azure.cosmos.implementation.apachecommons.lang.tuple.ImmutablePair;
import com.azure.cosmos.models.CosmosBulkOperations;
import com.azure.cosmos.models.CosmosContainerProperties;
import com.azure.cosmos.models.CosmosFullTextPolicy;
import com.azure.cosmos.models.CosmosItemOperation;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.CosmosVectorEmbeddingPolicy;
import com.azure.cosmos.models.IndexingPolicy;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.models.PartitionKeyDefinition;
import com.azure.cosmos.models.PartitionKind;
import com.azure.cosmos.models.SqlParameter;
import com.azure.cosmos.models.SqlQuerySpec;
import com.azure.cosmos.models.ThroughputProperties;
import com.azure.cosmos.util.CosmosPagedFlux;
import com.fasterxml.jackson.databind.JsonNode;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.retriever.azure.cosmos.nosql.AzureCosmosDBNoSqlFilterMapper;
import dev.langchain4j.rag.content.retriever.azure.cosmos.nosql.DefaultAzureCosmosDBNoSqlFilterMapper;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.RelevanceScore;
import dev.langchain4j.store.embedding.filter.Filter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

public class AbstractAzureCosmosDBNoSqlEmbeddingStore implements EmbeddingStore<TextSegment> {

    protected static final String USER_AGENT = "LangChain4J-CDBNoSql-VectorStore-Java";
    protected static final String DEFAULT_DATABASE_NAME = "default_db";
    protected static final String DEFAULT_CONTAINER_NAME = "default_container";
    protected static final Integer DEFAULT_THROUGHPUT = 400;
    protected static final String DEFAULT_PARTITION_KEY_PATH = "/id";
    protected static final AzureCosmosDBSearchQueryType DEFAULT_SEARCH_QUERY_TYPE = AzureCosmosDBSearchQueryType.VECTOR;
    protected static final String DEFAULT_VECTOR_INDEX_PATH = "/embedding";
    protected static final Integer DEFAULT_VECTOR_DIMENSIONS = 1536;
    protected static final String DEFAULT_VECTOR_INDEX_TYPE = "diskANN";
    protected static final String DEFAULT_VECTOR_DATA_TYPE = "float32";
    protected static final String DEFAULT_VECTOR_DISTANCE_FUNCTION = "cosine";
    protected static final String DEFAULT_FULL_TEXT_INDEX_PATH = "/text";
    protected static final String DEFAULT_FULL_TEXT_INDEX_LANGUAGE = "en-US";

    private static final Logger logger = LoggerFactory.getLogger(AbstractAzureCosmosDBNoSqlEmbeddingStore.class);
    protected AzureCosmosDBNoSqlFilterMapper filterMapper;
    private CosmosAsyncClient cosmosClient;
    private String databaseName;
    private String containerName;
    private String partitionKeyPath;
    private Integer vectorStoreThroughput;
    private IndexingPolicy indexingPolicy;
    private CosmosVectorEmbeddingPolicy cosmosVectorEmbeddingPolicy;
    private CosmosFullTextPolicy cosmosFullTextPolicy;
    private AzureCosmosDBSearchQueryType searchQueryType;
    private CosmosAsyncContainer container;

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
        ensureNotNull(endpoint, "%s", "cosmosClient cannot be null or empty for Azure CosmosDB NoSql Embedding Store.");

        if (filterMapper == null) {
            this.filterMapper = new DefaultAzureCosmosDBNoSqlFilterMapper();
        } else {
            this.filterMapper = filterMapper;
        }
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
            throw new RuntimeException("Error creating cosmosClient: {}", e);
        }

        this.databaseName = getOrDefault(databaseName, DEFAULT_DATABASE_NAME);
        this.containerName = getOrDefault(containerName, DEFAULT_CONTAINER_NAME);

        this.cosmosClient.createDatabaseIfNotExists(this.databaseName).block();

        this.partitionKeyPath = getOrDefault(partitionKeyPath, DEFAULT_PARTITION_KEY_PATH);

        this.vectorStoreThroughput = getOrDefault(vectorStoreThroughput, DEFAULT_THROUGHPUT);

        // handle hierarchical partition key
        PartitionKeyDefinition subPartitionKeyDefinition = new PartitionKeyDefinition();
        List<String> pathsFromCommaSeparatedList = new ArrayList<>();
        String[] subPartitionKeyPaths = this.partitionKeyPath.split(",");
        Collections.addAll(pathsFromCommaSeparatedList, subPartitionKeyPaths);
        if (subPartitionKeyPaths.length > 1) {
            subPartitionKeyDefinition.setPaths(pathsFromCommaSeparatedList);
            subPartitionKeyDefinition.setKind(PartitionKind.MULTI_HASH);
        } else {
            subPartitionKeyDefinition.setPaths(singletonList(this.partitionKeyPath));
            subPartitionKeyDefinition.setKind(PartitionKind.HASH);
        }

        this.searchQueryType = getOrDefault(searchQueryType, DEFAULT_SEARCH_QUERY_TYPE);

        if (indexingPolicy == null) {
            throw new AzureCosmosDBNoSqlRuntimeException("indexingPolicy is required");
        }
        switch (searchQueryType) {
            case VECTOR -> {
                if (cosmosVectorEmbeddingPolicy == null
                        || indexingPolicy.getVectorIndexes().isEmpty())
                    throw new AzureCosmosDBNoSqlRuntimeException(
                            "cosmosVectorEmbeddingPolicy is required for VECTOR search");
            }
            case FULL_TEXT_SEARCH, FULL_TEXT_RANKING -> {
                if (cosmosFullTextPolicy == null
                        || indexingPolicy.getCosmosFullTextIndexes().isEmpty())
                    throw new AzureCosmosDBNoSqlRuntimeException(
                            "cosmosFullTextPolicy is required for FULL_TEXT_* search");
            }
            case HYBRID -> {
                List<String> missing = new ArrayList<>();
                if (cosmosVectorEmbeddingPolicy == null) missing.add("cosmosVectorEmbeddingPolicy");
                if (cosmosFullTextPolicy == null) missing.add("cosmosFullTextPolicy");
                if (indexingPolicy.getVectorIndexes().isEmpty()) missing.add("vectorIndexes");
                if (indexingPolicy.getCosmosFullTextIndexes().isEmpty()) missing.add("fullTextIndexes");
                if (!missing.isEmpty())
                    throw new AzureCosmosDBNoSqlRuntimeException("Missing for HYBRID: " + String.join(", ", missing));
            }
        }
        CosmosContainerProperties collectionDefinition =
                new CosmosContainerProperties(this.containerName, subPartitionKeyDefinition);
        collectionDefinition.setIndexingPolicy(indexingPolicy);

        switch (this.searchQueryType) {
            case VECTOR -> collectionDefinition.setVectorEmbeddingPolicy(cosmosVectorEmbeddingPolicy);
            case FULL_TEXT_SEARCH, FULL_TEXT_RANKING -> collectionDefinition.setFullTextPolicy(cosmosFullTextPolicy);
            case HYBRID -> {
                collectionDefinition.setVectorEmbeddingPolicy(cosmosVectorEmbeddingPolicy);
                collectionDefinition.setFullTextPolicy(cosmosFullTextPolicy);
            }
        }

        ThroughputProperties throughputProperties =
                ThroughputProperties.createManualThroughput(this.vectorStoreThroughput);
        CosmosAsyncDatabase cosmosAsyncDatabase = this.cosmosClient.getDatabase(this.databaseName);
        cosmosAsyncDatabase
                .createContainerIfNotExists(collectionDefinition, throughputProperties)
                .block();
        this.container = cosmosAsyncDatabase.getContainer(this.containerName);
    }

    @Override
    public String add(final Embedding embedding) {
        String id = randomUUID();
        add(id, embedding);
        return id;
    }

    @Override
    public void add(final String id, final Embedding embedding) {
        addAll(singletonList(id), singletonList(embedding), null);
    }

    @Override
    public String add(final Embedding embedding, final TextSegment textSegment) {
        String id = randomUUID();
        addAll(singletonList(id), singletonList(embedding), singletonList(textSegment));
        return id;
    }

    public String add(final TextSegment textSegment) {
        String id = randomUUID();
        addAll(singletonList(id), null, singletonList(textSegment));
        return id;
    }

    @Override
    public List<String> addAll(final List<Embedding> embeddings) {
        List<String> ids = embeddings.stream().map(ignored -> randomUUID()).collect(toList());
        addAll(ids, embeddings, null);
        return ids;
    }

    @Override
    public void addAll(List<String> ids, List<Embedding> embeddings, List<TextSegment> embedded) {
        if (this.searchQueryType.equals(AzureCosmosDBSearchQueryType.FULL_TEXT_SEARCH)
                || this.searchQueryType.equals(AzureCosmosDBSearchQueryType.FULL_TEXT_RANKING)) {
            if (isNullOrEmpty(embedded)) {
                logger.info("do not add empty embeddings or content to Azure CosmosDB NoSQL");
                return;
            }
        } else {
            if (isNullOrEmpty(embeddings)) {
                logger.info("do not add empty embeddings to Azure CosmosDB NoSQL");
                return;
            }
        }

        List<ImmutablePair<String, CosmosItemOperation>> itemOperationsWithIds = ids.stream()
                .map(id -> {
                    String partitionKeyValue;

                    if ("/id".equals(this.partitionKeyPath)) {
                        partitionKeyValue = id;
                    } else if (this.partitionKeyPath.startsWith("/metadata/") && isNullOrEmpty(embedded)) {
                        String metadataKey = this.partitionKeyPath.substring("/metadata/".length());
                        Object value = embedded.get(ids.indexOf(id)).metadata() != null
                                ? embedded.get(ids.indexOf(id))
                                        .metadata()
                                        .toMap()
                                        .get(metadataKey)
                                : null;
                        if (value == null) {
                            throw new IllegalArgumentException(
                                    "Partition key '" + metadataKey + "' not found in document metadata.");
                        }
                        partitionKeyValue = value.toString();
                    } else {
                        throw new IllegalArgumentException("Unsupported partition key path: " + this.partitionKeyPath);
                    }

                    CosmosItemOperation operation;
                    if (isNullOrEmpty(embeddings)) {
                        operation = CosmosBulkOperations.getCreateItemOperation(
                                toNoSqlDbDocument(id, null, embedded.get(ids.indexOf(id))),
                                new PartitionKey(partitionKeyValue));
                    } else if (isNullOrEmpty(embedded)) {
                        operation = CosmosBulkOperations.getCreateItemOperation(
                                toNoSqlDbDocument(id, embeddings.get(ids.indexOf(id)), null),
                                new PartitionKey(partitionKeyValue));
                    } else {
                        operation = CosmosBulkOperations.getCreateItemOperation(
                                toNoSqlDbDocument(id, embeddings.get(ids.indexOf(id)), embedded.get(ids.indexOf(id))),
                                new PartitionKey(partitionKeyValue));
                    }

                    return new ImmutablePair<>(id, operation);
                })
                .toList();

        try {
            List<CosmosItemOperation> itemOperations =
                    itemOperationsWithIds.stream().map(ImmutablePair::getValue).collect(toList());

            this.container
                    .executeBulkOperations(Flux.fromIterable(itemOperations))
                    .doOnNext(response -> {
                        if (response != null && response.getResponse() != null) {
                            int statusCode = response.getResponse().getStatusCode();
                            if (statusCode == 409) {
                                // Retrieve the ID associated with the failed operation
                                String documentId = itemOperationsWithIds.stream()
                                        .filter(pair -> pair.getValue().equals(response.getOperation()))
                                        .findFirst()
                                        .map(ImmutablePair::getKey)
                                        .orElse("Unknown ID"); // Fallback if the ID can't be found

                                String errorMessage = String.format("Duplicate document id: %s", documentId);
                                logger.error(errorMessage);
                                throw new RuntimeException(errorMessage); // Throw an exception
                                // for status code 409
                            } else {
                                logger.info("Document added with status: {}", statusCode);
                            }
                        } else {
                            logger.warn("Received a null response or null status code for a document operation.");
                        }
                    })
                    .doOnError(error -> logger.error("Error adding document: {}", error.getMessage()))
                    .doOnComplete(() -> logger.info("Bulk operation completed successfully."))
                    .blockLast(); // Block until the last item of the Flux is processed
        } catch (Exception e) {
            logger.error("Exception occurred during bulk add operation: {}", e.getMessage(), e);
            throw e; // Rethrow the exception after logging
        }
    }

    @Override
    public void remove(final String id) {
        ensureNotBlank(id, "id");
        this.removeAll(singletonList(id));
    }

    @Override
    public void removeAll(final Collection<String> ids) {
        ensureNotEmpty(ids, "ids");
        try {
            List<CosmosItemOperation> itemOperations = ids.stream()
                    .map(id -> {
                        String partitionKeyValue;

                        if ("/id".equals(this.partitionKeyPath)) {
                            partitionKeyValue = id;
                        } else if (this.partitionKeyPath.startsWith("/metadata/")) {
                            // Will be inefficient for large numbers of documents but there is no
                            // other way to get the partition key value
                            // with current method signature. Ideally, we should be able to pass
                            // the partition key value directly.
                            String metadataKey = this.partitionKeyPath.substring("/metadata/".length());

                            // Run a reactive query to fetch the document by ID
                            String query = String.format("SELECT * FROM c WHERE c.id = '%s'", id);
                            CosmosPagedFlux<JsonNode> queryFlux =
                                    this.container.queryItems(query, new CosmosQueryRequestOptions(), JsonNode.class);

                            // Block to retrieve the first page synchronously
                            List<JsonNode> documents = Objects.requireNonNull(
                                            queryFlux.byPage(1).blockFirst())
                                    .getResults();

                            if (documents == null || documents.isEmpty()) {
                                throw new IllegalArgumentException("No document found for id: " + id);
                            }

                            JsonNode document = documents.get(0);
                            JsonNode metadataNode = document.get("metadata");

                            if (metadataNode == null || metadataNode.get(metadataKey) == null) {
                                throw new IllegalArgumentException("Partition key '" + metadataKey
                                        + "' not found in metadata for document with id: " + id);
                            }

                            partitionKeyValue = metadataNode.get(metadataKey).asText();
                        } else {
                            throw new IllegalArgumentException(
                                    "Unsupported partition key path: " + this.partitionKeyPath);
                        }

                        return CosmosBulkOperations.getDeleteItemOperation(id, new PartitionKey(partitionKeyValue));
                    })
                    .collect(toList());
            // Execute bulk delete operations synchronously by using blockLast() on the
            // Flux
            this.container
                    .executeBulkOperations(Flux.fromIterable(itemOperations))
                    .doOnNext(response -> logger.info(
                            "Document deleted with status: {}",
                            response.getResponse().getStatusCode()))
                    .doOnError(error -> logger.error("Error deleting document: {}", error.getMessage()))
                    .blockLast();
        } catch (Exception e) {
            logger.error("Exception while deleting documents: {}", e.getMessage(), e);
            throw e;
        }
    }

    public void deleteContainer() {
        try {
            this.container.delete().block();
            System.out.println("Deleted container: " + containerName);
        } catch (CosmosException e) {
            if (e.getStatusCode() == 404) {
                System.out.println("Container does not exist: " + containerName);
            } else {
                System.out.println("Failed to delete container: " + e.getMessage());
                throw e;
            }
        }
    }

    @Override
    public EmbeddingSearchResult<TextSegment> search(final EmbeddingSearchRequest request) {
        // Ensure topK is within acceptable limits
        if (request.maxResults() > 1000) {
            throw new IllegalArgumentException("Top K must be 1000 or less.");
        }
        String embeddingField = this.indexingPolicy
                .getVectorIndexes()
                .get(0)
                .getPath()
                .substring(
                        this.indexingPolicy.getVectorIndexes().get(0).getPath().lastIndexOf("/") + 1);

        List<Float> referenceEmbeddingString = request.queryEmbedding().vectorAsList();

        // Start building query for similarity search
        StringBuilder queryBuilder = new StringBuilder(String.format(
                "SELECT TOP @topK c.id as id, c.text as text, c.embedding as embedding, c.metadata as metadata, "
                        + "VectorDistance(c.%s, @embedding) as score FROM c",
                embeddingField));
        if (request.filter() != null) {
            queryBuilder.append(" AND").append(filterMapper.map(request.filter()));
        }
        queryBuilder.append(String.format(" ORDER BY VectorDistance(c.%s, @embedding)", embeddingField));

        String query = queryBuilder.toString();
        List<SqlParameter> parameters = new ArrayList<>();
        parameters.add(new SqlParameter("@embedding", referenceEmbeddingString));
        parameters.add(new SqlParameter("@topK", request.maxResults()));

        SqlQuerySpec sqlQuerySpec = new SqlQuerySpec(query, parameters);
        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        logger.info("Executing similarity search query: {}", query);

        return runQuery(sqlQuerySpec, options, request.minScore(), this.searchQueryType);
    }

    public EmbeddingSearchResult<TextSegment> findRelevantWithFullTextSearch(
            String content, Integer maxResults, double minScore, Filter filter) {
        // Ensure topK is within acceptable limits
        if (maxResults > 1000) {
            throw new IllegalArgumentException("Top K must be 1000 or less.");
        }
        if (filter == null) {
            throw new IllegalArgumentException("Filter cannot be null.");
        }

        String query = "SELECT TOP @topK * FROM c" + " WHERE " + filterMapper.map(filter);
        List<SqlParameter> parameters = new ArrayList<>();
        parameters.add(new SqlParameter("@topK", maxResults));

        SqlQuerySpec sqlQuerySpec = new SqlQuerySpec(query, parameters);
        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        logger.info("Executing full text search query: {}", query);

        return runQuery(sqlQuerySpec, options, minScore, this.searchQueryType);
    }

    public EmbeddingSearchResult<TextSegment> findRelevantWithFullTextRanking(
            String content, Integer maxResults, double minScore, Filter filter) {
        // Ensure topK is within acceptable limits
        if (maxResults > 1000) {
            throw new IllegalArgumentException("Top K must be 1000 or less.");
        }

        String searchWords =
                Arrays.stream(content.split("\\s+")).map(k -> "\"" + k + "\"").collect(Collectors.joining(", "));

        StringBuilder queryBuilder = new StringBuilder("SELECT TOP @topK * FROM c");
        if (filter != null) {
            queryBuilder.append(" WHERE").append(filterMapper.map(filter));
        }
        queryBuilder.append(String.format(" ORDER BY RANK FullTextScore(c.text, %s)", searchWords));

        String query = queryBuilder.toString();
        List<SqlParameter> parameters = new ArrayList<>();
        parameters.add(new SqlParameter("@topK", maxResults));

        SqlQuerySpec sqlQuerySpec = new SqlQuerySpec(query, parameters);
        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        logger.info("Executing full text search query: {}", query);

        return runQuery(sqlQuerySpec, options, minScore, this.searchQueryType);
    }

    public EmbeddingSearchResult<TextSegment> findRelevantWithHybridSearch(
            Embedding referenceEmbedding, String content, Integer maxResults, double minScore, Filter filter) {
        // Ensure topK is within acceptable limits
        if (maxResults > 1000) {
            throw new IllegalArgumentException("Top K must be 1000 or less.");
        }
        String embeddingField = this.indexingPolicy
                .getVectorIndexes()
                .get(0)
                .getPath()
                .substring(
                        this.indexingPolicy.getVectorIndexes().get(0).getPath().lastIndexOf("/") + 1);
        String textField = this.indexingPolicy
                .getCosmosFullTextIndexes()
                .get(0)
                .getPath()
                .substring(this.indexingPolicy
                                .getCosmosFullTextIndexes()
                                .get(0)
                                .getPath()
                                .lastIndexOf("/")
                        + 1);

        String searchWords =
                Arrays.stream(content.split("\\s+")).map(k -> "'" + k + "'").collect(Collectors.joining(", "));

        StringBuilder queryBuilder = new StringBuilder(String.format(
                "SELECT TOP %d c.id as id, c.text as text, c.embedding as embedding, c.metadata as metadata, "
                        + "VectorDistance(c.%s, %s) as score FROM c",
                maxResults, embeddingField, referenceEmbedding.vectorAsList()));

        if (filter != null) {
            queryBuilder.append(" AND").append(filterMapper.map(filter));
        }
        queryBuilder.append(String.format(
                " ORDER BY RANK RRF(FullTextScore(c.%s, %s), VectorDistance(c.%s, %s))",
                textField, searchWords, embeddingField, referenceEmbedding.vectorAsList()));

        String query = queryBuilder.toString();

        SqlQuerySpec sqlQuerySpec = new SqlQuerySpec(query, new ArrayList<>());
        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        logger.info("Executing hybrid search query: {}", query);

        return runQuery(sqlQuerySpec, options, minScore, this.searchQueryType);
    }

    private EmbeddingSearchResult<TextSegment> runQuery(
            SqlQuerySpec sqlQuerySpec,
            CosmosQueryRequestOptions options,
            Double minScore,
            AzureCosmosDBSearchQueryType azureCosmosDBSearchQueryType) {
        List<AzureCosmosDbNoSqlMatchedDocument> results = this.container
                .queryItems(sqlQuerySpec, options, AzureCosmosDbNoSqlMatchedDocument.class)
                .byPage()
                .flatMap(page -> Flux.fromIterable(page.getResults()))
                .collectList()
                .block();

        assert results != null;

        //        matches = results.stream().map(MappingUtils::toEmbeddingMatch).collect(toList());
        List<EmbeddingMatch<TextSegment>> matches =
                getEmbeddingMatches(results, minScore, azureCosmosDBSearchQueryType);

        return new EmbeddingSearchResult<>(matches);
    }

    private List<EmbeddingMatch<TextSegment>> getEmbeddingMatches(
            List<AzureCosmosDbNoSqlMatchedDocument> results,
            Double minScore,
            AzureCosmosDBSearchQueryType azureCosmosDBSearchQueryType) {

        if (azureCosmosDBSearchQueryType.equals(AzureCosmosDBSearchQueryType.FULL_TEXT_SEARCH)
                || azureCosmosDBSearchQueryType.equals(AzureCosmosDBSearchQueryType.FULL_TEXT_RANKING)) {
            return results.stream().map(MappingUtils::toEmbeddingMatch).toList();
        } else {
            List<EmbeddingMatch<TextSegment>> matches = new ArrayList<>();
            for (AzureCosmosDbNoSqlMatchedDocument result : results) {
                double score = RelevanceScore.fromCosineSimilarity(result.getScore());
                if (score < minScore) {
                    continue;
                }
                EmbeddingMatch<TextSegment> embeddingMatch = MappingUtils.toEmbeddingMatch(result);
                matches.add(embeddingMatch);
            }
            return matches;
        }
    }
}
