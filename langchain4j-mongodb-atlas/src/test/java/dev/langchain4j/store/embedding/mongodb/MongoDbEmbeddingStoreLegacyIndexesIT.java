package dev.langchain4j.store.embedding.mongodb;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIT;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.testcontainers.shaded.com.google.common.collect.Sets;

import java.util.Set;

import static dev.langchain4j.store.embedding.mongodb.MongoDbTestFixture.*;

class MongoDbEmbeddingStoreLegacyIndexesIT extends EmbeddingStoreIT {

    private final MongoDbTestFixture helper = initializeTestHelper();

    private MongoDbTestFixture initializeTestHelper() {
        MongoDbTestFixture helper = new MongoDbTestFixture(createClient());
        IndexMapping indexMapping = IndexMapping.builder()
                .dimension(EMBEDDING_MODEL.dimension())
                .metadataFieldNames(Sets.newHashSet("test-key"))
                .build();
        helper.getDatabase().createCollection(helper.getCollectionName());
        MongoCollection<MongoDbDocument> collection = helper.getDatabase()
                .getCollection(helper.getCollectionName(), MongoDbDocument.class);
        String indexName = "test_index_legacy";
        collection.createSearchIndex(indexName, fromIndexMappingLegacyKnnVector(indexMapping));
        MongoDbEmbeddingStore.waitForIndex(collection, indexName);
        MongoDbTestFixture fixture = helper.initialize(builder -> builder.createIndex(false).indexName(indexName));
        return fixture;
    }

    MongoClient createClient() {
        return createDefaultClient();
    }

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return helper.getEmbeddingStore();
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return EMBEDDING_MODEL;
    }

    @AfterEach
    void afterEach() {
        helper.afterTests();
    }

    static Document fromIndexMappingLegacyKnnVector(IndexMapping indexMapping) {
        Document fields = new Document()
                .append("embedding", new Document()
                        .append("dimensions", indexMapping.getDimension())
                        .append("similarity", "cosine")
                        .append("type", "knnVector"));
        Set<String> metadataFields = indexMapping.getMetadataFieldNames();
        if (metadataFields != null && !metadataFields.isEmpty()) {
            Document metadataFieldDoc = new Document();
            metadataFields.forEach(field -> metadataFieldDoc
                    .append(field, new Document("type", "token")));
            fields.append("metadata", new Document()
                    .append("dynamic", false)
                    .append("type", "document")
                    .append("fields", metadataFieldDoc));
        }
        return new Document("mappings", new Document()
                .append("dynamic", false)
                .append("fields", fields));
    }

}