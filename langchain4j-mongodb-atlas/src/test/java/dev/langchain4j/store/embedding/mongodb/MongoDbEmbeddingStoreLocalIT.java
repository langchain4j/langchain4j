package dev.langchain4j.store.embedding.mongodb;

import com.mongodb.*;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIT;
import lombok.SneakyThrows;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.bson.conversions.Bson;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.shaded.com.google.common.collect.Sets;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

/**
 * If container startup timeout (because atlas cli need to download mongodb binaries, which may take a few minutes),
 * the alternative way is running `docker compose up -d` in `src/test/resources`
 */
class MongoDbEmbeddingStoreLocalIT extends EmbeddingStoreIT {

    static MongoDBAtlasContainer mongodb = new MongoDBAtlasContainer();

    static MongoClient client;

    EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    IndexMapping indexMapping = IndexMapping.builder()
            .dimension(embeddingModel.dimension())
            .metadataFieldNames(Sets.newHashSet("test-key"))
            .build();

    EmbeddingStore<TextSegment> embeddingStore = MongoDbEmbeddingStore.builder()
            .fromClient(client)
            .databaseName("test_database")
            .collectionName("test_collection")
            .indexName("test_index")
            .indexMapping(indexMapping)
            .createIndex(true)
            .build();

    @BeforeAll
    @SneakyThrows
    static void start() {
        mongodb.start();

        MongoCredential credential = MongoCredential.createCredential("root", "admin", "root".toCharArray());
        client = MongoClients.create(
                MongoClientSettings.builder()
                        .credential(credential)
                        .serverApi(ServerApi.builder().version(ServerApiVersion.V1).build())
                        .applyConnectionString(new ConnectionString(mongodb.getConnectionString()))
                        .build());
    }

    @AfterAll
    static void stop() {
        mongodb.stop();
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
    protected void ensureStoreIsReady() {
        // to avoid "cannot query search index while in state INITIAL_SYNC" error
        awaitUntilAsserted(() -> assertThatNoException().isThrownBy(this::getAllEmbeddings));
    }

    @Override
    protected void clearStore() {
        CodecRegistry pojoCodecRegistry = fromProviders(PojoCodecProvider.builder()
                .register(MongoDbDocument.class, MongoDbMatchedDocument.class)
                .build());
        CodecRegistry codecRegistry = fromRegistries(MongoClientSettings.getDefaultCodecRegistry(), pojoCodecRegistry);

        MongoCollection<MongoDbDocument> collection = client.getDatabase("test_database")
                .getCollection("test_collection", MongoDbDocument.class)
                .withCodecRegistry(codecRegistry);

        Bson filter = Filters.exists("embedding");
        collection.deleteMany(filter);
    }
}
