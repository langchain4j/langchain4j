package dev.langchain4j.store.embedding.mongodb;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.ServerApi;
import com.mongodb.ServerApiVersion;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.bson.UuidRepresentation;
import org.testcontainers.mongodb.MongoDBAtlasLocalContainer;
import org.testcontainers.shaded.com.google.common.collect.Sets;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

import static java.lang.String.format;

final class MongoDbTestFixture {

    public static final EmbeddingModel EMBEDDING_MODEL = new AllMiniLmL6V2QuantizedEmbeddingModel();
    private static final String USERNAME = getenv("MONGODB_USERNAME");
    private static final String PASSWORD = getenv("MONGODB_PASSWORD");
    private static final String HOST = getenv("MONGODB_HOST");
    private static final String CONNECTION_STRING = getenv("MONGODB_CONNECTION_STRING");

    private static final String DATABASE_NAME = "test_database";
    private static MongoDBAtlasLocalContainer mongodb;

    private final String collectionName;
    private final MongoClient client;
    private MongoDbEmbeddingStore embeddingStore;

    MongoDbTestFixture(MongoClient client) {
        this.collectionName = "test_collection_" + ThreadLocalRandom.current().nextInt(0, Integer.MAX_VALUE);
        this.client = client;
    }

    MongoDbTestFixture initialize() {
        return this.initialize(b -> b);
    }

    MongoDbTestFixture initialize(Function<MongoDbEmbeddingStore.Builder, MongoDbEmbeddingStore.Builder> initializer) {
        this.embeddingStore = initializer.apply(getDefaultMongoDbEmbeddingStoreBuilder()).build();
        return this;
    }

    EmbeddingStore<TextSegment> getEmbeddingStore() {
        return embeddingStore;
    }

    private static MongoDBAtlasLocalContainer getContainer() {
        if (mongodb == null) {
            mongodb = new MongoDBAtlasLocalContainer("mongodb/mongodb-atlas-local:7.0.9");
            mongodb.start();
        }
        return mongodb;
    }

    static MongoClient createDefaultClient() {
        boolean hasParts = HOST != null && USERNAME != null && PASSWORD != null;
        if (hasParts || CONNECTION_STRING != null) {
            String connectionString = CONNECTION_STRING != null
                    ? CONNECTION_STRING
                    : format("mongodb+srv://%s:%s@%s/?retryWrites=true&w=majority", USERNAME, PASSWORD, HOST);
            MongoClientSettings.Builder builder = MongoClientSettings.builder()
                    .applyConnectionString(new ConnectionString(connectionString));
            return createClientFromBuilder(builder);
        } else {
            MongoClientSettings.Builder builder = MongoClientSettings.builder()
                    .applyConnectionString(new ConnectionString(getContainer().getConnectionString()));
            return createClientFromBuilder(builder);
        }
    }

    private static MongoClient createClientFromBuilder(MongoClientSettings.Builder builder) {
        MongoClientSettings mongoClientSettings = builder
                .uuidRepresentation(UuidRepresentation.STANDARD)
                .serverApi(ServerApi.builder().version(ServerApiVersion.V1).build())
                .build();
        return MongoClients.create(mongoClientSettings);
    }

    private MongoDbEmbeddingStore.Builder getDefaultMongoDbEmbeddingStoreBuilder() {
        IndexMapping indexMapping = IndexMapping.builder()
                .dimension(EMBEDDING_MODEL.dimension())
                .metadataFieldNames(Sets.newHashSet("test-key"))
                .build();
        return MongoDbEmbeddingStore.builder()
                .fromClient(client)
                .databaseName(DATABASE_NAME)
                .collectionName(collectionName)
                .indexName("test_index")
                .indexMapping(indexMapping)
                .createIndex(true);
    }

    void afterTests() {
        getDatabase().getCollection(collectionName).drop();
        client.close();
    }

    MongoDatabase getDatabase() {
        return client.getDatabase(DATABASE_NAME);
    }

    String getCollectionName() {
        return collectionName;
    }

    private static String getenv(String name) {
        String value = System.getenv(name);
        if ("".equals(value)) {
            return null;
        }
        return value;
    }
}
