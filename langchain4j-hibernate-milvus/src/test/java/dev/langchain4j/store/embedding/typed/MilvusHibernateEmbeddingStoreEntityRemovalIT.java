package dev.langchain4j.store.embedding.typed;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithRemovalIT;
import dev.langchain4j.store.embedding.hibernate.HibernateEmbeddingStore;
import dev.langchain4j.store.embedding.hibernate.milvus.MilvusDatabaseKind;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.hibernate.tool.schema.Action;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.milvus.MilvusContainer;

@Testcontainers
class MilvusHibernateEmbeddingStoreEntityRemovalIT extends EmbeddingStoreWithRemovalIT {

    static {
        // Register the driver class to allow resolving from the JDBC URL
        try {
            DriverManager.registerDriver(new org.hibernate.milvus.jdbc.internal.MilvusDriver());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Container
    static MilvusContainer databaseContainer =
            new MilvusContainer("milvusdb/milvus:v2.6.20").withEnv("DEPLOY_MODE", "STANDALONE");

    static SessionFactory sessionFactory;
    EmbeddingStore<TextSegment> embeddingStore;

    EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    @BeforeAll
    static void setup() {
        final Configuration configuration = new Configuration()
                .addAnnotatedClass(GenericEmbeddingEntity.class)
                .setJdbcUrl("jdbc:milvus://" + databaseContainer.getHost() + ":"
                        + databaseContainer.getMappedPort(19530) + "/default");
        if (System.getenv("MILVUS_USERNAME") != null || System.getenv("MILVUS_PASSWORD") != null) {
            configuration.setCredentials(System.getenv("MILVUS_USERNAME"), System.getenv("MILVUS_PASSWORD"));
        }
        sessionFactory = configuration.setSchemaExportAction(Action.CREATE_DROP).buildSessionFactory();
    }

    @BeforeEach
    void beforeEach() {
        embeddingStore = HibernateEmbeddingStore.builder(GenericEmbeddingEntity.class)
                .sessionFactory(sessionFactory)
                .databaseKind(MilvusDatabaseKind.INSTANCE)
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
