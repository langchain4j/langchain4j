package dev.langchain4j.store.embedding.azure.documentdb;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.Utils.randomUUID;
import static dev.langchain4j.internal.ValidationUtils.ensureConsistentSizes;
import static dev.langchain4j.internal.ValidationUtils.ensureGreaterThanZeroIfNotNull;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.store.embedding.azure.documentdb.MappingUtils.toEmbeddingMatch;
import static dev.langchain4j.store.embedding.azure.documentdb.MappingUtils.toMongoDbDocument;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.bson.codecs.configuration.CodecRegistries.fromCodecs;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCommandException;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.result.InsertManyResult;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.RelevanceScore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.bson.Document;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.conversions.Bson;

/**
 * Stores embeddings in Azure DocumentDB.
 * <p>
 * See the <a href="https://learn.microsoft.com/en-us/azure/documentdb/vector-search">vector search documentation</a>
 * for supported index types and cluster tiers.
 * <p>
 * When configured with a connection string, this store owns its MongoClient and must be closed
 * when no longer needed. A supplied MongoClient remains caller-owned and is never closed by this store.
 */
public class AzureDocumentDbEmbeddingStore implements EmbeddingStore<TextSegment>, AutoCloseable {

    private final MongoClient mongoClient;
    private final boolean ownsMongoClient;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final MongoCollection<AzureDocumentDbDocument> collection;
    private final String indexName;
    private final VectorIndexType kind;
    private final Integer numLists;
    private final Integer dimensions;
    private final Integer m;
    private final Integer efConstruction;
    private final Integer efSearch;

