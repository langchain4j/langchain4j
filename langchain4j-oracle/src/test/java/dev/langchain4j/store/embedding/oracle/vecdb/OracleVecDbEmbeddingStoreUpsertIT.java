package dev.langchain4j.store.embedding.oracle.vecdb;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.oracle.vecdb.mapper.VecDbVectorJsonMapper;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;
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

    /** Large ONNX-generated ingestion uses OSON batches below 32 MiB and is visible after commit. */
    @Test
    void testLargeIngestionIsBatchedAndCommitted() throws SQLException {
        TextSegment segment = TextSegment.from("Oracle VecDB batch ingestion", new Metadata().put("tenant", "acme"));
        Embedding embedding =
                VecDbTestOperations.embeddingModel().embed(segment).content();
        int maxBatchBytes = 32 * 1024 * 1024 - 1;
        int singleRecordBytes = VecDbVectorJsonMapper.toOsonBatches(
                        List.of("batch-0"), List.of(embedding), List.of(segment))
                .get(0)
                .length;
        // Oversize the fixture estimate, then verify actual batches because OSON shares structural overhead.
        int count = 2 * (maxBatchBytes / singleRecordBytes + 1);
        List<String> ids = IntStream.range(0, count).mapToObj(i -> "batch-" + i).toList();
        List<Embedding> embeddings = Collections.nCopies(count, embedding);
        List<TextSegment> segments = Collections.nCopies(count, segment);

        assertThat(VecDbVectorJsonMapper.toOsonBatches(ids, embeddings, segments))
                .hasSizeGreaterThan(1)
                .allSatisfy(batch -> assertThat(batch.length).isPositive().isLessThanOrEqualTo(maxBatchBytes));

        embeddingStore.addAll(ids, embeddings, segments);

        try (Connection connection = VecDbTestOperations.dataSource().getConnection();
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM " + TABLE_NAME)) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getInt(1)).isEqualTo(count);
        }
        assertThat(embeddingStore
                        .search(EmbeddingSearchRequest.builder()
                                .queryEmbedding(embedding)
                                .maxResults(1)
                                .build())
                        .matches())
                .singleElement()
                .satisfies(match -> {
                    assertThat(match.embeddingId()).isIn(ids);
                    assertThat(match.embedded()).isEqualTo(segment);
                });
    }

    @AfterAll
    static void dropVectorTable() throws SQLException {
        VecDbTestOperations.dropVectorTable(TABLE_NAME);
    }
}
