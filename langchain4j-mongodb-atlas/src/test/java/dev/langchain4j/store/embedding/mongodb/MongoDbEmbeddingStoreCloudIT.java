package dev.langchain4j.store.embedding.mongodb;

import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIT;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.bson.conversions.Bson;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static java.lang.String.format;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

@EnabledIfEnvironmentVariable(named = "MONGODB_ATLAS_USERNAME", matches = ".+")
class MongoDbEmbeddingStoreCloudIT extends EmbeddingStoreIT {

    private static final String DATABASE_NAME = "test_database";
    private static final String COLLECTION_NAME = "test_collection";
    private static final String INDEX_NAME = "default";

    static MongoClient client;

    MongoDbEmbeddingStore embeddingStore = MongoDbEmbeddingStore.builder()
            .fromClient(client)
            .databaseName(DATABASE_NAME)
            .collectionName(COLLECTION_NAME)
            .indexName(INDEX_NAME)
            .build();

    EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    @BeforeAll
    static void beforeAll() {
        String username = System.getenv("MONGODB_ATLAS_USERNAME");
        String password = System.getenv("MONGODB_ATLAS_PASSWORD");
        String host = System.getenv("MONGODB_ATLAS_HOST");
        String connectionString = format("mongodb+srv://%s:%s@%s/?retryWrites=true&w=majority", username, password, host);
        client = MongoClients.create(connectionString);
    }

    @AfterAll
    static void afterAll() {
        client.close();
    }

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return embeddingStore;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return embeddingModel;
    }

    @Override
    protected void clearStore() {
        CodecRegistry pojoCodecRegistry = fromProviders(PojoCodecProvider.builder()
                .register(MongoDbDocument.class, MongoDbMatchedDocument.class)
                .build());
        CodecRegistry codecRegistry = fromRegistries(MongoClientSettings.getDefaultCodecRegistry(), pojoCodecRegistry);

        MongoCollection<MongoDbDocument> collection = client.getDatabase(DATABASE_NAME)
                .getCollection(COLLECTION_NAME, MongoDbDocument.class)
                .withCodecRegistry(codecRegistry);

        Bson filter = Filters.exists("embedding");
        collection.deleteMany(filter);
    }
}
