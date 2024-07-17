package dev.langchain4j.store.embedding.oracle;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithFilteringIT;
import oracle.jdbc.datasource.OracleDataSource;

import javax.sql.DataSource;
import java.sql.SQLException;

//@Testcontainers
public class OracleEmbeddingStoreWithFilteringIT extends EmbeddingStoreWithFilteringIT {


    /*
    @Container
    static OracleContainer ORACLE_CONTAINER = new OracleContainer("gvenzl/oracle-free:23.4-slim-faststart");

     */

    private static final OracleEmbeddingStore EMBEDDING_STORE =
            OracleEmbeddingStore.builder()
                .tableName("oracle_embedding_store_with_filtering_it")
                .dataSource(CommonTestOperations.getDataSource())
                .build();


    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return EMBEDDING_STORE;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return CommonTestOperations.getEmbeddingModel();
    }

    @Override
    protected void clearStore() {
        embeddingStore().removeAll();
    }
}
