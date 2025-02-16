package dev.langchain4j.store.embedding.oracle;

import static dev.langchain4j.store.embedding.oracle.CommonTestOperations.dropTable;
import static dev.langchain4j.store.embedding.oracle.CommonTestOperations.newEmbeddingStoreBuilder;

import java.sql.SQLException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Test cases which configure {@link OracleEmbeddingStore} with both exact and approximate search options.
 */
class ExactSearchIT {

    /** Verifies all distance metrics with approximate search */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    private void search(boolean isExactSearch) throws SQLException {

        OracleEmbeddingStore oracleEmbeddingStore =
                newEmbeddingStoreBuilder().exactSearch(isExactSearch).build();

        try {
            CommonTestOperations.verifySearch(oracleEmbeddingStore);
        } finally {
            dropTable();
        }
    }
}
