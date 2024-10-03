package dev.langchain4j.store.embedding.mongodb;

import com.mongodb.client.MongoClient;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithRemovalIT;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.mongodb.MongoDBAtlasLocalContainer;

import static dev.langchain4j.store.embedding.mongodb.TestHelper.*;

public class MongoDbEmbeddingStoreWithRemovalIT extends EmbeddingStoreWithRemovalIT {

    public static class ContainerIT extends MongoDbEmbeddingStoreWithRemovalIT {
        static MongoDBAtlasLocalContainer mongodb = new MongoDBAtlasLocalContainer("mongodb/mongodb-atlas-local:7.0.9");

        @BeforeAll
        static void start() {
            TestHelper.assertDoContainerTests();
            mongodb.start();
        }

        @AfterAll
        static void stop() {
            mongodb.stop();
        }

        @Override
        MongoClient createClient() {
            return createClientFromContainer(mongodb);
        }
    }

    private TestHelper helper = new TestHelper(createClient()).initialize();

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
}
