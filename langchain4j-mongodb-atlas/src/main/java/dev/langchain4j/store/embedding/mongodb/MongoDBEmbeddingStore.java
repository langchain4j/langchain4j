package dev.langchain4j.store.embedding.mongodb;

import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCommandException;
import com.mongodb.client.*;
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
import static dev.langchain4j.store.embedding.mongodb.MappingUtils.toIndexMapping;
import static dev.langchain4j.store.embedding.mongodb.MappingUtils.toMongoDbDocument;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

/**
 * Represents a <a href="https://www.mongodb.com/">MongoDB</a> index as an embedding store.
 * <p>
 * More <a href="https://www.mongodb.com/docs/atlas/atlas-search/field-types/knn-vector/">info</a> to set up MongoDb as vectorDatabase
 * <p>
 * <a href="https://www.mongodb.com/developer/products/atlas/semantic-search-mongodb-atlas-vector-search/">tutorial</a> how to use a knn-vector in MongoDB Atlas (great startingpoint)
 */
public class MongoDBEmbeddingStore implements EmbeddingStore<TextSegment> {

    private static final Logger log = LoggerFactory.getLogger(MongoDBEmbeddingStore.class);

    private final MongoCollection<MongoDBDocument> collection;

    private final String indexName;
    private final long maxResultRatio;
    private final VectorSearchOptions vectorSearchOptions;

    public MongoDBEmbeddingStore(MongoClient mongoClient,
                                 String databaseName,
                                 String collectionName,
                                 String indexName,
                                 Long maxResultRatio,
                                 CreateCollectionOptions createCollectionOptions,
                                 Bson filter,
                                 IndexMapping indexMapping) {
        ensureNotNull(mongoClient, "mongoClient");
        databaseName = ensureNotNull(databaseName, "databaseName");
        collectionName = ensureNotNull(collectionName, "collectionName");
        this.indexName = ensureNotNull(indexName, "indexName");
        this.maxResultRatio = getOrDefault(maxResultRatio, 10L);

        CodecRegistry pojoCodecRegistry = fromProviders(PojoCodecProvider.builder()
                .register(MongoDBDocument.class, MongoDBMatchedDocument.class)
                .build());
        CodecRegistry codecRegistry = fromRegistries(MongoClientSettings.getDefaultCodecRegistry(), pojoCodecRegistry);

        // create collection if not exist
        MongoDatabase database = mongoClient.getDatabase(databaseName);
        if (!isCollectionExist(database, collectionName)) {
            createCollection(database, collectionName, getOrDefault(createCollectionOptions, new CreateCollectionOptions()));
        }

        this.collection = database.getCollection(collectionName, MongoDBDocument.class).withCodecRegistry(codecRegistry);
        this.vectorSearchOptions = filter == null ? vectorSearchOptions() : vectorSearchOptions().filter(filter);

        // create index if not exist
        if (!isIndexExist(this.indexName)) {
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

        public Builder fromClient(MongoClient mongoClient) {
            this.mongoClient = mongoClient;
            return this;
        }

        public Builder fromConnectionString(String connectionString) {
            this.mongoClient = MongoClients.create(connectionString);
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

        public Builder filter(Bson filter) {
            this.filter = filter;
            return this;
        }

        public Builder indexMapping(IndexMapping indexMapping) {
            this.indexMapping = indexMapping;
            return this;
        }

        public MongoDBEmbeddingStore build() {
            return new MongoDBEmbeddingStore(mongoClient, databaseName, collectionName, indexName, maxResultRatio, createCollectionOptions, filter, indexMapping);
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
            AggregateIterable<MongoDBMatchedDocument> results = collection.aggregate(pipeline, MongoDBMatchedDocument.class);

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

        List<MongoDBDocument> documents = new ArrayList<>(ids.size());
        for (int i = 0; i < ids.size(); i++) {
            MongoDBDocument document = toMongoDbDocument(ids.get(i), embeddings.get(i), embedded == null ? null : embedded.get(i));
            documents.add(document);
        }

        InsertManyResult result = collection.insertMany(documents);
        if (!result.wasAcknowledged() && log.isWarnEnabled()) {
            log.warn("Add document failed, Document={}", documents);
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
        Document index = toIndexMapping(indexMapping);
        collection.createSearchIndex(indexName, index);
    }
}