    /**
     * @param mongoClient             - caller-owned MongoClient for Azure DocumentDB; never closed by this store
     * @param connectionString        - connection string used to create an owned client when mongoClient is not provided
     * @param databaseName            - database name in Azure DocumentDB
     * @param collectionName          - collection name in Azure DocumentDB
     * @param indexName               - vector index name for the collection
     * @param applicationName         - application name for the client for tracking and logging
     * @param createCollectionOptions - options for creating a collection
     * @param createIndex             - set to true if you want the application to create an index, or false if you want to create
     *                                it manually.
     * @param kind                    - required vector index type for index creation and search
     * @param numLists                - This integer is the number of clusters that the inverted file (IVF) index uses to group the
     *                                vector data. We recommend that numLists is set to documentCount/1000 for up to 1 million
     *                                documents and to sqrt(documentCount) for more than 1 million documents. Using a numLists value
     *                                of 1 is akin to performing brute-force search, which has limited performance.
     * @param dimensions              - embedding dimensions; required when createIndex is true and must match the embedding model
     * @param m                       - used only for vector -hnsw. The max number of connections per layer (16 by default, minimum value is 2, maximum
     *                                value is 100). Higher m is suitable for datasets with high dimensionality and/or high
     *                                accuracy requirements.
     * @param efConstruction          - used only for vector -hnsw. The size of the dynamic candidate list for constructing the graph (64 by default, minimum
     *                                value is 4, maximum value is 1000). Higher ef_construction will result in better index
     *                                quality and higher accuracy, but it will also increase the time required to build the index.
     *                                ef_construction has to be at least 2 * m.
     * @param efSearch                - used only for vector -hnsw. The size of the dynamic candidate list for search (40 by default). A higher value provides
     *                                better recall at the cost of speed.
     */
    private AzureDocumentDbEmbeddingStore(
            MongoClient mongoClient,
            String connectionString,
            String databaseName,
            String collectionName,
            String indexName,
            String applicationName,
            CreateCollectionOptions createCollectionOptions,
            Boolean createIndex,
            VectorIndexType kind,
            Integer numLists,
            Integer dimensions,
            Integer m,
            Integer efConstruction,
            Integer efSearch) {
        if (mongoClient == null && isNullOrEmpty(connectionString)) {
            throw new IllegalArgumentException("You need to pass either the mongoClient or "
                    + "the connectionString required for connecting to Azure DocumentDB");
        }

        if (isNullOrEmpty(databaseName) || isNullOrEmpty(collectionName)) {
            throw new IllegalArgumentException("databaseName and collectionName needs to be provided.");
        }
        createIndex = getOrDefault(createIndex, false);
        this.indexName = getOrDefault(indexName, "defaultIndexAzureCosmos");
        applicationName = getOrDefault(applicationName, "LangChain4j");
        this.kind = ensureNotNull(kind, "kind");
        this.numLists = getOrDefault(numLists, 1);
        if (Boolean.TRUE.equals(createIndex) && dimensions == null) {
            throw new IllegalArgumentException("dimensions must be provided when createIndex is true");
        }
        this.dimensions = ensureGreaterThanZeroIfNotNull(dimensions, "dimensions");
        this.m = getOrDefault(m, 16);
        this.efConstruction = getOrDefault(efConstruction, 64);
        this.efSearch = getOrDefault(efSearch, 40);

        CodecRegistry codecRegistry = fromRegistries(
                MongoClientSettings.getDefaultCodecRegistry(), fromCodecs(new AzureDocumentDbDocumentCodec()));

        this.ownsMongoClient = mongoClient == null;
        this.mongoClient = ownsMongoClient
                ? MongoClients.create(MongoClientSettings.builder()
                        .applyConnectionString(new ConnectionString(connectionString))
                        .applicationName(applicationName)
                        .build())
                : mongoClient;

        try {
            MongoDatabase database = this.mongoClient.getDatabase(databaseName);
            // create collection if not exist
            if (!collectionExists(database, collectionName)) {
                createCollection(
                        database, collectionName, getOrDefault(createCollectionOptions, new CreateCollectionOptions()));
            }
            this.collection = database.getCollection(collectionName, AzureDocumentDbDocument.class)
                    .withCodecRegistry(codecRegistry);

            // create index if not exist
            if (Boolean.TRUE.equals(createIndex) && !isIndexExist(this.indexName)) {
                createIndex(this.indexName, collectionName, database);
            }
        } catch (RuntimeException | Error e) {
            if (ownsMongoClient) {
                try {
                    this.mongoClient.close();
                } catch (RuntimeException | Error closeException) {
                    e.addSuppressed(closeException);
                }
            }
            throw e;
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Closes the internally created MongoClient, releasing its connection pools and threads.
     * Repeated calls have no effect. A caller-supplied MongoClient is never closed.
     */
    @Override
    public void close() {
        if (ownsMongoClient && closed.compareAndSet(false, true)) {
            mongoClient.close();
        }
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
        List<String> ids = embeddings.stream().map(ignored -> randomUUID()).collect(toList());
        addAll(ids, embeddings, null);
        return ids;
    }

    @Override
    public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request) {
        if (request.filter() != null) {
            throw new UnsupportedOperationException("EmbeddingSearchRequest.Filter is not supported yet.");
        }

        List<EmbeddingMatch<TextSegment>> matches =
                findRelevant(request.queryEmbedding(), request.maxResults(), request.minScore());
        return new EmbeddingSearchResult<>(matches);
    }

    private List<EmbeddingMatch<TextSegment>> findRelevant(
            Embedding referenceEmbedding, int maxResults, double minScore) {

        List<Bson> pipeline = new ArrayList<>();

        switch (this.kind) {
            case VECTOR_IVF:
                pipeline = getPipelineDefinitionVectorIVF(referenceEmbedding, maxResults);
                break;
            case VECTOR_HNSW:
                pipeline = getPipelineDefinitionVectorHNSW(referenceEmbedding, maxResults);
                break;
        }

        try {
            AggregateIterable<BsonDocument> results = collection.aggregate(pipeline, BsonDocument.class);

            return StreamSupport.stream(results.spliterator(), false)
                    .filter(doc -> RelevanceScore.fromCosineSimilarity(
                                    doc.getDouble("similarityScore").getValue())
                            >= minScore)
                    .map(doc -> toEmbeddingMatch(mapBsonToAzureDocumentDbMatchedDocument(
                            doc.getDocument("document"),
                            doc.getDouble("similarityScore").getValue())))
                    .collect(Collectors.toList());

        } catch (MongoCommandException e) {
            throw new RuntimeException("Error in AzureDocumentDbEmbeddingStore.findRelevant", e);
        }
    }

    private List<Bson> getPipelineDefinitionVectorIVF(Embedding queryVector, int maxResults) {
        List<Bson> pipeline = new ArrayList<>();

        // First stage: $search
        Document searchStage = new Document(
                "$search",
                new Document(
                                "cosmosSearch",
                                new Document("vector", queryVector.vectorAsList())
                                        .append("path", "embedding")
                                        .append("k", maxResults))
                        .append("returnStoredSource", true));
        pipeline.add(searchStage);

        // Second stage: $project
        Document projectStage = new Document(
                "$project",
                new Document("similarityScore", new Document("$meta", "searchScore")).append("document", "$$ROOT"));
        pipeline.add(projectStage);

        return pipeline;
    }

    private List<Bson> getPipelineDefinitionVectorHNSW(Embedding queryVector, int maxResults) {
        List<Bson> pipeline = new ArrayList<>();

        // First stage: $search
        Document searchStage = new Document(
                "$search",
                new Document(
                        "cosmosSearch",
                        new Document("vector", queryVector.vectorAsList())
                                .append("path", "embedding")
                                .append("k", maxResults)
                                .append("efSearch", this.efSearch)));
        pipeline.add(searchStage);

        // Second stage: $project
        Document projectStage = new Document(
                "$project",
                new Document("similarityScore", new Document("$meta", "searchScore")).append("document", "$$ROOT"));
        pipeline.add(projectStage);

        return pipeline;
    }

    private AzureDocumentDbMatchedDocument mapBsonToAzureDocumentDbMatchedDocument(
            BsonDocument bsonDocument, Double score) {
        AzureDocumentDbMatchedDocument document = new AzureDocumentDbMatchedDocument();

        // Extract id
        document.setId(bsonDocument.getString("_id").getValue());

        // Extract embedding
        List<Float> embedding = new ArrayList<>();
        BsonArray embeddingArray = bsonDocument.getArray("embedding");
        for (BsonValue value : embeddingArray) {
            embedding.add((float) value.asDouble().getValue());
        }
        document.setEmbedding(embedding);

        // Extract text
        if (bsonDocument.containsKey("text")) {
            document.setText(bsonDocument.getString("text").getValue());
        }

        // Extract metadata
        if (bsonDocument.containsKey("metadata")) {
            Map<String, String> metadata = new HashMap<>();
            BsonDocument metadataDocument = bsonDocument.getDocument("metadata");
            for (String key : metadataDocument.keySet()) {
                metadata.put(key, metadataDocument.getString(key).getValue());
            }
            document.setMetadata(metadata);
        }

        // Set score
        document.setScore(RelevanceScore.fromCosineSimilarity(score));

        return document;
    }

    private void addInternal(String id, Embedding embedding, TextSegment embedded) {
        addAll(singletonList(id), singletonList(embedding), embedded == null ? null : singletonList(embedded));
    }

    @Override
    public void addAll(List<String> ids, List<Embedding> embeddings, List<TextSegment> embedded) {
        ensureConsistentSizes(ids, embeddings, embedded);
        if (isNullOrEmpty(embeddings)) {
            return;
        }

        List<AzureDocumentDbDocument> documents = new ArrayList<>(ids.size());
        for (int i = 0; i < ids.size(); i++) {
            AzureDocumentDbDocument document =
                    toMongoDbDocument(ids.get(i), embeddings.get(i), embedded == null ? null : embedded.get(i));
            documents.add(document);
        }

        InsertManyResult result = collection.insertMany(documents);
        if (!result.wasAcknowledged()) {
            throw new RuntimeException("Failed to add embeddings to Azure DocumentDB: the insert was not acknowledged");
        }
    }

    static boolean collectionExists(MongoDatabase database, String collectionName) {
        return StreamSupport.stream(database.listCollectionNames().spliterator(), false)
                .anyMatch(collectionName::equals);
    }

    private void createCollection(
            MongoDatabase database, String collectionName, CreateCollectionOptions createCollectionOptions) {
        database.createCollection(collectionName, createCollectionOptions);
    }

    private boolean isIndexExist(String indexName) {
        return StreamSupport.stream(collection.listIndexes().spliterator(), false)
                .anyMatch(index -> indexName.equals(index.getString("name")));
    }

    private void createIndex(String indexName, String collectionName, MongoDatabase database) {
        Bson commandDocument = new Document();
        switch (this.kind) {
            case VECTOR_IVF:
                commandDocument = getIndexDefinitionVectorIVF(indexName, collectionName);
                break;
            case VECTOR_HNSW:
                commandDocument = getIndexDefinitionVectorHNSW(indexName, collectionName);
                break;
        }

        database.runCommand(commandDocument);
    }

    private BsonDocument getIndexDefinitionVectorIVF(String indexName, String collectionName) {
        Document indexDefinition = new Document()
                .append("name", indexName)
                .append("key", new Document("embedding", "cosmosSearch"))
                .append(
                        "cosmosSearchOptions",
                        new Document()
                                .append("kind", this.kind.getValue())
                                .append("numLists", this.numLists)
                                .append("similarity", SimilarityMetric.COS)
                                .append("dimensions", this.dimensions));

        BsonDocument bsonIndexDefinition = indexDefinition.toBsonDocument();

        BsonArray bsonArray = new BsonArray();
        bsonArray.add(bsonIndexDefinition);

        return new Document()
                .append("createIndexes", collectionName)
                .append("indexes", bsonArray)
                .toBsonDocument();
    }

    private BsonDocument getIndexDefinitionVectorHNSW(String indexName, String collectionName) {
        Document indexDefinition = new Document()
                .append("name", indexName)
                .append("key", new Document("embedding", "cosmosSearch"))
                .append(
                        "cosmosSearchOptions",
                        new Document()
                                .append("kind", this.kind.getValue())
                                .append("m", this.m)
                                .append("efConstruction", this.efConstruction)
                                .append("similarity", SimilarityMetric.COS)
                                .append("dimensions", this.dimensions));

        BsonDocument bsonIndexDefinition = indexDefinition.toBsonDocument();

        BsonArray bsonArray = new BsonArray();
        bsonArray.add(bsonIndexDefinition);

        return new Document()
                .append("createIndexes", collectionName)
                .append("indexes", bsonArray)
                .toBsonDocument();
    }

    public static class Builder {
        private MongoClient mongoClient;
        private String connectionString;
        private String databaseName;
        private String collectionName;
        private String indexName;
        private String applicationName;
        private CreateCollectionOptions createCollectionOptions;
        private Boolean createIndex;
        private VectorIndexType kind;
        private Integer numLists;
        private Integer dimensions;
        private Integer m;
        private Integer efConstruction;
        private Integer efSearch;

        /**
         * Sets a caller-owned MongoClient. The caller is responsible for closing it;
         * closing the store does not close this client.
         * Takes precedence over connectionString when both are provided.
         */
        public Builder mongoClient(MongoClient mongoClient) {
            this.mongoClient = mongoClient;
            return this;
        }

        /**
         * Sets the Azure DocumentDB connectionString. This is a mandatory parameter if not providing the Mongo Client.
         * The store owns the client created from this connection string. Close the store to release its resources.
         *
         * @param connectionString The Azure DocumentDB connectionString.
         * @return builder
         */
        public Builder connectionString(String connectionString) {
            this.connectionString = connectionString;
            return this;
        }

        public Builder databaseName(String databaseName) {
            this.databaseName = databaseName;
            return this;
        }

        public Builder collectionName(String collectionName) {
            this.collectionName = collectionName;
            return this;
        }

        public Builder indexName(String indexName) {
            this.indexName = indexName;
            return this;
        }

        public Builder applicationName(String applicationName) {
            this.applicationName = applicationName;
            return this;
        }

        public Builder createCollectionOptions(CreateCollectionOptions createCollectionOptions) {
            this.createCollectionOptions = createCollectionOptions;
            return this;
        }

        /**
         * Set to true if you want the application to create an index, or false if you want to create it manually.
         *
         * <p>default value is false</p>
         *
         * When true, {@link #dimensions(Integer)} must also be configured.
         *
         * @param createIndex whether to create the vector index if it is missing
         * @return builder
         */
        public Builder createIndex(Boolean createIndex) {
            this.createIndex = createIndex;
            return this;
        }

        /**
         * Sets the required vector index type for index creation and search.
         *
         * @param kind {@code vector-ivf} or {@code vector-hnsw}
         * @return builder
         */
        public Builder kind(String kind) {
            return kind(VectorIndexType.fromString(kind));
        }

        /**
         * Sets the required vector index type for index creation and search.
         * HNSW requires an M30 or higher Azure DocumentDB cluster tier.
         *
         * @param kind the vector index type
         * @return builder
         */
        public Builder kind(VectorIndexType kind) {
            this.kind = kind;
            return this;
        }

        /**
         * @param numLists - This integer is the number of clusters that the inverted file (IVF) index uses to group the
         *                 vector data. We recommend that numLists is set to documentCount/1000 for up to 1 million
         *                 documents and to sqrt(documentCount) for more than 1 million documents. Using a numLists value
         *                 of 1 is akin to performing brute-force search, which has limited performance.
         * @return builder
         */
        public Builder numLists(Integer numLists) {
            this.numLists = numLists;
            return this;
        }

        /**
         * Sets the number of embedding dimensions. Required when {@link #createIndex(Boolean)} is true.
         *
         * @param dimensions a positive value matching the embedding model's output dimensions
         * @return builder
         */
        public Builder dimensions(Integer dimensions) {
            this.dimensions = dimensions;
            return this;
        }

        /**
         * @param m - The max number of connections per layer (16 by default, minimum value is 2, maximum
         *          value is 100). Higher m is suitable for datasets with high dimensionality and/or high
         *          accuracy requirements.
         * @return builder
         */
        public Builder m(Integer m) {
            this.m = m;
            return this;
        }

        /**
         * @param efConstruction - the size of the dynamic candidate list for constructing the graph (64 by default, minimum
         *                       value is 4, maximum value is 1000). Higher ef_construction will result in better index
         *                       quality and higher accuracy, but it will also increase the time required to build the index.
         *                       ef_construction has to be at least 2 * m.
         * @return builder
         */
        public Builder efConstruction(Integer efConstruction) {
            this.efConstruction = efConstruction;
            return this;
        }

        /**
         * @param efSearch - The size of the dynamic candidate list for search (40 by default). A higher value provides
         *                 better recall at the cost of speed.
         * @return builder
         */
        public Builder efSearch(Integer efSearch) {
            this.efSearch = efSearch;
            return this;
        }

        public AzureDocumentDbEmbeddingStore build() {
            return new AzureDocumentDbEmbeddingStore(
                    mongoClient,
                    connectionString,
                    databaseName,
                    collectionName,
                    indexName,
                    applicationName,
                    createCollectionOptions,
                    createIndex,
                    kind,
                    numLists,
                    dimensions,
                    m,
                    efConstruction,
                    efSearch);
        }
    }

    private enum SimilarityMetric {
        COS("COS");

        private final String value;

        SimilarityMetric(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public static SimilarityMetric fromString(String similarityString) {
            return Arrays.stream(SimilarityMetric.values())
                    .filter(k -> k.getValue().equals(similarityString))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "This similarity metric is not supported: " + similarityString));
        }
    }

    public enum VectorIndexType {
        VECTOR_IVF("vector-ivf"),
        VECTOR_HNSW("vector-hnsw");

        private final String value;

        VectorIndexType(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public static VectorIndexType fromString(String kindString) {
            ensureNotNull(kindString, "kind");
            return Arrays.stream(VectorIndexType.values())
                    .filter(k -> k.getValue().equals(kindString))
                    .findFirst()
                    .orElseThrow(() ->
                            new IllegalArgumentException("This vector index type is not supported: " + kindString));
        }
    }
}
