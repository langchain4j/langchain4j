package dev.langchain4j.store.embedding.oracle.vecdb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.store.embedding.oracle.CreateOption;
import dev.langchain4j.store.embedding.oracle.vecdb.enums.VecDbDistanceMetric;
import java.sql.SQLException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies the VecDB vector-table lifecycle selected by {@link CreateOption}.
 */
@Testcontainers(disabledWithoutDocker = true)
class VecDbSchemaManagerTableIT {

    private static final String TABLE_NAME = "LC4J_VECDB_TABLE_IT";
    private static final Embedding TEST_EMBEDDING = new Embedding(new float[] {1.0f, 0.0f, 0.0f});

    @BeforeEach
    void removeExistingTable() throws SQLException {
        VecDbTestOperations.dropVectorTable(TABLE_NAME);
    }

    @AfterEach
    void removeTestTable() throws SQLException {
        VecDbTestOperations.dropVectorTable(TABLE_NAME);
    }

    @Test
    void testCreateNoneFailsWhenTableIsMissing() {
        assertThatThrownBy(() -> createStore(CreateOption.CREATE_NONE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("VecDB table does not exist: " + TABLE_NAME);
    }

    @Test
    void testCreateNoneReusesExistingTable() throws SQLException {
        OracleVecDbEmbeddingStore initialStore = createStore(CreateOption.CREATE_IF_NOT_EXISTS);
        String id = initialStore.add(TEST_EMBEDDING);

        createStore(CreateOption.CREATE_NONE);

        assertThat(VecDbTestOperations.listVectorIds(TABLE_NAME)).containsExactly(id);
    }

    @Test
    void testCreateIfNotExistsCreatesMissingTable() throws SQLException {
        createStore(CreateOption.CREATE_IF_NOT_EXISTS);

        assertThat(VecDbTestOperations.vectorTableExists(TABLE_NAME)).isTrue();
    }

    @Test
    void testCreateIfNotExistsReusesExistingTable() throws SQLException {
        OracleVecDbEmbeddingStore initialStore = createStore(CreateOption.CREATE_IF_NOT_EXISTS);
        String id = initialStore.add(TEST_EMBEDDING);

        createStore(CreateOption.CREATE_IF_NOT_EXISTS);

        assertThat(VecDbTestOperations.listVectorIds(TABLE_NAME)).containsExactly(id);
    }

    @Test
    void testCreateOrReplaceCreatesMissingTable() throws SQLException {
        createStore(CreateOption.CREATE_OR_REPLACE);

        assertThat(VecDbTestOperations.vectorTableExists(TABLE_NAME)).isTrue();
    }

    @Test
    void testCreateOrReplaceReplacesExistingTable() throws SQLException {
        OracleVecDbEmbeddingStore initialStore = createStore(CreateOption.CREATE_IF_NOT_EXISTS);
        initialStore.add(TEST_EMBEDDING);

        createStore(CreateOption.CREATE_OR_REPLACE);

        assertThat(VecDbTestOperations.listVectorIds(TABLE_NAME)).isEmpty();
    }

    private static OracleVecDbEmbeddingStore createStore(CreateOption createOption) {
        return OracleVecDbEmbeddingStore.builder()
                .dataSource(VecDbTestOperations.dataSource())
                .embeddingTable(TABLE_NAME, createOption)
                .distanceMetric(VecDbDistanceMetric.COSINE)
                .build();
    }
}
