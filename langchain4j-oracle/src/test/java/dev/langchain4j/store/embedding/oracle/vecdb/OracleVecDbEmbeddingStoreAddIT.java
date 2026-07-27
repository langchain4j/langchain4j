package dev.langchain4j.store.embedding.oracle.vecdb;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.embedding.Embedding;
import java.sql.SQLException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class OracleVecDbEmbeddingStoreAddIT {

    private static final String TABLE_NAME = "LANGCHAIN4J_VECDB_IT";

    private OracleVecDbEmbeddingStore embeddingStore;

    @BeforeEach
    void createStore() {
        embeddingStore = VecDbTestOperations.newEmbeddingStore(TABLE_NAME);
    }

    @Test
    void shouldAddEmbedding() throws SQLException {
        String id = embeddingStore.add(Embedding.from(new float[] {0.1f, 0.2f, 0.3f}));

        assertThat(id).isNotBlank();
        assertThat(VecDbTestOperations.numberOfStoredVectorsWithId(TABLE_NAME, id))
                .isOne();
    }

    @AfterAll
    static void dropVectorTable() throws SQLException {
        VecDbTestOperations.dropVectorTable(TABLE_NAME);
    }
}
