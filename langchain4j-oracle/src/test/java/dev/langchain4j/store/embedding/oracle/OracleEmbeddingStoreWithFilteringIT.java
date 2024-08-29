package dev.langchain4j.store.embedding.oracle;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithFilteringIT;
import org.junit.jupiter.api.AfterAll;

import java.sql.SQLException;

public class OracleEmbeddingStoreWithFilteringIT extends EmbeddingStoreWithFilteringIT {

    private static final OracleEmbeddingStore EMBEDDING_STORE = CommonTestOperations.newEmbeddingStore();

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

    @AfterAll
    public static void cleanUp() throws SQLException {
        CommonTestOperations.dropTable();
    }
}
