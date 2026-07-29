package dev.langchain4j.store.embedding.oracle.vecdb;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.store.embedding.oracle.CreateOption;
import dev.langchain4j.store.embedding.oracle.vecdb.enums.VecDbDistanceMetric;
import java.sql.SQLException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies the VecDB vector-index lifecycle selected for an existing vector table.
 */
@Testcontainers(disabledWithoutDocker = true)
class VecDbSchemaManagerVectorIndexIT {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String TABLE_NAME = "LC4J_VECDB_VECTOR_INDEX_IT";
    private static final int INITIAL_ACCURACY = 80;
    private static final int UPDATED_ACCURACY = 90;

    @BeforeEach
    void createTableWithoutVectorIndex() throws SQLException {
        VecDbTestOperations.dropVectorTable(TABLE_NAME);
        createStore(CreateOption.CREATE_IF_NOT_EXISTS, vectorIndex(CreateOption.CREATE_NONE, INITIAL_ACCURACY));
    }

    @AfterEach
    void removeTestTable() throws SQLException {
        VecDbTestOperations.dropVectorTable(TABLE_NAME);
    }

    @Test
    void testCreateNoneDoesNotCreateMissingIndex() throws SQLException {
        createStore(CreateOption.CREATE_NONE, vectorIndex(CreateOption.CREATE_NONE, UPDATED_ACCURACY));

        assertThat(vectorIndexExists()).isFalse();
    }

    @Test
    void testCreateNoneKeepsExistingIndex() throws Exception {
        createStore(CreateOption.CREATE_NONE, vectorIndex(CreateOption.CREATE_IF_NOT_EXISTS, INITIAL_ACCURACY));

        createStore(CreateOption.CREATE_NONE, vectorIndex(CreateOption.CREATE_NONE, UPDATED_ACCURACY));

        assertThat(vectorIndexExists()).isTrue();
        assertThat(vectorIndexAccuracy()).isEqualTo(INITIAL_ACCURACY);
    }

    @Test
    void testCreateIfNotExistsCreatesMissingIndex() throws SQLException {
        createStore(CreateOption.CREATE_NONE, vectorIndex(CreateOption.CREATE_IF_NOT_EXISTS, INITIAL_ACCURACY));

        assertThat(vectorIndexExists()).isTrue();
    }

    @Test
    void testCreateIfNotExistsKeepsExistingIndex() throws Exception {
        createStore(CreateOption.CREATE_NONE, vectorIndex(CreateOption.CREATE_IF_NOT_EXISTS, INITIAL_ACCURACY));

        createStore(CreateOption.CREATE_NONE, vectorIndex(CreateOption.CREATE_IF_NOT_EXISTS, UPDATED_ACCURACY));

        assertThat(vectorIndexExists()).isTrue();
        assertThat(vectorIndexAccuracy()).isEqualTo(INITIAL_ACCURACY);
    }

    @Test
    void testCreateOrReplaceCreatesMissingIndex() throws SQLException {
        createStore(CreateOption.CREATE_NONE, vectorIndex(CreateOption.CREATE_OR_REPLACE, INITIAL_ACCURACY));

        assertThat(vectorIndexExists()).isTrue();
    }

    @Test
    void testCreateOrReplaceRebuildsExistingIndex() throws Exception {
        createStore(CreateOption.CREATE_NONE, vectorIndex(CreateOption.CREATE_IF_NOT_EXISTS, INITIAL_ACCURACY));

        createStore(CreateOption.CREATE_NONE, vectorIndex(CreateOption.CREATE_OR_REPLACE, UPDATED_ACCURACY));

        assertThat(vectorIndexExists()).isTrue();
        assertThat(vectorIndexAccuracy()).isEqualTo(UPDATED_ACCURACY);
    }

    private static OracleVecDbEmbeddingStore createStore(CreateOption tableCreateOption, VecDbVectorIndex vectorIndex) {
        return OracleVecDbEmbeddingStore.builder()
                .dataSource(VecDbTestOperations.dataSource())
                .embeddingTable(TABLE_NAME, tableCreateOption)
                .index(vectorIndex)
                .distanceMetric(VecDbDistanceMetric.COSINE)
                .build();
    }

    private static VecDbVectorIndex vectorIndex(CreateOption createOption, int accuracy) {
        return VecDbVectorIndex.ivfIndexBuilder()
                .createOption(createOption)
                .distanceMetric(VecDbDistanceMetric.COSINE)
                .accuracy(accuracy)
                .build();
    }

    private static boolean vectorIndexExists() throws SQLException {
        return VecDbTestOperations.indexStatus(TABLE_NAME).vectorIndexExists();
    }

    private static int vectorIndexAccuracy() throws SQLException, JsonProcessingException {
        JsonNode description = OBJECT_MAPPER.readTree(VecDbTestOperations.describeVectorTable(TABLE_NAME));
        return description
                .path("index_params")
                .path("vector_index_params")
                .path("accuracy")
                .asInt(-1);
    }
}
