package dev.langchain4j.store.embedding.mongodb;

import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCommandException;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.SearchIndexModel;
import com.mongodb.client.model.SearchIndexType;
import com.mongodb.client.model.search.VectorSearchOptions;
import com.mongodb.client.result.InsertManyResult;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import org.bson.Document;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.StreamSupport;

import static com.mongodb.client.model.Aggregates.*;
import static com.mongodb.client.model.Projections.*;
import static com.mongodb.client.model.search.SearchPath.fieldPath;
import static com.mongodb.client.model.search.VectorSearchOptions.approximateVectorSearchOptions;
import static dev.langchain4j.internal.Utils.*;
import static dev.langchain4j.internal.ValidationUtils.*;
import static dev.langchain4j.store.embedding.mongodb.IndexMapping.defaultIndexMapping;
import static dev.langchain4j.store.embedding.mongodb.MappingUtils.*;
import static dev.langchain4j.store.embedding.mongodb.MongoDbMetadataFilterMapper.map;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

/**
 * Represents a <a href="https://www.mongodb.com/">MongoDB</a> indexed collection as an embedding store.
 * <p>
 * More <a href="https://www.mongodb.com/docs/atlas/atlas-vector-search/vector-search-overview/">info</a>
 * on using MongoDb vector search.
 * <p>
 * <a href="https://www.mongodb.com/developer/products/atlas/semantic-search-mongodb-atlas-vector-search/">tutorial</a>
 * how to use vector search with MongoDB Atlas (great starting point).
 * <p>
 * To deploy a local instance of Atlas, see
 * <a href="https://www.mongodb.com/docs/atlas/cli/current/atlas-cli-deploy-local/">this guide</a>.
 * <p>
 * If you are using a free tier, {@code #createIndex = true} might not be supported,
 * so you will need to create an index manually.
 * In your Atlas web console go to: DEPLOYMENT -&gt; Database -&gt; {your cluster} -&gt; Atlas Search tab
 * -&gt; Create Index Search -&gt; "JSON Editor" under "Atlas Vector Search" (not "Atlas Search") -&gt; Next
 * -&gt; Select your database in the left pane -&gt; Insert the following JSON into the right pane
 * (set "numDimensions" and additional metadata fields to desired values)
 * <pre>
 * {
 *   "fields" : [ {
 *     "type" : "vector",
 *     "path" : "embedding",
 *     "numDimensions" : 384,
 *     "similarity" : "cosine"
 *   }, {
 *     "type" : "filter",
 *     "path" : "metadata.test-key"
 *   } ]
 * }
 * </pre>
 * -&gt; Next -&gt; Create Search Index
 */
public class MongoDbEmbeddingStore implements EmbeddingStore<TextSegment> {
    private static final int SECONDS_TO_WAIT_FOR_INDEX = 20;

    private static final Logger log = LoggerFactory.getLogger(MongoDbEmbeddingStore.class);

    private final MongoCollection<MongoDbDocument> collection;

    private final String indexName;
    private final long maxResultRatio;
    private final Bson globalPrefilter;

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
        this.globalPrefilter = filter;

