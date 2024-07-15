package dev.langchain4j.store.embedding.oracle;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithFilteringIT;
import oracle.jdbc.datasource.OracleDataSource;
import org.junit.jupiter.api.BeforeAll;

import javax.sql.DataSource;
import java.sql.SQLException;

//@Testcontainers
public class OracleEmbeddingStoreIT extends EmbeddingStoreWithFilteringIT {

    /**
     * Model used to generate embeddings for this test. The all-MiniLM-L6-v2 model is chosen for consistency with other
     * implementations of EmbeddingStoreIT.
     */
    private static final EmbeddingModel EMBEDDING_MODEL = new AllMiniLmL6V2QuantizedEmbeddingModel();

    /*
    @Container
    static OracleContainer ORACLE_CONTAINER = new OracleContainer("gvenzl/oracle-free:23.4-slim-faststart");

     */

    private static final OracleEmbeddingStore EMBEDDING_STORE;
    static {
        final DataSource dataSource;
        try {
            OracleDataSource oracleDataSource = new oracle.jdbc.datasource.impl.OracleDataSource();
            oracleDataSource.setURL("jdbc:oracle:thin:@test");
            oracleDataSource.setUser("test");
            oracleDataSource.setPassword("test");
            dataSource = new TestDataSource(oracleDataSource);
        }
        catch (SQLException sqlException) {
            throw new AssertionError(sqlException);
        }

        EMBEDDING_STORE = OracleEmbeddingStore.builder()
                .tableName("oracle_embedding_store_it")
                .dataSource(dataSource)
                .build();
    }


    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return EMBEDDING_STORE;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return EMBEDDING_MODEL;
    }

    @Override
    protected void clearStore() {
        embeddingStore().removeAll();
    }
}
