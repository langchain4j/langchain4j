package dev.langchain4j.store.embedding.milvus.generic;

import static dev.langchain4j.store.embedding.milvus.generic.MilvusHibernateEmbeddingStoreIT.MILVUS_DOCKER_IMAGE;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithFilteringIT;
import dev.langchain4j.store.embedding.hibernate.DistanceFunction;
import dev.langchain4j.store.embedding.hibernate.HibernateEmbeddingStore;
import dev.langchain4j.store.embedding.hibernate.milvus.MilvusDatabaseKind;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.milvus.MilvusContainer;

/**
 * The embedding model returns normalized embeddings, so the inner product scores must match the cosine scores
 * that the shared tests expect.
 */
@Testcontainers
class MilvusInnerProductHibernateEmbeddingStoreIT extends EmbeddingStoreWithFilteringIT {

    @Container
    static MilvusContainer databaseContainer =
            new MilvusContainer(MILVUS_DOCKER_IMAGE).withEnv("DEPLOY_MODE", "STANDALONE");

    private final EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    private HibernateEmbeddingStore<?> embeddingStore;

    @Override
    protected void ensureStoreIsReady() {
        // createIndex() is not set: the collection always gets a vector index with the metric of the distance function
        embeddingStore = HibernateEmbeddingStore.dynamicBuilder()
                .databaseKind(MilvusDatabaseKind.INSTANCE)
                .host(databaseContainer.getHost())
                .port(databaseContainer.getMappedPort(19530))
                .database("default")
                .table("test_" + UUID.randomUUID().toString().replace("-", ""))
                .dimension(embeddingModel.dimension())
                .distanceFunction(DistanceFunction.INNER_PRODUCT)
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
