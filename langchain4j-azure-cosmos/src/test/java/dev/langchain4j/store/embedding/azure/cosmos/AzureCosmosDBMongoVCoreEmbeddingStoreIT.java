package dev.langchain4j.store.embedding.azure.cosmos;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIT;
import org.bson.BsonDocument;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.bson.conversions.Bson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

@EnabledIfEnvironmentVariable(named = "AZURE_COSMOS_ENDPOINT", matches = ".+")
public class AzureCosmosDBMongoVCoreEmbeddingStoreIT  extends EmbeddingStoreIT {

    private static final Logger log = LoggerFactory.getLogger(AzureCosmosDBMongoVCoreEmbeddingStoreIT.class);
    private static MongoClient client;
    private EmbeddingModel embeddingModel;
    private EmbeddingStore<TextSegment> embeddingStore;
    private int dimensions;


    public AzureCosmosDBMongoVCoreEmbeddingStoreIT() {
        embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();
        dimensions = embeddingModel.embed("test").content().vector().length;

        client = MongoClients.create(
                MongoClientSettings.builder()
                        .applyConnectionString(new ConnectionString(System.getenv("AZURE_COSMOS_ENDPOINT")))
                        .applicationName("JAVA_LANG_CHAIN")
                        .build());

        embeddingStore = AzureCosmosDbMongoVCoreEmbeddingStore.builder()
                .fromClient(client)
                .databaseName("test_database")
                .collectionName("test_collection")
                .indexName("test_index")
                .applicationName("JAVA_LANG_CHAIN")
                .createIndex(true)
                .kind("vector-ivf")
                .numLists(1)
                .similarity("COS")
                .dimensions(dimensions)
                .numberOfConnections(16)
                .efConstruction(64)
                .efSearch(40)
                .build();
    }

    @Test
    void testAddEmbeddingsAndFindRelevant() {
        String content1 = "banana";
        String content2 = "computer";
        String content3 = "apple";
        String content4 = "pizza";
        String content5 = "strawberry";
        String content6 = "chess";
        List<String> contents = asList(content1, content2, content3, content4, content5, content6);

        for (String content : contents) {
            TextSegment textSegment = TextSegment.from(content);
            Embedding embedding = embeddingModel.embed(content).content();
            embeddingStore.add(embedding, textSegment);
        }

        awaitUntilPersisted();

        Embedding relevantEmbedding = embeddingModel.embed("fruit").content();
        List<EmbeddingMatch<TextSegment>> relevant = embeddingStore.findRelevant(relevantEmbedding, 3);
        assertThat(relevant).hasSize(3);
        assertThat(relevant.get(0).embedding()).isNotNull();
        assertThat(relevant.get(0).embedded().text()).isIn(content1, content3, content5);
        log.info("#1 relevant item: {}", relevant.get(0).embedded().text());
        assertThat(relevant.get(1).embedding()).isNotNull();
        assertThat(relevant.get(1).embedded().text()).isIn(content1, content3, content5);
        log.info("#2 relevant item: {}", relevant.get(1).embedded().text());
        assertThat(relevant.get(2).embedding()).isNotNull();
        assertThat(relevant.get(2).embedded().text()).isIn(content1, content3, content5);
        log.info("#3 relevant item: {}", relevant.get(2).embedded().text());
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
    protected void awaitUntilPersisted() {
        try {
            Thread.sleep(1_000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
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
