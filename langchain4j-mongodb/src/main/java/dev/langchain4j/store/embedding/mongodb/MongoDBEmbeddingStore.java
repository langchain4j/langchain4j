package dev.langchain4j.store.embedding.mongodb;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCommandException;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.search.VectorSearchOptions;
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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static com.mongodb.client.model.Aggregates.project;
import static com.mongodb.client.model.Aggregates.vectorSearch;
import static com.mongodb.client.model.Projections.fields;
import static com.mongodb.client.model.Projections.include;
import static com.mongodb.client.model.Projections.metaVectorSearchScore;
import static com.mongodb.client.model.search.SearchPath.fieldPath;
import static com.mongodb.client.model.search.VectorSearchOptions.vectorSearchOptions;
import static dev.langchain4j.internal.Utils.randomUUID;
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

    private final MongoCollection<EmbeddingDocument> collection;

    private final String indexName;
    private final long maxResultRatio;
    private final VectorSearchOptions vectorSearchOptions;

    private final CodecRegistry codecRegistry;

    private final DocumentMapping documentMapping = new DocumentMapping();
    private final QueryMapping queryMapping = new QueryMapping();
    private boolean shouldCreateIndex;

    MongoDBEmbeddingStore(MongoClient mongoClient, String database, String collection, String indexName, long maxResultRatio, Bson filter, boolean shouldCreateIndex) {


        CodecRegistry pojoCodecRegistry = fromProviders(PojoCodecProvider.builder()
                .automatic(true).build());
        codecRegistry = fromRegistries(MongoClientSettings.getDefaultCodecRegistry(), pojoCodecRegistry);
        this.indexName = indexName;
        this.maxResultRatio = maxResultRatio;
        this.collection = mongoClient.getDatabase(database).getCollection(collection, EmbeddingDocument.class).withCodecRegistry(codecRegistry);
        this.vectorSearchOptions = filter == null ? vectorSearchOptions() : vectorSearchOptions().filter(filter);
        this.shouldCreateIndex = shouldCreateIndex;
    }

    @Override
    public String add(Embedding embedding) {
        return addInternal(null, embedding, null);
    }

    @Override
    public void add(String id, Embedding embedding) {
        addInternal(id, embedding, null);
    }

    @Override
    public String add(Embedding embedding, TextSegment textSegment) {
        return addInternal(null, embedding, textSegment);

    }

    @Override
    public List<String> addAll(List<Embedding> embeddings) {
        return addAllInternal(embeddings, null);
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings, List<TextSegment> textSegments) {
        return addAllInternal(embeddings, textSegments);
    }

    public String addInternal(String id, Embedding embedding, TextSegment textSegment) {

        String documentId = id != null ? id : randomUUID();

        EmbeddingDocument document = documentMapping.generateDocument(documentId, embedding, textSegment);
        createIndexIfNotExist(document.getEmbedding(), document.getMetadata());
        collection.insertOne(document);
        return documentId;
    }

    public List<String> addAllInternal(List<Embedding> embeddings, List<TextSegment> textSegments) {
        assert (textSegments == null || embeddings.size() == textSegments.size());

        List<String> ids = new ArrayList<>();
        List<EmbeddingDocument> documents = new ArrayList<>();
        for (int i = 0; i < embeddings.size(); i++) {
            String id = randomUUID();
            ids.add(id);
            TextSegment textSegment = textSegments != null ? textSegments.get(i) : null;
            documents.add(documentMapping.generateDocument(id, embeddings.get(i), textSegment));
        }
        documents.stream().findFirst().ifPresent(document -> createIndexIfNotExist(document.getEmbedding(), document.getMetadata()));

        collection.insertMany(documents);
        return ids;
    }

    private void createIndexIfNotExist(List<Double> embedding, Map<String, String> metaData) {
        if (shouldCreateIndex) {
            Optional<Document> existing = StreamSupport.stream(collection.listSearchIndexes().spliterator(), false)
                    .filter(index -> indexName.equals(index.getString("name")))
                    .findAny();

            if (!existing.isPresent()) {
                Set<String> metaDataFields = metaData != null ? metaData.keySet() : Collections.emptySet();
                Bson mapping = BsonUtils.getMapping(embedding.size(), metaDataFields);

                collection.createSearchIndex(indexName, mapping);
            }
            shouldCreateIndex = false;
        }

    }


    @Override
    public List<EmbeddingMatch<TextSegment>> findRelevant(Embedding referenceEmbedding, int maxResults, double minScore) {
        List<Double> queryVector = queryMapping.asDoublesList(referenceEmbedding.vector());
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
                ));
        try {
            AggregateIterable<EmbeddingMatchDocument> results = collection.aggregate(pipeline, EmbeddingMatchDocument.class);

            return StreamSupport.stream(results.spliterator(), false)
                    .map(documentMapping::asTextSegmentEmbeddingMatch)
                    .collect(Collectors.toList());

        } catch (MongoCommandException e) {
            log.error("Error in MongoDBEmbeddingStore.findRelevant", e);
            if (log.isDebugEnabled()) {
                log.debug("probably the index is not yet created. Please create the index in MongoDB Atlas");
                log.debug("to test the aggregation pipeline you can use this json: {}", BsonUtils.asJson(pipeline));

                try (MongoCursor<Document> resultsCursor = collection.listSearchIndexes().iterator()) {
                    while (resultsCursor.hasNext()) {
                        log.debug("current existing indexes: {}", BsonUtils.asJson(resultsCursor.next()));
                    }
                }
            }
            throw e;
        }
    }


    public static Builder withConnectionString(ConnectionString connectionString) {
        MongoClient mongoClient = MongoClients.create(connectionString);
        String database = connectionString.getDatabase();
        String collection = connectionString.getCollection();

        return new Builder().mongoClient(mongoClient).collection(collection).database(database);
    }

    public static Builder withUri(String uri, String database, String collection) {
        MongoClient mongoClient = MongoClients.create(uri);

        return new Builder().mongoClient(mongoClient).collection(collection).database(database);
    }

    public static Builder withMongoDBClient(MongoClient mongoClient, String database, String collection) {
        return new Builder().mongoClient(mongoClient).collection(collection).database(database);
    }

    public static class Builder {

        private MongoClient mongoClient;
        private String database;
        private String collection;
        private String indexName = "default";
        private long maxResultRatio = 10L;
        private Bson filter = null;
        private boolean shouldCreateIndex = true;

        protected Builder mongoClient(MongoClient mongoClient) {
            this.mongoClient = mongoClient;
            return this;
        }

        protected Builder database(String database) {
            this.database = database;
            return this;
        }

        protected Builder collection(String collection) {
            this.collection = collection;
            return this;
        }

        public Builder indexName(String indexName) {
            this.indexName = indexName;
            return this;
        }

        public Builder maxResultRatio(long maxResultRatio) {
            this.maxResultRatio = maxResultRatio;
            return this;
        }

        public Builder shouldCreateIndex(boolean shouldCreateIndex) {
            this.shouldCreateIndex = shouldCreateIndex;
            return this;
        }

        /**
         * A filter to apply to the documents before searching. This is useful if you want to restrict the search to a subset of embeddings
         * <p>
         * example {@code Filters.eq("embedded.metadata.document_type", "TXT")} will only search for embeddings with the metadata.document_type = TXT
         * <p>
         *
         * @param filter a filter to apply to the documents before searching
         * @return the builder
         * @see com.mongodb.client.model.Filters#eqFull(String, Object)
         * @see <a href="https://docs.mongodb.com/manual/reference/operator/query/">MongoDB Query Operators</a>
         */
        public Builder filter(Bson filter) {
            this.filter = filter;
            return this;
        }

        public MongoDBEmbeddingStore build() {
            return new MongoDBEmbeddingStore(mongoClient, database, collection, indexName, maxResultRatio, filter, shouldCreateIndex);
        }

    }

}
