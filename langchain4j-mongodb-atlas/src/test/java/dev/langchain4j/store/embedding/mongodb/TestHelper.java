package dev.langchain4j.store.embedding.mongodb;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
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
import org.junit.jupiter.api.Assumptions;
import org.testcontainers.mongodb.MongoDBAtlasLocalContainer;
import org.testcontainers.shaded.com.google.common.collect.Sets;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

import static java.lang.String.format;

class TestHelper {

    public static final EmbeddingModel EMBEDDING_MODEL = new AllMiniLmL6V2QuantizedEmbeddingModel();
    private static final String USERNAME = getenv("MONGODB_ATLAS_USERNAME");
    private static final String PASSWORD = getenv("MONGODB_ATLAS_PASSWORD");
    private static final String HOST = getenv("MONGODB_ATLAS_HOST");
    private static final String CONNECTION_STRING = "mongodb://localhost"; // TODO

    private final String dbName;
    private final MongoClient client;
    private MongoDbEmbeddingStore embeddingStore;

    TestHelper(MongoClient client) {
        this.dbName = "test_database_" + ThreadLocalRandom.current().nextInt(0, Integer.MAX_VALUE);
        this.client = client;
    }

    TestHelper initialize() {
        return this.initialize(b -> b);
    }

    TestHelper initialize(Function<MongoDbEmbeddingStore.Builder, MongoDbEmbeddingStore.Builder> initializer) {
        this.embeddingStore = initializer.apply(getDefaultMongoDbEmbeddingStoreBuilder()).build();
        return this;
    }

    static void assertDoContainerTests() {
        Assumptions.assumeTrue(getenv("MONGODB_DISABLE_CONTAINER_TESTS") == null);
    }

    EmbeddingStore<TextSegment> getEmbeddingStore() {
        return embeddingStore;
    }

    static MongoClient createClientFromContainer(MongoDBAtlasLocalContainer mongodb) {
        MongoClientSettings.Builder builder = MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(mongodb.getConnectionString()));
        return createClientFromBuilder(builder);
    }

    static MongoClient createClientFromEnv() {
        boolean hasParts = HOST != null && USERNAME != null && PASSWORD != null;
        Assumptions.assumeTrue(hasParts || CONNECTION_STRING != null,
                "Skipping test: host/username/password, or connection string must be provided via env vars.");

        String connectionString = CONNECTION_STRING != null
                ? CONNECTION_STRING
                : format("mongodb+srv://%s:%s@%s/?retryWrites=true&w=majority", USERNAME, PASSWORD, HOST);
        MongoClientSettings.Builder builder = MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(connectionString));
        return createClientFromBuilder(builder);
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
                .databaseName(dbName)
                .collectionName("test_collection")
                .indexName("test_index")
                .indexMapping(indexMapping)
                .createIndex(true);
    }

    void afterTests() {
        getDatabase().drop();
        client.close();
    }

    MongoDatabase getDatabase() {
        return client.getDatabase(dbName);
    }

    private static String getenv(String name) {
        String value = System.getenv(name);
        if ("".equals(value)) {
            return null;
        }
        return value;
    }
}
