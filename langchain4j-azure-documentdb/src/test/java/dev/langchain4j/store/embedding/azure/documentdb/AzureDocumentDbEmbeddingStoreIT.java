package dev.langchain4j.store.embedding.azure.documentdb;

import static dev.langchain4j.store.embedding.azure.documentdb.AzureDocumentDbEmbeddingStore.VectorIndexType.VECTOR_HNSW;
import static org.bson.codecs.configuration.CodecRegistries.fromCodecs;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

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
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.conversions.Bson;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "AZURE_DOCUMENTDB_CONNECTION_STRING", matches = ".+")
public class AzureDocumentDbEmbeddingStoreIT extends EmbeddingStoreIT {

    private final MongoClient client;
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;

    public AzureDocumentDbEmbeddingStoreIT() {
        embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

        client = MongoClients.create(MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(System.getenv("AZURE_DOCUMENTDB_CONNECTION_STRING")))
                .applicationName("JAVA_LANG_CHAIN")
                .build());

        embeddingStore = AzureDocumentDbEmbeddingStore.builder()
                .mongoClient(client)
                .databaseName("test_database")
                .collectionName("test_collection")
                .indexName("test_index")
                .applicationName("JAVA_LANG_CHAIN")
                .createIndex(true)
                .kind(VECTOR_HNSW)
                .numLists(2)
                .dimensions(embeddingModel.dimension())
                .m(16)
                .efConstruction(64)
                .efSearch(40)
                .build();
    }

    @AfterEach
    void closeClient() {
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
        CodecRegistry codecRegistry = fromRegistries(
                MongoClientSettings.getDefaultCodecRegistry(), fromCodecs(new AzureDocumentDbDocumentCodec()));

        MongoCollection<AzureDocumentDbDocument> collection = client.getDatabase("test_database")
                .getCollection("test_collection", AzureDocumentDbDocument.class)
                .withCodecRegistry((codecRegistry));

        Bson filter = Filters.exists("embedding");
        collection.deleteMany(filter);
    }
}
