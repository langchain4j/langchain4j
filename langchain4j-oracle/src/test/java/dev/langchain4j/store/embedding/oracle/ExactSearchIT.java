package dev.langchain4j.store.embedding.oracle;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.sql.SQLException;

import static dev.langchain4j.store.embedding.oracle.CommonTestOperations.dropTable;
import static dev.langchain4j.store.embedding.oracle.CommonTestOperations.newEmbeddingStoreBuilder;

/**
 * Test cases which configure {@link OracleEmbeddingStore} with both exact and approximate search options.
 */
public class ExactSearchIT {

    /** Verifies all distance metrics with approximate search */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    private void testSearch(boolean isExactSearch) throws SQLException  {

        OracleEmbeddingStore oracleEmbeddingStore =
                newEmbeddingStoreBuilder()
                   .exactSearch(isExactSearch)
                   .build();

        try {
            CommonTestOperations.verifySearch(oracleEmbeddingStore);
        }
        finally {
            dropTable();
        }
    }

}
