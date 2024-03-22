package dev.langchain4j.store.embedding.azure.cosmos.mongo.vcore;

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
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.RelevanceScore;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.bson.Document;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.Utils.randomUUID;
import static dev.langchain4j.internal.ValidationUtils.ensureTrue;
import static dev.langchain4j.store.embedding.azure.cosmos.mongo.vcore.MappingUtils.toEmbeddingMatch;
import static dev.langchain4j.store.embedding.azure.cosmos.mongo.vcore.MappingUtils.toMongoDbDocument;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

/**
 * Represents an Azure CosmosDB Mongo vCore as an embedding store.
 * <p>
 * More <a href="https://learn.microsoft.com/en-us/azure/cosmos-db/mongodb/vcore/vector-search">info</a>
 * to set up MongoDb as vectorDatabase.
 */
public class AzureCosmosDbMongoVCoreEmbeddingStore implements EmbeddingStore<TextSegment> {

    private static final Logger log = LoggerFactory.getLogger(AzureCosmosDbMongoVCoreEmbeddingStore.class);
    private final MongoCollection<AzureCosmosDbMongoVCoreDocument> collection;
    private final String indexName;
    private final VectorIndexType kind;
    private final Integer numLists;
    private final Integer dimensions;
    private final Integer m;
    private final Integer efConstruction;
    private final Integer efSearch;

