package dev.langchain4j.store.embedding.pgvector;

import static dev.langchain4j.store.embedding.TestUtils.awaitUntilAsserted;
import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Percentage.withPercentage;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.CosineSimilarity;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithFilteringIT;
import dev.langchain4j.store.embedding.filter.Filter;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Abstract base for metadata-config integration tests that use {@code halfvec} as the vector type.
 * Mirrors {@link PgVectorEmbeddingStoreConfigIT} but passes {@code vectorType(HALFVEC)} to the builder.
 *
 * <p>Concrete subclasses supply a {@link MetadataStorageConfig} via {@code @BeforeAll}
 * and call {@link #configureStore(MetadataStorageConfig)}.
 */
@Testcontainers
abstract class PgVectorEmbeddingStoreHalfVecConfigIT extends EmbeddingStoreWithFilteringIT {

    @Container
    static PostgreSQLContainer<?> pgVector = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    static EmbeddingStore<TextSegment> embeddingStore;

    EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    static DataSource dataSource;

    static final String TABLE_NAME = "test_halfvec_meta";
    static final int TABLE_DIMENSION = 384;

    static void configureStore(MetadataStorageConfig config) {
        PGSimpleDataSource source = new PGSimpleDataSource();
        source.setServerNames(new String[] {pgVector.getHost()});
        source.setPortNumbers(new int[] {pgVector.getFirstMappedPort()});
        source.setDatabaseName("test");
        source.setUser("test");
        source.setPassword("test");
        dataSource = source;
        embeddingStore = PgVectorEmbeddingStore.datasourceBuilder()
                .datasource(dataSource)
                .table(TABLE_NAME)
                .dimension(TABLE_DIMENSION)
                .dropTableFirst(true)
                .metadataStorageConfig(config)
                .vectorType(PgVectorEmbeddingStore.VectorType.HALFVEC)
                .build();
    }

    @BeforeEach
    void beforeEach() {
        try (var connection = dataSource.getConnection()) {
            connection.createStatement().executeUpdate("TRUNCATE TABLE %s".formatted(TABLE_NAME));
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void ensureStoreIsEmpty() {
        // cleared by @BeforeEach truncate
    }

    @Override
    protected void assertVectorWithPrecisionBuffer(Embedding actual, Embedding expected) {
        assertThat(CosineSimilarity.between(actual, expected))
                .isCloseTo(1.0, withPercentage(0.01));
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
     * SQL injection via metadata filter keys/values must be prevented regardless of vector type.
     */
    @Test
    void sqlInjectionShouldBePrevented() {
        Embedding embedding = embeddingModel().embed("hello").content();
        embeddingStore().add(embedding);
        awaitUntilAsserted(() -> assertThat(getAllEmbeddings()).hasSize(1));

        Embedding referenceEmbedding = embeddingModel().embed("hi").content();
        Filter filter = metadataKey("key").isEqualTo("foo'; DROP TABLE " + TABLE_NAME + "; --");
        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(referenceEmbedding)
                .maxResults(1)
                .filter(filter)
                .build();

        try {
            embeddingStore().search(searchRequest);
        } catch (Exception e) {
            // ignore; the important assertion is below
        }

        assertThat(getAllEmbeddings()).isNotEmpty();
    }
}
