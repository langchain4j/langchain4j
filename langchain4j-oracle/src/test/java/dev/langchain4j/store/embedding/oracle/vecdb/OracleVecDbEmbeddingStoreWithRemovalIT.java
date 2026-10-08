package dev.langchain4j.store.embedding.oracle.vecdb;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithRemovalIT;
import java.sql.SQLException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies ID, filter, and full-table removal behavior against VecDB.
 */
@Testcontainers(disabledWithoutDocker = true)
class OracleVecDbEmbeddingStoreWithRemovalIT extends EmbeddingStoreWithRemovalIT {

    protected static final String TABLE_NAME = "LC4J_VECDB_REMOVE_IT";

    private OracleVecDbEmbeddingStore embeddingStore;

    @BeforeEach
    void prepareEmptyStore() {
        if (embeddingStore == null) {
            embeddingStore = createEmbeddingStore();
        } else {
            embeddingStore.removeAll();
        }
    }

    /** Creates the store configuration exercised by the inherited removal contract. */
    protected OracleVecDbEmbeddingStore createEmbeddingStore() {
        return VecDbTestOperations.createStore(TABLE_NAME);
    }

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return embeddingStore;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return VecDbTestOperations.embeddingModel();
    }

    @AfterAll
    static void dropVectorTable() throws SQLException {
        VecDbTestOperations.dropVectorTable(TABLE_NAME);
    }
}