        if (!indexExists(this.indexName)) {
            if (createIndex) {
                createIndex(this.indexName, getOrDefault(indexMapping, defaultIndexMapping()));
            } else {
                throw new RuntimeException(String.format(
                        "Search Index '%s' not found and must be created via createIndex(true), or manually as a vector search index (not a regular index), via the createSearchIndexes command",
                        this.indexName));
            }
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
            return new MongoDbEmbeddingStore(
                    mongoClient, databaseName, collectionName, indexName,
                    maxResultRatio, createCollectionOptions, filter,
                    indexMapping, createIndex);
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
    public void removeAll() {
        collection.deleteMany(Filters.empty());
    }

    @Override
    public void removeAll(Collection<String> ids) {
        ensureNotEmpty(ids, "ids");
        collection.deleteMany(Filters.in("_id", ids));
    }

    @Override
    public void removeAll(Filter filter) {
        ensureNotNull(filter, "filter");
        collection.deleteMany(map(filter));
    }

    @Override
    public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request) {
        List<Double> queryVector = request.queryEmbedding().vectorAsList().stream()
                .map(Float::doubleValue)
                .collect(toList());
        long numCandidates = request.maxResults() * maxResultRatio;

        Bson postFilter = null;
        if (request.minScore() > 0) {
            postFilter = Filters.gte("score", request.minScore());
        }
        if (request.filter() != null) {
            Bson newFilter = map(request.filter());
            postFilter = postFilter == null ? newFilter : Filters.and(postFilter, newFilter);
        }

        VectorSearchOptions vectorSearchOptions = this.globalPrefilter == null
                ? approximateVectorSearchOptions(numCandidates)
                : approximateVectorSearchOptions(numCandidates).filter(this.globalPrefilter);

        ArrayList<Bson> pipeline = new ArrayList<>();
        pipeline.add(vectorSearch(
                fieldPath("embedding"),
                queryVector,
                indexName,
                request.maxResults(),
                vectorSearchOptions));
        pipeline.add(project(fields(
                metaVectorSearchScore("score"),
                include("embedding", "metadata", "text"))));
        if (postFilter != null) {
            Bson match = match(postFilter);
            pipeline.add(match);
        }

        try {
            AggregateIterable<MongoDbMatchedDocument> results = collection.aggregate(pipeline, MongoDbMatchedDocument.class);
            List<EmbeddingMatch<TextSegment>> result = StreamSupport.stream(results.spliterator(), false)
                    .map(MappingUtils::toEmbeddingMatch)
                    .collect(toList());
            return new EmbeddingSearchResult<>(result);
        } catch (MongoCommandException e) {
            log.error("Error in MongoDBEmbeddingStore.search", e);
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
            String id = ids.get(i);
            MongoDbDocument document = toMongoDbDocument(id, embeddings.get(i), embedded == null ? null : embedded.get(i));
            documents.add(document);
        }

        InsertManyResult result = collection.insertMany(documents);
        if (!result.wasAcknowledged()) {
            String errMsg = String.format("[MongoDbEmbeddingStore] Add document failed, Document=%s", documents);
            log.error(errMsg);
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

    private boolean indexExists(String indexName) {
        Document indexRecord = indexRecord(collection, indexName);
        return indexRecord != null && !indexRecord.getString("status").equals("DOES_NOT_EXIST");
    }

    private static Document indexRecord(
            MongoCollection<MongoDbDocument> collection, String indexName) {
        return StreamSupport.stream(collection.listSearchIndexes().spliterator(), false)
                .filter(index -> indexName.equals(index.getString("name")))
                .findAny().orElse(null);
    }

    private void createIndex(String indexName, IndexMapping indexMapping) {
        collection.createSearchIndexes(List.of(new SearchIndexModel(
                indexName,
                fromIndexMapping(indexMapping),
                SearchIndexType.vectorSearch())));

        waitForIndex(collection, indexName);
    }

    static void waitForIndex(MongoCollection<MongoDbDocument> collection, String indexName) {
        long startTime = System.nanoTime();
        long timeoutNanos = TimeUnit.SECONDS.toNanos(SECONDS_TO_WAIT_FOR_INDEX);
        while (System.nanoTime() - startTime < timeoutNanos) {
            Document indexRecord = indexRecord(collection, indexName);
            if (indexRecord != null) {
                if ("FAILED".equals(indexRecord.getString("status"))) {
                    throw new RuntimeException("Search index has failed status.");
                }
                if (indexRecord.getBoolean("queryable")) {
                    return;
                }
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }
        log.warn("Index {} was not created or did not exit INITIAL_SYNC within {} seconds",
                indexName, SECONDS_TO_WAIT_FOR_INDEX);
    }
}
