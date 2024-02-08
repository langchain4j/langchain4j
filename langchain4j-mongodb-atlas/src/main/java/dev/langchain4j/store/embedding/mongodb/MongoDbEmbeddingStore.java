package dev.langchain4j.store.embedding.mongodb;

import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCommandException;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.search.VectorSearchOptions;
import com.mongodb.client.result.InsertManyResult;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.bson.Document;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static com.mongodb.client.model.Aggregates.*;
import static com.mongodb.client.model.Projections.*;
import static com.mongodb.client.model.search.SearchPath.fieldPath;
import static com.mongodb.client.model.search.VectorSearchOptions.vectorSearchOptions;
import static dev.langchain4j.internal.Utils.*;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.internal.ValidationUtils.ensureTrue;
import static dev.langchain4j.store.embedding.mongodb.IndexMapping.defaultIndexMapping;
import static dev.langchain4j.store.embedding.mongodb.MappingUtils.fromIndexMapping;
import static dev.langchain4j.store.embedding.mongodb.MappingUtils.toMongoDbDocument;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

/**
 * Represents a <a href="https://www.mongodb.com/">MongoDB</a> index as an embedding store.
 * <p>
 * More <a href="https://www.mongodb.com/docs/atlas/atlas-search/field-types/knn-vector/">info</a>
 * to set up MongoDb as vectorDatabase.
 * <p>
 * <a href="https://www.mongodb.com/developer/products/atlas/semantic-search-mongodb-atlas-vector-search/">tutorial</a>
 * how to use a knn-vector in MongoDB Atlas (great starting point).
 * <p>
 * If you are using a free tier, {@code #createIndex = true} might not be supported,
 * so you will need to create an index manually.
 * In your Atlas web console go to: DEPLOYMENT -&gt; Database -&gt; {your cluster} -&gt; Atlas Search -&gt; Create Index Search
 * -&gt; "JSON Editor" under "Atlas Search" -&gt; Next -&gt; Select your database in the left pane
 * -&gt; Insert the following JSON into the right pane (set "dimensions" and "metadata"-&gt;"fields" to desired values)
 * <pre>
 * {
 *   "mappings": {
 *     "dynamic": false,
 *     "fields": {
 *       "embedding": {
 *         "dimensions": 384,
 *         "similarity": "cosine",
 *         "type": "knnVector"
 *       },
 *       "metadata": {
 *         "dynamic": false,
 *         "fields": {
 *           "test-key": {
 *             "type": "token"
 *           }
 *         },
 *         "type": "document"
 *       }
 *     }
 *   }
 * }
 * </pre>
 * -&gt; Next -&gt; Create Search Index
 */
public class MongoDbEmbeddingStore implements EmbeddingStore<TextSegment> {

    private static final Logger log = LoggerFactory.getLogger(MongoDbEmbeddingStore.class);

    private final MongoCollection<MongoDbDocument> collection;

    private final String indexName;
    private final long maxResultRatio;
    private final VectorSearchOptions vectorSearchOptions;

