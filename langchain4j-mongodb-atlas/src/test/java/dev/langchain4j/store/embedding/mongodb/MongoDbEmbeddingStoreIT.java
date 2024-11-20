package dev.langchain4j.store.embedding.mongodb;

import com.mongodb.client.MongoClient;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIT;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;

import static dev.langchain4j.store.embedding.TestUtils.awaitUntilAsserted;
import static dev.langchain4j.store.embedding.mongodb.MongoDbTestFixture.*;
import static org.assertj.core.api.Assertions.assertThatNoException;

public class MongoDbEmbeddingStoreIT extends EmbeddingStoreIT {

    private MongoDbTestFixture fixture = new MongoDbTestFixture(createClient()).initialize();

    public static class ContainerIT extends MongoDbEmbeddingStoreIT {
        @BeforeAll
        static void start() {
            assertDoContainerTests();
        }

        @Override
        MongoClient createClient() {
            return createClientFromContainer();
        }
    }

    MongoClient createClient() {
        return createClientFromEnv();
    }

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return fixture.getEmbeddingStore();
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return EMBEDDING_MODEL;
    }

    @AfterEach
    void afterEach() {
        fixture.afterTests();
    }


    @Override
    protected void ensureStoreIsReady() {
        // to avoid "cannot query search index while in state INITIAL_SYNC" error
        awaitUntilAsserted(() -> assertThatNoException().isThrownBy(this::getAllEmbeddings));
    }
}
