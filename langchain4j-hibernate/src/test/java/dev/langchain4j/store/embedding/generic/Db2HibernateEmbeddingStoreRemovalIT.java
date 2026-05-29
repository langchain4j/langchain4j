package dev.langchain4j.store.embedding.generic;

import static org.testcontainers.shaded.org.apache.commons.lang3.RandomUtils.nextInt;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithRemovalIT;
import dev.langchain4j.store.embedding.hibernate.DatabaseKind;
import dev.langchain4j.store.embedding.hibernate.HibernateEmbeddingStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.containers.Db2Container;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class Db2HibernateEmbeddingStoreRemovalIT extends EmbeddingStoreWithRemovalIT {

    @Container
    static Db2Container dbContainer = new Db2Container("icr.io/db2_community/db2:12.1.2.0")
            .acceptLicense()
            .withPrivilegedMode(true);

    EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    private HibernateEmbeddingStore<?> embeddingStore;

    @BeforeEach
    protected void beforeEach() {
        embeddingStore = HibernateEmbeddingStore.dynamicBuilder()
                .databaseKind(DatabaseKind.DB2)
                .host(dbContainer.getHost())
                .port(dbContainer.getFirstMappedPort())
                .database(dbContainer.getDatabaseName())
                .user(dbContainer.getUsername())
                .password(dbContainer.getPassword())
                .table("test" + nextInt(2000, 3000))
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
