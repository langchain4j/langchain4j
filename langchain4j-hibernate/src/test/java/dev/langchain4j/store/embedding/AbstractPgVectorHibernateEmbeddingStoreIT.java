package dev.langchain4j.store.embedding;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public abstract class AbstractPgVectorHibernateEmbeddingStoreIT extends EmbeddingStoreWithFilteringIT {

    @Container
    protected static PostgreSQLContainer<?> pgVector = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    protected static SessionFactory sessionFactory;

    protected static EmbeddingStore<TextSegment> embeddingStore;

    protected EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    @BeforeEach
    void beforeEach() {
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
    protected void ensureStoreIsEmpty() {
        // it's not necessary to clear the store before every test
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
