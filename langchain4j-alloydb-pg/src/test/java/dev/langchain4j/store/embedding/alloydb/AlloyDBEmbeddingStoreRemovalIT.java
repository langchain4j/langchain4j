package dev.langchain4j.store.embedding.alloydb;

import static org.testcontainers.shaded.org.apache.commons.lang3.RandomUtils.nextInt;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.engine.AlloyDBEngine;
import dev.langchain4j.engine.EmbeddingStoreConfig;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithRemovalIT;
import dev.langchain4j.store.embedding.index.DistanceStrategy;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public class AlloyDBEmbeddingStoreRemovalIT extends EmbeddingStoreWithRemovalIT {

    @Container
    static PostgreSQLContainer<?> pgVector = new PostgreSQLContainer<>("pgvector/pgvector:pg15");

    final String tableName = "test" + nextInt(2000, 3000);
    static AlloyDBEngine engine;
    EmbeddingStore<TextSegment> embeddingStore;
    EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    @BeforeAll
    public static void startEngine() {
        if (engine == null) {
            engine = new AlloyDBEngine.Builder()
                    .host(pgVector.getHost())
                    .port(pgVector.getFirstMappedPort())
                    .user("test")
                    .password("test")
                    .database("test")
                    .build();
        }
    }

    @AfterAll
    public static void stopEngine() {
        engine.close();
    }

    protected void ensureStoreIsReady() {
        engine.initVectorStoreTable(new EmbeddingStoreConfig.Builder(tableName, 384).build());

        embeddingStore = new AlloyDBEmbeddingStore.Builder(engine, tableName)
                .distanceStrategy(DistanceStrategy.COSINE_DISTANCE)
                // .metadataColumns(metaColumnNames)
                .build();
    }

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        if (embeddingStore == null) {
            ensureStoreIsReady();
        }
        return embeddingStore;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return embeddingModel;
    }

    protected boolean supportsRemoveAllByFilter() {
        return false;
    }

    protected boolean supportsRemoveAll() {
        return false;
    }
}
