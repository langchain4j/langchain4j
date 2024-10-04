package dev.langchain4j.store.embedding.mongodb;

import com.mongodb.client.MongoClient;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIT;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;

import static dev.langchain4j.store.embedding.mongodb.MongoDbTestFixture.*;

public class MongoDbEmbeddingStoreIT extends EmbeddingStoreIT {

    public static class ContainerIT extends MongoDbEmbeddingStoreIT {
        @BeforeAll
        static void start() {
            MongoDbTestFixture.assertDoContainerTests();
        }

        @Override
        MongoClient createClient() {
            return createClientFromContainer();
        }
    }

    private MongoDbTestFixture helper = new MongoDbTestFixture(createClient()).initialize();

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