    /**
     * @param mongoClient             - mongoClient for the Azure CosmosDB Mongo vCore
     * @param connectionString        - connection string required to connect to Azure Cosmos Mongo vCore
     * @param databaseName            - databaseName for the mongoDb vCore
     * @param collectionName          - collection name for the mongoDB vCore
     * @param indexName               - index name for the mongoDB vCore collection
     * @param applicationName         - application name for the client for tracking and logging
     * @param createCollectionOptions - options for creating a collection
     * @param createIndex             - set to true if you want the application to create an index, or false if you want to create
     *                                it manually.
     * @param kind                    - Type of vector index to create.
     *                                Possible options are:
     *                                - vector-ivf
     *                                - vector-hnsw: available as a preview feature only, to enable visit
     *                                https://learn.microsoft.com/en-us/azure/azure-resource-manager/management/preview-features
     * @param numLists                - This integer is the number of clusters that the inverted file (IVF) index uses to group the
     *                                vector data. We recommend that numLists is set to documentCount/1000 for up to 1 million
     *                                documents and to sqrt(documentCount) for more than 1 million documents. Using a numLists value
     *                                of 1 is akin to performing brute-force search, which has limited performance.
     * @param dimensions              - Number of dimensions for vector similarity. The maximum number of supported dimensions
     *                                is 2000.
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
    public AzureCosmosDbMongoVCoreEmbeddingStore(
            MongoClient mongoClient,
            String connectionString,
            String databaseName,
            String collectionName,
            String indexName,
            String applicationName,
            CreateCollectionOptions createCollectionOptions,
            Boolean createIndex,
            String kind,
            Integer numLists,
            Integer dimensions,
            Integer m,
            Integer efConstruction,
            Integer efSearch) {
        if (mongoClient == null && (connectionString == null || connectionString.isEmpty())) {
            throw new IllegalArgumentException("You need to pass either the mongoClient or " +
                    "the connectionString required for connecting to Azure CosmosDB Mongo vCore");
        }

        if (databaseName == null || databaseName.isEmpty() || collectionName == null || collectionName.isEmpty()) {
            throw new IllegalArgumentException("databaseName and collectionName needs to be provided.");
        }
        createIndex = getOrDefault(createIndex, false);
        this.indexName = getOrDefault(indexName, "defaultIndexAzureCosmos");
        applicationName = getOrDefault(applicationName, "LangChain4j");
        this.kind = VectorIndexType.fromString(kind);
        this.numLists = getOrDefault(numLists, 1);
        // TODO: update this value as a user input once LangChain4J only
        //  supports other similarity types other than Cosine.
        this.dimensions = getOrDefault(dimensions, 1536);
        this.m = getOrDefault(m, 16);
        this.efConstruction = getOrDefault(efConstruction, 64);
        this.efSearch = getOrDefault(efSearch, 40);

        CodecRegistry pojoCodecRegistry = fromProviders(PojoCodecProvider.builder()
                .register(AzureCosmosDbMongoVCoreDocument.class, BsonDocument.class)
                .build());
        CodecRegistry codecRegistry = fromRegistries(MongoClientSettings.getDefaultCodecRegistry(), pojoCodecRegistry);

        if (mongoClient == null) {
            mongoClient = MongoClients.create(
                    MongoClientSettings.builder()
                            .applyConnectionString(new ConnectionString(connectionString))
                            .applicationName(applicationName)
                            .build());
        }

        MongoDatabase database = mongoClient.getDatabase(databaseName);
        // create collection if not exist
        if (!isCollectionExist(database, collectionName)) {
            createCollection(database, collectionName, getOrDefault(createCollectionOptions, new CreateCollectionOptions()));
        }
        this.collection = database.getCollection(collectionName, AzureCosmosDbMongoVCoreDocument.class).withCodecRegistry(codecRegistry);

        // create index if not exist
        if (Boolean.TRUE.equals(createIndex) && !isIndexExist(this.indexName)) {
            createIndex(this.indexName, collectionName, database);
        }
    }

    public static Builder builder() {
        return new Builder();
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
                .collect(toList());
        addAllInternal(ids, embeddings, null);
        return ids;
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings, List<TextSegment> embedded) {
        List<String> ids = embeddings.stream()
                .map(ignored -> randomUUID())
                .collect(toList());
        addAllInternal(ids, embeddings, embedded);
        return ids;
    }

    @Override
    public List<EmbeddingMatch<TextSegment>> findRelevant(Embedding referenceEmbedding, int maxResults, double minScore) {

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
                    .filter(doc -> RelevanceScore.fromCosineSimilarity(doc.getDouble("similarityScore").getValue()) >= minScore)
                    .map(doc -> toEmbeddingMatch(mapBsonToAzureCosmosDbMongoVCoreMatchedDocument(doc.getDocument("document"), doc.getDouble("similarityScore").getValue())))
                    .collect(Collectors.toList());

        } catch (MongoCommandException e) {
            throw new RuntimeException("Error in AzureCosmosDbMongoVCoreEmbeddingStore.findRelevant", e);
        }
    }

    private List<Bson> getPipelineDefinitionVectorIVF(Embedding queryVector, int maxResults) {
        List<Bson> pipeline = new ArrayList<>();

        // First stage: $search
        Document searchStage = new Document("$search", new Document("cosmosSearch",
                new Document("vector", queryVector.vectorAsList())
                        .append("path", "embedding")
                        .append("k", maxResults))
                .append("returnStoredSource", true));
        pipeline.add(searchStage);

        // Second stage: $project
        Document projectStage = new Document("$project", new Document("similarityScore",
                new Document("$meta", "searchScore"))
                .append("document", "$$ROOT"));
        pipeline.add(projectStage);

        return pipeline;
    }

    private List<Bson> getPipelineDefinitionVectorHNSW(Embedding queryVector, int maxResults) {
        List<Bson> pipeline = new ArrayList<>();

        // First stage: $search
        Document searchStage = new Document("$search", new Document("cosmosSearch",
                new Document("vector", queryVector.vectorAsList())
                        .append("path", "embedding")
                        .append("k", maxResults)
                        .append("efSearch", this.efSearch)));
        pipeline.add(searchStage);

        // Second stage: $project
        Document projectStage = new Document("$project", new Document("similarityScore",
                new Document("$meta", "searchScore"))
                .append("document", "$$ROOT"));
        pipeline.add(projectStage);

        return pipeline;
    }

    private AzureCosmosDbMongoVCoreMatchedDocument mapBsonToAzureCosmosDbMongoVCoreMatchedDocument(BsonDocument bsonDocument, Double score) {
        AzureCosmosDbMongoVCoreMatchedDocument document = new AzureCosmosDbMongoVCoreMatchedDocument();

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
        addAllInternal(singletonList(id), singletonList(embedding), embedded == null ? null : singletonList(embedded));
    }

    private void addAllInternal(List<String> ids, List<Embedding> embeddings, List<TextSegment> embedded) {
        if (isNullOrEmpty(ids) || isNullOrEmpty(embeddings)) {
            log.info("do not add empty embeddings to Azure CosmosDB  Mongo vCore");
            return;
        }
        ensureTrue(ids.size() == embeddings.size(), "ids size is not equal to embeddings size");
        ensureTrue(embedded == null || embeddings.size() == embedded.size(), "embeddings size is not equal to embedded size");

        List<AzureCosmosDbMongoVCoreDocument> documents = new ArrayList<>(ids.size());
        for (int i = 0; i < ids.size(); i++) {
            AzureCosmosDbMongoVCoreDocument document = toMongoDbDocument(ids.get(i), embeddings.get(i), embedded == null ? null : embedded.get(i));
            documents.add(document);
        }

        InsertManyResult result = collection.insertMany(documents);
        if (!result.wasAcknowledged()) {
            String errMsg = String.format("[AzureCosmosDbMongoVCoreEmbeddingStore] Add document failed, Document=%s", documents);
            throw new RuntimeException(errMsg);
        }
    }

    private boolean isCollectionExist(MongoDatabase database, String collectionName) {
        return StreamSupport.stream(database.listCollectionNames().spliterator(), false)
                .anyMatch(collectionName::equals);
    }

    private void createCollection(MongoDatabase database, String collectionName, CreateCollectionOptions createCollectionOptions) {
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
                .append("cosmosSearchOptions", new Document()
                        .append("kind", this.kind.getValue())
                        .append("numLists", this.numLists)
                        .append("similarity", SimilarityMetric.COS)
                        .append("dimensions", this.dimensions));

        BsonDocument bsonIndexDefinition = indexDefinition.toBsonDocument();

        BsonArray bsonArray = new BsonArray();
        bsonArray.add(bsonIndexDefinition);

        return new Document()
                .append("createIndexes", collectionName)
                .append("indexes", bsonArray).toBsonDocument();
    }

    private BsonDocument getIndexDefinitionVectorHNSW(String indexName, String collectionName) {
        Document indexDefinition = new Document()
                .append("name", indexName)
                .append("key", new Document("embedding", "cosmosSearch"))
                .append("cosmosSearchOptions", new Document()
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
                .append("indexes", bsonArray).toBsonDocument();
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
        private String kind;
        private Integer numLists;
        private Integer dimensions;
        private Integer m;
        private Integer efConstruction;
        private Integer efSearch;

        /**
         * Build Mongo Client, Please close the client to release resources after usage.
         * This is a mandatory parameter if not providing the connectionString.
         */
        public Builder mongoClient(MongoClient mongoClient) {
            this.mongoClient = mongoClient;
            return this;
        }

