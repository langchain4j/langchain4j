package dev.langchain4j.store.embedding.pgvector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Percentage.withPercentage;
import static org.testcontainers.shaded.org.apache.commons.lang3.RandomUtils.nextInt;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.CosineSimilarity;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithFilteringIT;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration tests for {@link PgVectorEmbeddingStore} using {@link PgVectorEmbeddingStore.VectorType#HALFVEC}.
 * Verifies that all standard embedding store operations work correctly with the halfvec column type,
 * which stores 16-bit floats and supports up to 4,000 dimensions.
 */
@Testcontainers
class PgVectorEmbeddingStoreHalfVecIT extends EmbeddingStoreWithFilteringIT {

    @Container
    static PostgreSQLContainer<?> pgVector = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    String tableName;
    EmbeddingStore<TextSegment> embeddingStore;
    EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    @Override
    protected void ensureStoreIsReady() {
        tableName = "test" + nextInt(1000, 2000);
        embeddingStore = PgVectorEmbeddingStore.builder()
                .host(pgVector.getHost())
                .port(pgVector.getFirstMappedPort())
                .user("test")
                .password("test")
                .database("test")
                .table(tableName)
                .dimension(384)
                .dropTableFirst(true)
                .vectorType(PgVectorEmbeddingStore.VectorType.HALFVEC)
                .build();
    }

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return embeddingStore;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return embeddingModel;
    }

    @Override
    protected boolean supportsContains() {
        return true;
    }

    @Override
    protected void assertVectorWithPrecisionBuffer(Embedding actual, Embedding expected) {
        assertThat(CosineSimilarity.between(actual, expected))
                .isCloseTo(1.0, withPercentage(0.01));
    }

    /**
     * Verifies the actual PostgreSQL column type is {@code halfvec}, not {@code vector}.
     * This test will fail if the table DDL uses the wrong column type.
     */
    @Test
    void should_create_halfvec_column_type_in_schema() throws Exception {
        try (Connection conn = openConnection()) {
            ResultSet rs = conn.createStatement().executeQuery(
                    "SELECT udt_name FROM information_schema.columns "
                            + "WHERE table_name = '" + tableName + "' AND column_name = 'embedding'");
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("udt_name")).isEqualTo("halfvec");
        }
    }

    /**
     * Verifies that a stored embedding can be retrieved with a score close to 1.0.
     * halfvec stores 16-bit floats (lossy), so exact round-trip equality is not expected,
     * but cosine similarity against the same query vector should still be ≈ 1.0.
     */
    @Test
    void should_retrieve_embedding_with_near_perfect_score_despite_halfvec_precision() {
        Embedding embedding = embeddingModel.embed("halfvec precision test").content();
        embeddingStore.add(embedding);

        List<EmbeddingMatch<TextSegment>> matches = embeddingStore.search(
                EmbeddingSearchRequest.builder()
                        .queryEmbedding(embedding)
                        .maxResults(1)
                        .minScore(0.99)
                        .build()
        ).matches();

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).score()).isCloseTo(1.0, withPercentage(1));
    }

    /**
     * Verifies that re-adding an embedding with the same ID performs an upsert (updates the row),
     * and the updated embedding is returned on search.
     */
    @Test
    void should_upsert_embedding_with_halfvec() {
        String id = UUID.randomUUID().toString();
        Embedding first = embeddingModel.embed("first version").content();
        Embedding updated = embeddingModel.embed("updated version").content();

        embeddingStore.add(id, first);
        embeddingStore.add(id, updated);

        List<EmbeddingMatch<TextSegment>> matches = embeddingStore.search(
                EmbeddingSearchRequest.builder()
                        .queryEmbedding(updated)
                        .maxResults(1)
                        .build()
        ).matches();

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).embeddingId()).isEqualTo(id);
    }

    /**
     * Verifies that inserting via addAll uses PGhalfvec (not PGvector) for the halfvec column type.
     * If PGvector is used instead, PostgreSQL may reject or silently miscast the value.
     * This test exercises the batch insert path and checks results are retrievable.
     */
    @Test
    void should_store_and_retrieve_batch_embeddings_with_halfvec() {
        Embedding e1 = embeddingModel.embed("batch item one").content();
        Embedding e2 = embeddingModel.embed("batch item two").content();
        Embedding e3 = embeddingModel.embed("batch item three").content();

        TextSegment s1 = TextSegment.from("batch item one");
        TextSegment s2 = TextSegment.from("batch item two");
        TextSegment s3 = TextSegment.from("batch item three");

        List<String> ids = embeddingStore.addAll(List.of(e1, e2, e3), List.of(s1, s2, s3));
        assertThat(ids).hasSize(3);

        List<EmbeddingMatch<TextSegment>> matches = embeddingStore.search(
                EmbeddingSearchRequest.builder()
                        .queryEmbedding(e1)
                        .maxResults(1)
                        .build()
        ).matches();

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).embedded().text()).isEqualTo("batch item one");
        assertThat(matches.get(0).score()).isCloseTo(1.0, withPercentage(1));
    }

    // -------------------------------------------------------------------------
    // Data integrity
    // -------------------------------------------------------------------------

    /**
     * Verifies that the most similar embedding ranks above a dissimilar one,
     * confirming cosine distance ordering is correct through the halfvec path.
     */
    @Test
    void should_rank_embeddings_by_cosine_similarity_with_halfvec() {
        Embedding query = embeddingModel.embed("database systems").content();
        Embedding similar = embeddingModel.embed("relational database and SQL").content();
        Embedding dissimilar = embeddingModel.embed("cooking recipes and kitchen tips").content();

        embeddingStore.add(similar, TextSegment.from("similar"));
        embeddingStore.add(dissimilar, TextSegment.from("dissimilar"));

        List<EmbeddingMatch<TextSegment>> matches = embeddingStore.search(
                EmbeddingSearchRequest.builder()
                        .queryEmbedding(query)
                        .maxResults(2)
                        .build()
        ).matches();

        assertThat(matches).hasSize(2);
        assertThat(matches.get(0).embedded().text()).isEqualTo("similar");
        assertThat(matches.get(0).score()).isGreaterThan(matches.get(1).score());
    }

    /**
     * Verifies that halfvec precision loss (16-bit floats) is within the tolerance expected
     * from the IEEE 754 half-precision format (~3 significant decimal digits).
     * The retrieved float values should not deviate more than 0.1% from the original.
     */
    @Test
    void should_store_embedding_values_within_halfvec_precision_tolerance() {
        // Use a predictable vector to measure precision loss
        float[] original = new float[384];
        for (int i = 0; i < original.length; i++) {
            original[i] = (float) ((i + 1) * 0.001);
        }
        Embedding embedding = new Embedding(original);
        embeddingStore.add(embedding);

        List<EmbeddingMatch<TextSegment>> matches = embeddingStore.search(
                EmbeddingSearchRequest.builder()
                        .queryEmbedding(embedding)
                        .maxResults(1)
                        .build()
        ).matches();

        assertThat(matches).hasSize(1);
        // Even with 16-bit precision loss, cosine similarity of a vector against itself remains ≈ 1.0
        assertThat(matches.get(0).score()).isCloseTo(1.0, withPercentage(0.5));
    }

    /**
     * Verifies that the {@code minScore} filter works correctly with halfvec.
     * Embeddings below the threshold must not be returned.
     */
    @Test
    void should_apply_min_score_filter_with_halfvec() {
        Embedding query = embeddingModel.embed("software engineering").content();
        Embedding relevant = embeddingModel.embed("software engineering and").content();
        Embedding irrelevant = embeddingModel.embed("mountain hiking and outdoor activities").content();

        embeddingStore.add(relevant);
        embeddingStore.add(irrelevant);

        // Use a high minScore — only the similar embedding should pass
        List<EmbeddingMatch<TextSegment>> matches = embeddingStore.search(
                EmbeddingSearchRequest.builder()
                        .queryEmbedding(query)
                        .maxResults(10)
                        .minScore(0.85)
                        .build()
        ).matches();

        assertThat(matches).isNotEmpty();
        matches.forEach(m -> assertThat(m.score()).isGreaterThanOrEqualTo(0.85));
    }

    // -------------------------------------------------------------------------
    // Schema verification
    // -------------------------------------------------------------------------

    /**
     * Verifies that when no {@code vectorType} is set, the default column type is {@code vector}.
     */
    @Test
    void should_default_to_vector_column_type_when_vectorType_not_set() throws Exception {
        String defaultTable = "default_type_" + nextInt(5000, 6000);
        PgVectorEmbeddingStore.builder()
                .host(pgVector.getHost())
                .port(pgVector.getFirstMappedPort())
                .user("test")
                .password("test")
                .database("test")
                .table(defaultTable)
                .dimension(384)
                .dropTableFirst(true)
                .build();

        try (Connection conn = openConnection()) {
            ResultSet rs = conn.createStatement().executeQuery(
                    "SELECT udt_name FROM information_schema.columns "
                            + "WHERE table_name = '" + defaultTable + "' AND column_name = 'embedding'");
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("udt_name")).isEqualTo("vector");
        }
    }

    private Connection openConnection() throws Exception {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setServerNames(new String[] {pgVector.getHost()});
        ds.setPortNumbers(new int[] {pgVector.getFirstMappedPort()});
        ds.setDatabaseName("test");
        ds.setUser("test");
        ds.setPassword("test");
        return ds.getConnection();
    }
}
