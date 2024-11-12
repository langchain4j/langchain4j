package dev.langchain4j.store.embedding.mongodb;

import com.mongodb.client.MongoClient;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIT;
import dev.langchain4j.store.embedding.EmbeddingStoreWithFilteringIT;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;

import static dev.langchain4j.store.embedding.mongodb.MongoDbTestFixture.*;

class MongoDbEmbeddingStoreIT extends EmbeddingStoreWithFilteringIT {
    private MongoDbTestFixture helper = new MongoDbTestFixture(createClient()).initialize();

    MongoClient createClient() {
        return createDefaultClient();
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
