package dev.langchain4j.store.embedding.oracle.vecdb;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithFilteringIT;
import java.sql.SQLException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies the core embedding-store and metadata-filtering contracts against VecDB.
 */
@Testcontainers(disabledWithoutDocker = true)
class OracleVecDbEmbeddingStoreWithFilteringIT extends EmbeddingStoreWithFilteringIT {

    private static final String TABLE_NAME = "LC4J_VECDB_FILTER_IT";

    private OracleVecDbEmbeddingStore embeddingStore;

    @Override
    protected void ensureStoreIsReady() {
        if (embeddingStore == null) {
            embeddingStore = VecDbTestOperations.createStore(TABLE_NAME);
        }
    }

    @Override
    protected void clearStore() {
        embeddingStore.removeAll();
    }

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return embeddingStore;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return VecDbTestOperations.embeddingModel();
    }

    @Override
    @Test
    protected void should_throw_exception_when_contains_is_not_supported() {
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(embeddingModel().embed("matching").content())
                .filter(metadataKey("key").containsString("value"))
                .maxResults(100)
                .build();

        assertThatThrownBy(() -> embeddingStore().search(request))
                .isInstanceOf(UnsupportedFeatureException.class)
                .hasMessageContaining("filter type");
    }

    @AfterAll
    static void dropVectorTable() throws SQLException {
        VecDbTestOperations.dropVectorTable(TABLE_NAME);
    }
}