        /**
         * Sets the Azure CosmosDB Mongo vCore connectionString. This is a mandatory parameter if not providing the Mongo Client.
         *
         * @param connectionString The Azure CosmosDB Mongo vCore connectionString.
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
         * @param createIndex whether in production mode
         * @return builder
         */
        public Builder createIndex(Boolean createIndex) {
            this.createIndex = createIndex;
            return this;
        }

        /**
         * @param kind - Type of vector index to create.
         *             Possible options are:
         *             - vector-ivf
         *             - vector-hnsw: available as a preview feature only, to enable visit
         *             https://learn.microsoft.com/en-us/azure/azure-resource-manager/management/preview-feature
         */
        public Builder kind(String kind) {
            this.kind = kind;
            return this;
        }

        /**
         * @param numLists - This integer is the number of clusters that the inverted file (IVF) index uses to group the
         *                 vector data. We recommend that numLists is set to documentCount/1000 for up to 1 million
         *                 documents and to sqrt(documentCount) for more than 1 million documents. Using a numLists value
         *                 of 1 is akin to performing brute-force search, which has limited performance.
         * @return
         */
        public Builder numLists(Integer numLists) {
            this.numLists = numLists;
            return this;
        }

        /**
         * @param dimensions - Number of dimensions for vector similarity. The maximum number of supported dimensions
         *                   is 2000.
         * @return
         */
        public Builder dimensions(Integer dimensions) {
            this.dimensions = dimensions;
            return this;
        }

        /**
         * @param m - The max number of connections per layer (16 by default, minimum value is 2, maximum
         *          value is 100). Higher m is suitable for datasets with high dimensionality and/or high
         *          accuracy requirements.
         * @return
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
         * @return
         */
        public Builder efConstruction(Integer efConstruction) {
            this.efConstruction = efConstruction;
            return this;
        }

        /**
         * @param efSearch - The size of the dynamic candidate list for search (40 by default). A higher value provides
         *                 better recall at the cost of speed.
         * @return
         */
        public Builder efSearch(Integer efSearch) {
            this.efSearch = efSearch;
            return this;
        }

        public AzureCosmosDbMongoVCoreEmbeddingStore build() {
            return new AzureCosmosDbMongoVCoreEmbeddingStore(mongoClient, connectionString, databaseName, collectionName, indexName, applicationName,
                    createCollectionOptions, createIndex, kind, numLists, dimensions, m,
                    efConstruction, efSearch);
        }
    }

    public enum SimilarityMetric {
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
                    .orElseThrow(() -> new IllegalArgumentException("This similarity metric is not supported: "
                            +similarityString));
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
            return Arrays.stream(VectorIndexType.values())
                    .filter(k -> k.getValue().equals(kindString))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("This vector index type is not supported: " +kindString));
        }
    }
}
