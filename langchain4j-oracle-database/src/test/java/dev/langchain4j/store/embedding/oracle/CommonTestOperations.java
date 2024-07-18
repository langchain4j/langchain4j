package dev.langchain4j.store.embedding.oracle;

import dev.langchain4j.model.embedding.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import oracle.jdbc.datasource.OracleDataSource;

import javax.sql.DataSource;
import java.sql.SQLException;

/**
 * A collection of operations which are shared by tests in this package.
 */
final class CommonTestOperations {

    /**
     * Model used to generate embeddings for this test. The all-MiniLM-L6-v2 model is chosen for consistency with other
     * implementations of EmbeddingStoreIT.
     */
    private static final EmbeddingModel EMBEDDING_MODEL = new AllMiniLmL6V2QuantizedEmbeddingModel();

    private CommonTestOperations() {}

    private static final DataSource DATA_SOURCE;
    static {
        try {
            OracleDataSource oracleDataSource = new oracle.jdbc.datasource.impl.OracleDataSource();
            oracleDataSource.setURL("jdbc:oracle:thin:@test");
            oracleDataSource.setUser("test");
            oracleDataSource.setPassword("test");
            DATA_SOURCE = new TestDataSource(oracleDataSource);
        } catch (
                SQLException sqlException) {
            throw new AssertionError(sqlException);
        }
    }

    static EmbeddingModel getEmbeddingModel() {
        return EMBEDDING_MODEL;
    }

    static DataSource getDataSource() {
        return DATA_SOURCE;
    }



}
