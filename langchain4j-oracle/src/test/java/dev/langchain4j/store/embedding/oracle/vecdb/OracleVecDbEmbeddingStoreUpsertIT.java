package dev.langchain4j.store.embedding.oracle.vecdb;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Verifies VecDB upsert behavior when the application supplies the embedding ID. */
@Testcontainers(disabledWithoutDocker = true)
class OracleVecDbEmbeddingStoreUpsertIT {

    private static final String TABLE_NAME = "LC4J_VECDB_UPSERT_IT";
    private static final String EMBEDDING_ID = "caller-provided-id";

    private OracleVecDbEmbeddingStore embeddingStore;

    @BeforeEach
    void prepareEmptyStore() {
        embeddingStore = VecDbTestOperations.createStore(TABLE_NAME);
    }

    /**
     * Verifies that storing another embedding and segment under the same caller-provided ID
     * replaces the existing record instead of inserting a duplicate.
     */
    @Test
    void testCallerProvidedIdUpdatesExistingVector() throws SQLException {
        TextSegment originalSegment = TextSegment.from("Original content", new Metadata().put("revision", 1));
        Embedding originalEmbedding =
                VecDbTestOperations.embeddingModel().embed(originalSegment).content();
        embeddingStore.addAll(List.of(EMBEDDING_ID), List.of(originalEmbedding), List.of(originalSegment));

        TextSegment updatedSegment = TextSegment.from("Updated content", new Metadata().put("revision", 2));
        Embedding updatedEmbedding =
                VecDbTestOperations.embeddingModel().embed(updatedSegment).content();
        embeddingStore.addAll(List.of(EMBEDDING_ID), List.of(updatedEmbedding), List.of(updatedSegment));

        assertThat(VecDbTestOperations.listVectorIds(TABLE_NAME)).containsExactly(EMBEDDING_ID);

        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(updatedEmbedding)
                .maxResults(10)
                .build();

        assertThat(embeddingStore.search(request).matches()).singleElement().satisfies(match -> {
            assertThat(match.embeddingId()).isEqualTo(EMBEDDING_ID);
            assertThat(match.embedded().text()).isEqualTo("Updated content");
            assertThat(match.embedded().metadata().getInteger("revision")).isEqualTo(2);
        });
    }

    @AfterAll
    static void dropVectorTable() throws SQLException {
        VecDbTestOperations.dropVectorTable(TABLE_NAME);
    }
}
