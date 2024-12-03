package dev.langchain4j.store.embedding.azure.cosmos.mongo.vcore;

import com.mongodb.ConnectionString;
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
import org.bson.BsonDocument;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.bson.conversions.Bson;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

@EnabledIfEnvironmentVariable(named = "AZURE_COSMOS_ENDPOINT", matches = ".+")
public class AzureCosmosDBMongoVCoreEmbeddingStoreIT extends EmbeddingStoreIT {

    private static MongoClient client;
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;


    public AzureCosmosDBMongoVCoreEmbeddingStoreIT() {
        embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

        client = MongoClients.create(
                MongoClientSettings.builder()
                        .applyConnectionString(new ConnectionString(System.getenv("AZURE_COSMOS_ENDPOINT")))
                        .applicationName("JAVA_LANG_CHAIN")
                        .build());

        embeddingStore = AzureCosmosDbMongoVCoreEmbeddingStore.builder()
                .mongoClient(client)
                .databaseName("test_database")
                .collectionName("test_collection")
                .indexName("test_index")
                .applicationName("JAVA_LANG_CHAIN")
                .createIndex(true)
                .kind("vector-hnsw")
                .numLists(2)
                .dimensions(embeddingModel.dimension())
                .m(16)
                .efConstruction(64)
                .efSearch(40)
                .build();
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
                .register(AzureCosmosDbMongoVCoreDocument.class, BsonDocument.class)
                .build());
        CodecRegistry codecRegistry = fromRegistries(MongoClientSettings.getDefaultCodecRegistry(), pojoCodecRegistry);

        MongoCollection<AzureCosmosDbMongoVCoreDocument> collection = client.getDatabase("test_database")
                .getCollection("test_collection", AzureCosmosDbMongoVCoreDocument.class)
                .withCodecRegistry((codecRegistry));

        Bson filter = Filters.exists("embedding");
        collection.deleteMany(filter);
    }
}
