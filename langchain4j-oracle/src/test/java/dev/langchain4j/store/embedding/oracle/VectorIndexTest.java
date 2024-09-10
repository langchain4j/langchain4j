package dev.langchain4j.store.embedding.oracle;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static dev.langchain4j.store.embedding.oracle.CommonTestOperations.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests which verify all possible configurations of {@link OracleEmbeddingStore.Builder#vectorIndex(CreateOption)}
 */
public class VectorIndexTest {

    @ParameterizedTest
    @EnumSource(CreateOption.class)
    public void testCreateOption(CreateOption createOption) throws SQLException {
        OracleEmbeddingStore oracleEmbeddingStore =
                newEmbeddingStoreBuilder()
                        .vectorIndex(createOption)
                        .build();

        verifyIndexExists(createOption);

        try {
            verifySearch(oracleEmbeddingStore);
        }
        finally {
            dropTable();
        }
    }

    /**
     * Queries the USER_INDEXES view to verify that an index has been created or not. This method verifies that the
     * index is of the VECTOR type, and that it has the name specified in the JavaDoc of {@link OracleEmbeddingStore}:
     * {tableName}_EMBEDDING_INDEX.
     * @param createOption Option configured with {@link OracleEmbeddingStore.Builder#vectorIndex(CreateOption)}
     */
    private void verifyIndexExists(CreateOption createOption) throws SQLException {
        try (Connection connection = CommonTestOperations.getDataSource().getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT 'OK'" +
                             " FROM user_indexes" +
                             " WHERE table_name='" + TABLE_NAME + "'" +
                             " AND index_name='" + TABLE_NAME + "_EMBEDDING_INDEX'" +
                             " AND index_type='VECTOR'"
             )) {

            if (createOption == CreateOption.CREATE_NONE)
                assertFalse(resultSet.next());
            else
                assertTrue(resultSet.next());
        }
    }

}