    public MongoDbEmbeddingStore(MongoClient mongoClient,
                                 String databaseName,
                                 String collectionName,
                                 String indexName,
                                 Long maxResultRatio,
                                 CreateCollectionOptions createCollectionOptions,
                                 Bson filter,
                                 IndexMapping indexMapping,
                                 Boolean createIndex) {
        databaseName = ensureNotNull(databaseName, "databaseName");
        collectionName = ensureNotNull(collectionName, "collectionName");
        createIndex = getOrDefault(createIndex, false);
        this.indexName = ensureNotNull(indexName, "indexName");
        this.maxResultRatio = getOrDefault(maxResultRatio, 10L);

        CodecRegistry pojoCodecRegistry = fromProviders(PojoCodecProvider.builder()
                .register(MongoDbDocument.class, MongoDbMatchedDocument.class)
                .build());
        CodecRegistry codecRegistry = fromRegistries(MongoClientSettings.getDefaultCodecRegistry(), pojoCodecRegistry);

        // create collection if not exist
        MongoDatabase database = mongoClient.getDatabase(databaseName);
        if (!isCollectionExist(database, collectionName)) {
            createCollection(database, collectionName, getOrDefault(createCollectionOptions, new CreateCollectionOptions()));
        }

        this.collection = database.getCollection(collectionName, MongoDbDocument.class).withCodecRegistry(codecRegistry);
        this.vectorSearchOptions = filter == null ? vectorSearchOptions() : vectorSearchOptions().filter(filter);

        // create index if not exist
        if (Boolean.TRUE.equals(createIndex) && !isIndexExist(this.indexName)) {
            createIndex(this.indexName, getOrDefault(indexMapping, defaultIndexMapping()));
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private MongoClient mongoClient;
        private String databaseName;
        private String collectionName;
        private String indexName;
        private Long maxResultRatio;
        private CreateCollectionOptions createCollectionOptions;
        private Bson filter;
        private IndexMapping indexMapping;
        /**
         * Whether MongoDB Atlas is deployed in cloud
         *
         * <p>if true, you need to create index in <a href="https://cloud.mongodb.com/">MongoDB Atlas</a></p>
         * <p>if false, {@link MongoDbEmbeddingStore} will create collection and index automatically</p>
         */
        private Boolean createIndex;

        /**
         * Build Mongo Client, Please close the client to release resources after usage
         */
        public Builder fromClient(MongoClient mongoClient) {
            this.mongoClient = mongoClient;
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

        public Builder maxResultRatio(Long maxResultRatio) {
            this.maxResultRatio = maxResultRatio;
            return this;
        }

        public Builder createCollectionOptions(CreateCollectionOptions createCollectionOptions) {
            this.createCollectionOptions = createCollectionOptions;
            return this;
        }

        /**
         * Document query filter, all fields included in filter must be contained in {@link IndexMapping#metadataFieldNames}
         *
         * <p>For example:</p>
         *
         * <ul>
         *     <li>AND filter: Filters.and(Filters.in("type", asList("TXT", "md")), Filters.eqFull("test-key", "test-value"))</li>
         *     <li>OR filter: Filters.or(Filters.in("type", asList("TXT", "md")), Filters.eqFull("test-key", "test-value"))</li>
         * </ul>
         *
         * @param filter document query filter
         * @return builder
         */
        public Builder filter(Bson filter) {
            this.filter = filter;
            return this;
        }

        /**
         * set MongoDB search index fields mapping
         *
         * <p>if {@link Builder#createIndex} is true, then indexMapping not work</p>
         *
         * @param indexMapping MongoDB search index fields mapping
         * @return builder
         */
        public Builder indexMapping(IndexMapping indexMapping) {
            this.indexMapping = indexMapping;
            return this;
        }

        /**
         * Set whether in production mode, production mode will not create index automatically
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

        public MongoDbEmbeddingStore build() {
            return new MongoDbEmbeddingStore(mongoClient, databaseName, collectionName, indexName, maxResultRatio, createCollectionOptions, filter, indexMapping, createIndex);
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
        List<Double> queryVector = referenceEmbedding.vectorAsList().stream()
                .map(Float::doubleValue)
                .collect(toList());
        long numCandidates = maxResults * maxResultRatio;

        List<Bson> pipeline = Arrays.asList(
                vectorSearch(
                        fieldPath("embedding"),
                        queryVector,
                        indexName,
                        numCandidates,
                        maxResults,
                        vectorSearchOptions),
                project(
                        fields(
                                metaVectorSearchScore("score"),
                                include("embedding", "metadata", "text")
                        )
                ),
                match(
                        Filters.gte("score", minScore)
                ));

        try {
            AggregateIterable<MongoDbMatchedDocument> results = collection.aggregate(pipeline, MongoDbMatchedDocument.class);

            return StreamSupport.stream(results.spliterator(), false)
                    .map(MappingUtils::toEmbeddingMatch)
                    .collect(Collectors.toList());

        } catch (MongoCommandException e) {
            if (log.isErrorEnabled()) {
                log.error("Error in MongoDBEmbeddingStore.findRelevant", e);
            }
            throw new RuntimeException(e);
        }
    }

    private void addInternal(String id, Embedding embedding, TextSegment embedded) {
        addAllInternal(singletonList(id), singletonList(embedding), embedded == null ? null : singletonList(embedded));
    }

    private void addAllInternal(List<String> ids, List<Embedding> embeddings, List<TextSegment> embedded) {
        if (isNullOrEmpty(ids) || isNullOrEmpty(embeddings)) {
            log.info("do not add empty embeddings to MongoDB Atlas");
            return;
        }
        ensureTrue(ids.size() == embeddings.size(), "ids size is not equal to embeddings size");
        ensureTrue(embedded == null || embeddings.size() == embedded.size(), "embeddings size is not equal to embedded size");

        List<MongoDbDocument> documents = new ArrayList<>(ids.size());
        for (int i = 0; i < ids.size(); i++) {
            MongoDbDocument document = toMongoDbDocument(ids.get(i), embeddings.get(i), embedded == null ? null : embedded.get(i));
            documents.add(document);
        }

        InsertManyResult result = collection.insertMany(documents);
        if (!result.wasAcknowledged() && log.isWarnEnabled()) {
            String errMsg = String.format("[MongoDbEmbeddingStore] Add document failed, Document=%s", documents);
            log.warn(errMsg);
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
        return StreamSupport.stream(collection.listSearchIndexes().spliterator(), false)
                .anyMatch(index -> indexName.equals(index.getString("name")));
    }

    private void createIndex(String indexName, IndexMapping indexMapping) {
        Document index = fromIndexMapping(indexMapping);
        collection.createSearchIndex(indexName, index);
    }
}
