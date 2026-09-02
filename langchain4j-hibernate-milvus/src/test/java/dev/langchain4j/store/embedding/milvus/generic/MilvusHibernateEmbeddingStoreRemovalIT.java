package dev.langchain4j.store.embedding.milvus.generic;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithRemovalIT;
import dev.langchain4j.store.embedding.hibernate.HibernateEmbeddingStore;
import dev.langchain4j.store.embedding.hibernate.milvus.MilvusDatabaseKind;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.milvus.MilvusContainer;

@Testcontainers
class MilvusHibernateEmbeddingStoreRemovalIT extends EmbeddingStoreWithRemovalIT {

    @Container
    static MilvusContainer databaseContainer =
            new MilvusContainer("milvusdb/milvus:v2.6.22").withEnv("DEPLOY_MODE", "STANDALONE");

    EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    private HibernateEmbeddingStore<?> embeddingStore;

    @BeforeEach
    protected void beforeEach() {
        // The test container runs Milvus without authentication, so no credentials are configured
        embeddingStore = HibernateEmbeddingStore.dynamicBuilder()
                .databaseKind(MilvusDatabaseKind.INSTANCE)
                .host(databaseContainer.getHost())
                .port(databaseContainer.getMappedPort(19530))
                .database("default")
                .table("test_" + UUID.randomUUID().toString().replace("-", ""))
                .dimension(384)
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
}
