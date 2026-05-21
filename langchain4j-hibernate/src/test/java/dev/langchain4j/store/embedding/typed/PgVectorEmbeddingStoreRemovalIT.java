package dev.langchain4j.store.embedding.typed;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithRemovalIT;
import dev.langchain4j.store.embedding.hibernate.HibernateEmbeddingStore;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.hibernate.cfg.SchemaToolingSettings;
import org.hibernate.tool.schema.Action;
import org.hibernate.tool.schema.SourceType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class PgVectorEmbeddingStoreRemovalIT extends EmbeddingStoreWithRemovalIT {

    @Container
    static PostgreSQLContainer<?> pgVector = new PostgreSQLContainer<>("pgvector/pgvector:pg15");

    static SessionFactory sessionFactory;
    EmbeddingStore<TextSegment> embeddingStore;

    EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    @BeforeAll
    static void setup() {
        sessionFactory = new Configuration()
                .addAnnotatedClass(GenericEmbeddingEntity.class)
                .setJdbcUrl(pgVector.getJdbcUrl())
                .setCredentials("test", "test")
                .setSchemaExportAction(Action.CREATE_DROP)
                .setProperty(SchemaToolingSettings.JAKARTA_HBM2DDL_CREATE_SOURCE, SourceType.SCRIPT_THEN_METADATA)
                .setProperty(SchemaToolingSettings.JAKARTA_HBM2DDL_CREATE_SCRIPT_SOURCE, "/setup.sql")
                .setProperty(SchemaToolingSettings.JAKARTA_HBM2DDL_LOAD_SCRIPT_SOURCE, "/import-generic.sql")
                .buildSessionFactory();
    }

    @BeforeEach
    void beforeEach() {
        embeddingStore = HibernateEmbeddingStore.builder(GenericEmbeddingEntity.class)
                .sessionFactory(sessionFactory)
                .build();
    }

    @AfterEach
    void clearData() {
        embeddingStore = null;
        if (sessionFactory != null) {
            sessionFactory.getSchemaManager().truncate();
        }
    }

    @AfterAll
    static void tearDown() {
        if (sessionFactory != null) {
            sessionFactory.close();
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
