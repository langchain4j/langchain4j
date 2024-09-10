package dev.langchain4j.store.embedding.oracle;

import oracle.sql.json.OracleJsonObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.sql.*;
import java.util.stream.Stream;

import static dev.langchain4j.store.embedding.oracle.CommonTestOperations.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests which verify all possible configurations of {@link OracleEmbeddingStore.Builder#vectorIndex(CreateOption)}
 */
public class VectorIndexIT {

    @ParameterizedTest
    @EnumSource(CreateOption.class)
    public void testCreateOption(CreateOption createOption) throws SQLException {
        OracleEmbeddingStore oracleEmbeddingStore =
                newEmbeddingStoreBuilder()
                        .index(Index.ivfIndexBuilder().createOption(createOption).build())
                        .build();

        verifyIndexExists(createOption);

        try {
            verifySearch(oracleEmbeddingStore);
        }
        finally {
            dropTable();
        }
    }

    @ParameterizedTest
    @MethodSource("createIndexArguments")
    public void testCreateIndexOnStoreCreation(
        int targetAccuracy,
        int degreeOfParallelism,
        int neighborPartitions,
        int samplePerPartition,
        int minVectorsPerPartition) throws Exception {

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
                .dataSource(CommonTestOperations.getDataSource())
                .embeddingTable(EmbeddingTable
                    .builder()
                    .createOption(CreateOption.CREATE_IF_NOT_EXISTS)
                    .name(TABLE_NAME)
                    .build())
                .index(ivfIndexBuilder.build())
                .build();


            try (Connection connection = CommonTestOperations.getSysDBADataSource().getConnection();
                 PreparedStatement stmt = connection.prepareStatement("select IDX_PARAMS from vecsys.vector$index where IDX_NAME = ?")
            ) {
                stmt.setString(1, TABLE_NAME + "_VECTOR_INDEX");
                ResultSet rs = stmt.executeQuery();
                Assertions.assertTrue(rs.next(), "A index should be returned");
                OracleJsonObject params = rs.getObject("IDX_PARAMS", OracleJsonObject.class);
                assertIndexType("IVF_FLAT", params);
                assertTargetAccuracy(targetAccuracy, params);
                assertDegreeOfParallelism(degreeOfParallelism, params);
                assertNeighborPartitions(neighborPartitions, params);
                assertSamplePerPartition(samplePerPartition, params);
                assertMinVectorsPerPartition(minVectorsPerPartition, params);
                Assertions.assertFalse(rs.next(), "Only one index should be returned");
            }
            verifySearch(oracleEmbeddingStore);
        } finally {
            dropTable(TABLE_NAME);
        }
    }

    @Test
    public void testMetadataKeyAndVectorIndex() throws SQLException {
        try {
            Index jsonIndex = Index.jsonIndexBuilder()
                .createOption(CreateOption.CREATE_OR_REPLACE)
                .name("JSON_INDEX")
                .key("key", Integer.class, JSONIndexBuilder.Order.ASC)
                .build();

            Index ivfIndex =  Index.ivfIndexBuilder()
                .createOption(CreateOption.CREATE_IF_NOT_EXISTS)
                .minVectorsPerPartition(10)
                .neighborPartitions(3)
                .samplePerPartition(15)
                .targetAccuracy(90)
                .build();

            OracleEmbeddingStore oracleEmbeddingStore = OracleEmbeddingStore
                .builder()
                .dataSource(CommonTestOperations.getDataSource())
                .embeddingTable(EmbeddingTable
                    .builder()
                    .createOption(CreateOption.CREATE_IF_NOT_EXISTS)
                    .name(TABLE_NAME)
                    .build())
                .index(jsonIndex, ivfIndex)
                .build();

            verifyIndexExists(CreateOption.CREATE_OR_REPLACE,
                TABLE_NAME,
                "JSON_INDEX",
                "FUNCTION-BASED NORMAL");

            verifyIndexExists(CreateOption.CREATE_OR_REPLACE,
                TABLE_NAME,
                TABLE_NAME + "_VECTOR_INDEX",
                "VECTOR");

            verifySearch(oracleEmbeddingStore);
        } finally {
            dropTable(TABLE_NAME);
        }
    }

    @Test
    public void testMetadataKeysIndex() throws SQLException {
        try {
            Index jsonIndex = Index.jsonIndexBuilder()
                .name("JSON_INDEX")
                .key("key", Integer.class, JSONIndexBuilder.Order.ASC)
                .key("name", String.class, JSONIndexBuilder.Order.DESC)
                .createOption(CreateOption.CREATE_OR_REPLACE)
                .build();

            OracleEmbeddingStore oracleEmbeddingStore = OracleEmbeddingStore
                .builder()
                .dataSource(CommonTestOperations.getDataSource())
                .embeddingTable(EmbeddingTable
                    .builder()
                    .createOption(CreateOption.CREATE_IF_NOT_EXISTS)
                    .name(TABLE_NAME)
                    .build())
                .index(jsonIndex)
                .build();

            verifyIndexExists(CreateOption.CREATE_OR_REPLACE,
                TABLE_NAME,
                "JSON_INDEX",
                "FUNCTION-BASED NORMAL");

            verifySearch(oracleEmbeddingStore);
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
    private void verifyIndexExists(CreateOption createOption, String tableName, String indexName, String indexType) throws SQLException {
        try (Connection connection = CommonTestOperations.getDataSource().getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                 "SELECT 'OK'" +
                     " FROM user_indexes" +
                     " WHERE table_name='" + tableName + "'" +
                     " AND index_name='" + indexName + "'" +
                     " AND index_type='" + indexType + "'"
             )) {

            if (createOption == CreateOption.DO_NOT_CREATE)
                assertFalse(resultSet.next());
            else
                assertTrue(resultSet.next());
        }

    }

    private void assertIndexType(String expectedIndexType, OracleJsonObject params) {
        Assertions.assertEquals("IVF_FLAT", params.getString("type"), "Unexpected index type");
    }

    private void assertTargetAccuracy(int expectedTargetAccuracy, OracleJsonObject params) {
        if (expectedTargetAccuracy < 0) { return; }
        Assertions.assertEquals(expectedTargetAccuracy, params.getInt("accuracy"), "Unexpected accuracy");
    }

    private void assertDegreeOfParallelism(int expectedDegreeOfParallelism, OracleJsonObject params) {
        if (expectedDegreeOfParallelism < 0) { return; }
        Assertions.assertEquals(expectedDegreeOfParallelism, params.getInt("degree_of_parallelism"), "Unexpected degree of parallelism");
    }

    private void assertNeighborPartitions(int expectedNeighborPartitions, OracleJsonObject params) {
        if (expectedNeighborPartitions < 0) { return; }
        Assertions.assertEquals(expectedNeighborPartitions, params.getInt("target_centroids"), "Unexpected neighbor partitions");
    }

    private void assertSamplePerPartition(int expectedSamplePerPartition, OracleJsonObject params) {
        if (expectedSamplePerPartition < 0) { return; }
        Assertions.assertEquals(expectedSamplePerPartition, params.getInt("samples_per_partition"), "Unexpected samples per partition");
    }

    private void assertMinVectorsPerPartition(int expectedMinVectorsPerPartition, OracleJsonObject params) {
        if (expectedMinVectorsPerPartition < 0) { return; }
        Assertions.assertEquals(expectedMinVectorsPerPartition, params.getInt("min_vectors_per_partition"), "Unexpected vectors per partition");
    }

    static Stream<Arguments> createIndexArguments() {
        return Stream.of(
            Arguments.arguments(-1, -1, -1, -1, -1),
            Arguments.arguments(80, 1, 5, 2, 3),
            Arguments.arguments(50, 2, 4, 7, 1),
            Arguments.arguments(70, 3, -1, 2, 3),
            Arguments.arguments(90, 4, 5, -1, 3),
            Arguments.arguments(95, 5, 5, 2, -1)
        );
    }


}
