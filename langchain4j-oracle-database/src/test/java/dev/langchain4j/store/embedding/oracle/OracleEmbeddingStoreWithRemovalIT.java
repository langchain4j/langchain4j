package dev.langchain4j.store.embedding.oracle;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithRemovalIT;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;

import java.sql.SQLException;

public class OracleEmbeddingStoreWithRemovalIT extends EmbeddingStoreWithRemovalIT {

    private static final OracleEmbeddingStore EMBEDDING_STORE = CommonTestOperations.newEmbeddingStore();

    @BeforeEach
    public void clearTable() {
        //  A removeAll call happens before each test because EmbeddingStoreWithRemovalIT is designed for each test to
        //  begin with an empty store.
        EMBEDDING_STORE.removeAll();
    }

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return EMBEDDING_STORE;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return CommonTestOperations.getEmbeddingModel();
    }

    @AfterAll
    public static void cleanUp() throws SQLException {
        CommonTestOperations.dropTable();
    }
}
