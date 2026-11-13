package dev.langchain4j.store.embedding.generic;

import static org.testcontainers.shaded.org.apache.commons.lang3.RandomUtils.nextInt;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithFilteringIT;
import dev.langchain4j.store.embedding.hibernate.DatabaseKind;
import dev.langchain4j.store.embedding.hibernate.DistanceFunction;
import dev.langchain4j.store.embedding.hibernate.HibernateEmbeddingStore;
import org.junit.jupiter.api.AfterEach;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The embedding model returns normalized embeddings, so the inner product scores must match the cosine scores
 * that the shared tests expect.
 */
@Testcontainers
class PgVectorInnerProductHibernateEmbeddingStoreIT extends EmbeddingStoreWithFilteringIT {

    @Container
    static PostgreSQLContainer<?> databaseContainer = new PostgreSQLContainer<>("pgvector/pgvector:pg15");

    private final EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    private HibernateEmbeddingStore<?> embeddingStore;

    @Override
    protected void ensureStoreIsReady() {
        embeddingStore = HibernateEmbeddingStore.dynamicBuilder()
                .databaseKind(DatabaseKind.POSTGRESQL)
                .host(databaseContainer.getHost())
                .port(databaseContainer.getFirstMappedPort())
                .database(databaseContainer.getDatabaseName())
                .user(databaseContainer.getUsername())
                .password(databaseContainer.getPassword())
                .table("test" + nextInt(1, 1000))
                .dimension(embeddingModel.dimension())
                .distanceFunction(DistanceFunction.INNER_PRODUCT)
                .createIndex(true)
                .indexType("ivfflat")
                .indexOptions("lists = 1")
                .createTable(true)
                .dropTableFirst(true)
                .build();
    }

    @AfterEach
    void clearData() {
        if (embeddingStore != null) {
            embeddingStore.close();
        }
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
    protected boolean supportsContains() {
        return true;
    }
}
