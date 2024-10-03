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

import static dev.langchain4j.store.embedding.mongodb.TestHelper.EMBEDDING_MODEL;
import static dev.langchain4j.store.embedding.mongodb.TestHelper.createClientFromEnv;

public class MongoDbEmbeddingStoreLegacyIndexesIT extends EmbeddingStoreIT {

    private final TestHelper helper = initializeTestHelper();

    private TestHelper initializeTestHelper() {
        TestHelper helper = new TestHelper(createClient());
        IndexMapping indexMapping = IndexMapping.builder()
                .dimension(EMBEDDING_MODEL.dimension())
                .metadataFieldNames(Sets.newHashSet("test-key"))
                .build();
        helper.getDatabase().createCollection("test_collection");
        MongoCollection<Document> collection = helper.getDatabase().getCollection("test_collection");
        collection.createSearchIndex("test_index", fromIndexMappingLegacyKnnVector(indexMapping));
        return helper.initialize(builder -> builder.createIndex(false));
    }

    MongoClient createClient() {
        return createClientFromEnv();
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