package dev.langchain4j.store.embedding.pgvector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.testcontainers.shaded.org.apache.commons.lang3.RandomUtils.nextInt;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithFilteringIT;
import java.sql.Connection;
import java.sql.ResultSet;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class PgVectorEmbeddingIndexedStoreIT extends EmbeddingStoreWithFilteringIT {

    @Container
    static PostgreSQLContainer<?> pgVector = new PostgreSQLContainer<>("pgvector/pgvector:pg15");

    private final EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    private EmbeddingStore<TextSegment> embeddingStore;

    @Override
    protected void ensureStoreIsReady() {
        embeddingStore = PgVectorEmbeddingStore.builder()
                .host(pgVector.getHost())
                .port(pgVector.getFirstMappedPort())
                .user("test")
                .password("test")
                .database("test")
                .table("test" + nextInt(1, 1000))
                .dimension(embeddingModel.dimension())
                .useIndex(true)
                .indexListSize(1)
                .dropTableFirst(true)
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

    /**
     * Verifies the IVFFlat index for a {@code vector} column uses {@code vector_cosine_ops}.
     */
    @Test
    void should_create_ivfflat_index_with_vector_cosine_ops() throws Exception {
        String tableName = "idx_vector_" + nextInt(3000, 4000);
        PgVectorEmbeddingStore.builder()
                .host(pgVector.getHost())
                .port(pgVector.getFirstMappedPort())
                .user("test")
                .password("test")
                .database("test")
                .table(tableName)
                .dimension(embeddingModel.dimension())
                .useIndex(true)
                .indexListSize(1)
                .dropTableFirst(true)
                .build();

        try (Connection conn = openConnection()) {
            ResultSet rs = conn.createStatement().executeQuery(
                    "SELECT indexdef FROM pg_indexes "
                            + "WHERE tablename = '" + tableName + "' AND indexname LIKE '%ivfflat%'");
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("indexdef")).contains("vector_cosine_ops");
        }
    }

    /**
     * Verifies the IVFFlat index for a {@code halfvec} column uses {@code halfvec_cosine_ops}.
     *
     * <p>This test exposes a bug in {@code initTable()}: {@code useIndex=true} with
     * {@code vectorType=HALFVEC} must emit {@code halfvec_cosine_ops} in the DDL, not
     * {@code vector_cosine_ops}, otherwise PostgreSQL rejects or misuses the index.
     */
    @Test
    void should_create_ivfflat_index_with_halfvec_cosine_ops() throws Exception {
        String tableName = "idx_halfvec_" + nextInt(4000, 5000);
        PgVectorEmbeddingStore.builder()
                .host(pgVector.getHost())
                .port(pgVector.getFirstMappedPort())
                .user("test")
                .password("test")
                .database("test")
                .table(tableName)
                .dimension(embeddingModel.dimension())
                .useIndex(true)
                .indexListSize(100)
                .dropTableFirst(true)
                .vectorType(PgVectorEmbeddingStore.VectorType.HALFVEC)
                .build();

        try (Connection conn = openConnection()) {
            ResultSet rs = conn.createStatement().executeQuery(
                    "SELECT indexdef FROM pg_indexes "
                            + "WHERE tablename = '" + tableName + "' AND indexname LIKE '%ivfflat%'");
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("indexdef"))
                    .as("IVFFlat index on halfvec column must use vector_cosine_ops")
                    .contains("halfvec_cosine_ops")
                    .doesNotContain("vector_cosine_ops");
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
