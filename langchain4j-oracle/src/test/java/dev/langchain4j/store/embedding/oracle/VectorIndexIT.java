package dev.langchain4j.store.embedding.oracle;

import static dev.langchain4j.store.embedding.oracle.CommonTestOperations.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.sql.*;
import java.util.stream.Stream;
import oracle.sql.json.OracleJsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests which verify all possible configurations of {@link OracleEmbeddingStore.Builder#vectorIndex(CreateOption)}
 */
class VectorIndexIT {

    /**
     * Verifies that the index is being create or not depending on the {@link CreateOption}
     * @param createOption the CreateOption
     * @throws SQLException throws an exception if an unexpected error occurs.
     */
    @ParameterizedTest
    @EnumSource(CreateOption.class)
    void createOption(CreateOption createOption) throws SQLException {
        OracleEmbeddingStore oracleEmbeddingStore = newEmbeddingStoreBuilder()
                .index(Index.ivfIndexBuilder().createOption(createOption).build())
                .build();

        verifyIndexExists(createOption);

        try {
            verifySearch(oracleEmbeddingStore);
        } finally {
            dropTable();
        }
    }

    /**
     * Verifies that all IVF index arguments are being correcty set on the index.
     * @param targetAccuracy the target accuracy
     * @param degreeOfParallelism the drgree of parallelism
     * @param neighborPartitions the number of neighbor partitions
     * @param samplePerPartition the number of samples per partition
     * @param minVectorsPerPartition the nminimus number of vectors per partition
     * @throws Exception throws an exception if an unexpected error occurs.
     */
    @ParameterizedTest
    @MethodSource("createIndexArguments")
    void createIndexOnStoreCreation(
            int targetAccuracy,
            int degreeOfParallelism,
            int neighborPartitions,
            int samplePerPartition,
            int minVectorsPerPartition)
            throws Exception {

        try {
            IVFIndexBuilder ivfIndexBuilder = Index.ivfIndexBuilder().createOption(CreateOption.CREATE_OR_REPLACE);
            if (targetAccuracy >= 0) {
                ivfIndexBuilder.targetAccuracy(targetAccuracy);
            }
            if (degreeOfParallelism >= 0) {
                ivfIndexBuilder.degreeOfParallelism(degreeOfParallelism);
            }
            if (neighborPartitions >= 0) {
                ivfIndexBuilder.neighborPartitions(neighborPartitions);
            }
            if (samplePerPartition >= 0) {
                ivfIndexBuilder.samplePerPartition(samplePerPartition);
            }
            if (minVectorsPerPartition >= 0) {
                ivfIndexBuilder.minVectorsPerPartition(minVectorsPerPartition);
            }

            OracleEmbeddingStore oracleEmbeddingStore = OracleEmbeddingStore.builder()
                    .dataSource(getDataSource())
                    .embeddingTable(EmbeddingTable.builder()
                            .createOption(CreateOption.CREATE_IF_NOT_EXISTS)
                            .name(TABLE_NAME)
                            .build())
                    .index(ivfIndexBuilder.build())
                    .build();

            try (Connection connection = getSysDBADataSource().getConnection();
                    PreparedStatement stmt = connection.prepareStatement(
                            "select IDX_PARAMS from vecsys.vector$index where IDX_NAME = ?")) {
                stmt.setString(1, TABLE_NAME + "_VECTOR_INDEX");
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    OracleJsonObject params = rs.getObject("IDX_PARAMS", OracleJsonObject.class);
                    assertIndexType("IVF_FLAT", params);
                    assertTargetAccuracy(targetAccuracy, params);
                    assertDegreeOfParallelism(degreeOfParallelism, params);
                    assertNeighborPartitions(neighborPartitions, params);
                    assertSamplePerPartition(samplePerPartition, params);
                    assertMinVectorsPerPartition(minVectorsPerPartition, params);
                    assertThat(rs.next())
                            .as("Only one index should be returned")
                            .isFalse();
                } else {
                    fail("The result set should have returned a line");
                }
            }
            verifySearch(oracleEmbeddingStore);
        } finally {
            dropTable(TABLE_NAME);
        }
    }

    /**
     * Verifies that it is possible to create a store with both a JSON index and
     * a IVF index. That the vectors are created and the search works.
     * @throws SQLException throws an exception if an unexpected error occurs.
     */
    @Test
    void metadataKeyAndVectorIndex() throws SQLException {
        try {
            Index jsonIndex = Index.jsonIndexBuilder()
                    .createOption(CreateOption.CREATE_OR_REPLACE)
                    .name("JSON_INDEX")
                    .key("key", Integer.class, JSONIndexBuilder.Order.ASC)
                    .build();

            Index ivfIndex = Index.ivfIndexBuilder()
                    .createOption(CreateOption.CREATE_IF_NOT_EXISTS)
                    .minVectorsPerPartition(10)
                    .neighborPartitions(3)
                    .samplePerPartition(15)
                    .targetAccuracy(90)
                    .build();

            OracleEmbeddingStore oracleEmbeddingStore = OracleEmbeddingStore.builder()
                    .dataSource(getDataSource())
                    .embeddingTable(EmbeddingTable.builder()
                            .createOption(CreateOption.CREATE_IF_NOT_EXISTS)
                            .name(TABLE_NAME)
                            .build())
                    .index(jsonIndex, ivfIndex)
                    .build();

            verifyIndexExists(CreateOption.CREATE_OR_REPLACE, TABLE_NAME, "JSON_INDEX", "FUNCTION-BASED NORMAL");

            verifyIndexExists(CreateOption.CREATE_OR_REPLACE, TABLE_NAME, TABLE_NAME + "_VECTOR_INDEX", "VECTOR");

            verifySearch(oracleEmbeddingStore);
        } finally {
            dropTable(TABLE_NAME);
        }
    }

    /**
     * Verifies that it is possible to create a store with a JSON index and that
     * the search works.
     * @throws SQLException throws an exception if an unexpected error occurs.
     */
    @Test
    void metadataKeysIndex() throws SQLException {
        try {
            Index jsonIndex = Index.jsonIndexBuilder()
                    .name("JSON_INDEX")
                    .key("key", Integer.class, JSONIndexBuilder.Order.ASC)
                    .key("name", String.class, JSONIndexBuilder.Order.DESC)
                    .createOption(CreateOption.CREATE_OR_REPLACE)
                    .build();

            OracleEmbeddingStore oracleEmbeddingStore = OracleEmbeddingStore.builder()
                    .dataSource(getDataSource())
                    .embeddingTable(EmbeddingTable.builder()
                            .createOption(CreateOption.CREATE_IF_NOT_EXISTS)
                            .name(TABLE_NAME)
                            .build())
                    .index(jsonIndex)
                    .build();

            verifyIndexExists(CreateOption.CREATE_OR_REPLACE, TABLE_NAME, "JSON_INDEX", "FUNCTION-BASED NORMAL");

            verifySearch(oracleEmbeddingStore);
        } finally {
            dropTable(TABLE_NAME);
        }
    }

    /**
     * This tests verifies that creating the EmbeddingStore will fail if the
     * index name is invalid (reserved word, unquoted and starting not starting
     * by an alphabetic character, too long).
     * @param indexName the name of the index
     * @throws Exception if an exception other than the expected exception occurs.
     */
    @ParameterizedTest
    @ValueSource(
            strings = {
                "CREATE",
                "012sdf",
                "azertyuiopazertyuiopazertyuiopazertyuiopazertyuiopazertyuiopazertyuiopazertyuiopazertyuiopazertyuiopazertyuiopazertyuiopazertyuiopazertyuiopazertyuiopazertyuiopazertyuiopazertyuiopazertyuiopazertyuiop"
            })
    void InvalidIndexNameTest(String indexName) throws Exception {
        try {
            OracleEmbeddingStore.builder()
                    .dataSource(getDataSource())
                    .embeddingTable(EmbeddingTable.builder()
                            .createOption(CreateOption.CREATE_OR_REPLACE)
                            .name(TABLE_NAME)
                            .build())
                    .index(Index.ivfIndexBuilder()
                            .name(indexName)
                            .createOption(CreateOption.CREATE_OR_REPLACE)
                            .build())
                    .build();
            fail("Previous statement should throw a runtime exception");
        } catch (RuntimeException runtimeException) {
            assertThat(runtimeException.getCause().getClass()).isSameAs(SQLSyntaxErrorException.class);
        } finally {
            dropTable(TABLE_NAME);
        }
        try {
            OracleEmbeddingStore.builder()
                    .dataSource(getDataSource())
                    .embeddingTable(EmbeddingTable.builder()
                            .createOption(CreateOption.CREATE_OR_REPLACE)
                            .name(TABLE_NAME)
                            .build())
                    .index(Index.jsonIndexBuilder()
                            .name(indexName)
                            .createOption(CreateOption.CREATE_OR_REPLACE)
                            .build())
                    .build();
            fail("Previous statement should throw a runtime exception");
        } catch (RuntimeException runtimeException) {
            assertThat(runtimeException.getCause().getClass()).isSameAs(SQLSyntaxErrorException.class);
        } finally {
            dropTable(TABLE_NAME);
        }
    }

    /**
     * Queries the USER_INDEXES view to verify that an index has been created or not. This method verifies that the
     * index is of the VECTOR type, and that it has the name specified in the JavaDoc of {@link OracleEmbeddingStore}:
     * {tableName}_EMBEDDING_INDEX.
     * @param createOption Option configured with {@link OracleEmbeddingStore.Builder#vectorIndex(CreateOption)}
     */
    private void verifyIndexExists(CreateOption createOption) throws SQLException {

        verifyIndexExists(createOption, TABLE_NAME, TABLE_NAME + "_VECTOR_INDEX", "VECTOR");
    }

    /**
     * Queries the USER_INDEXES view to verify that an index has been created or not. This method verifies that the
     * index is of the VECTOR type, and that it has the name specified in the JavaDoc of {@link OracleEmbeddingStore}:
     * {tableName}_EMBEDDING_INDEX.
     * @param createOption Option configured with {@link OracleEmbeddingStore.Builder#vectorIndex(CreateOption)}
     * @param tableName The name of the table.
     * @param indexName The name if the index.
     * @param indexType The type of index.
     */
    private void verifyIndexExists(CreateOption createOption, String tableName, String indexName, String indexType)
            throws SQLException {
        try (Connection connection = getDataSource().getConnection();
                PreparedStatement preparedStatement = connection.prepareStatement(
                        "SELECT 'OK' FROM user_indexes WHERE table_name=? AND index_name=? AND index_type=?")) {
            preparedStatement.setString(1, tableName);
            preparedStatement.setString(2, indexName);
            preparedStatement.setString(3, indexType);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (createOption == CreateOption.CREATE_NONE)
                    assertThat(resultSet.next()).isFalse();
                else assertThat(resultSet.next()).isTrue();
            }
        }
    }

    private void assertIndexType(String expectedIndexType, OracleJsonObject params) {
        assertThat(params.getString("type")).as("Unexpected index type").isEqualTo(expectedIndexType);
    }

    private void assertTargetAccuracy(int expectedTargetAccuracy, OracleJsonObject params) {
        if (expectedTargetAccuracy < 0) {
            return;
        }
        assertThat(params.getInt("accuracy")).as("Unexpected accuracy").isEqualTo(expectedTargetAccuracy);
    }

    private void assertDegreeOfParallelism(int expectedDegreeOfParallelism, OracleJsonObject params) {
        if (expectedDegreeOfParallelism < 0) {
            return;
        }
        assertThat(params.getInt("degree_of_parallelism"))
                .as("Unexpected degree of parallelism")
                .isEqualTo(expectedDegreeOfParallelism);
    }

    private void assertNeighborPartitions(int expectedNeighborPartitions, OracleJsonObject params) {
        if (expectedNeighborPartitions < 0) {
            return;
        }
        assertThat(params.getInt("target_centroids"))
                .as("Unexpected neighbor partitions")
                .isEqualTo(expectedNeighborPartitions);
    }

    private void assertSamplePerPartition(int expectedSamplePerPartition, OracleJsonObject params) {
        if (expectedSamplePerPartition < 0) {
            return;
        }
        assertThat(params.getInt("samples_per_partition"))
                .as("Unexpected samples per partition")
                .isEqualTo(expectedSamplePerPartition);
    }

    private void assertMinVectorsPerPartition(int expectedMinVectorsPerPartition, OracleJsonObject params) {
        if (expectedMinVectorsPerPartition < 0) {
            return;
        }
        assertThat(params.getInt("min_vectors_per_partition"))
                .as("Unexpected vectors per partition")
                .isEqualTo(expectedMinVectorsPerPartition);
    }

    static Stream<Arguments> createIndexArguments() {
        return Stream.of(
                Arguments.arguments(-1, -1, -1, -1, -1),
                Arguments.arguments(80, 1, 5, 2, 3),
                Arguments.arguments(50, 2, 4, 7, 1),
                Arguments.arguments(70, 3, -1, 2, 3),
                Arguments.arguments(90, 4, 5, -1, 3),
                Arguments.arguments(95, 5, 5, 2, -1));
    }
}
