package dev.langchain4j.store.embedding.oracle;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithRemovalIT;
import dev.langchain4j.store.embedding.oracle.test.condition.DisabledOnWindowsCIRequiringContainer;
import java.sql.SQLException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;

@DisabledOnWindowsCIRequiringContainer
public class OracleEmbeddingStoreWithRemovalIT extends EmbeddingStoreWithRemovalIT {

    private final OracleEmbeddingStore embeddingStore = CommonTestOperations.newEmbeddingStore();

    @BeforeEach
    public void clearTable() {
        //  A removeAll call happens before each test because EmbeddingStoreWithRemovalIT is designed for each test to
        //  begin with an empty store.
        embeddingStore().removeAll();
    }

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return embeddingStore;
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
