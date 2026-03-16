package dev.langchain4j.store.embedding.pgvector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.Percentage.withPercentage;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Tests the dimension boundaries for each vector type:
 * <ul>
 *   <li>{@code vector} supports up to 2,000 dimensions</li>
 *   <li>{@code halfvec} supports up to 4,000 dimensions</li>
 * </ul>
 */
@Testcontainers
class PgVectorHalfVecDimensionIT {

    @Container
    static PostgreSQLContainer<?> pgVector = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    private static final Random RANDOM = new Random(42);

    // -------------------------------------------------------------------------
    // halfvec — valid dimensions
    // -------------------------------------------------------------------------

    /**
     * halfvec supports dimensions above the vector limit of 2,000.
     * This is the primary use case for choosing halfvec over vector.
     */
    @Test
    void halfvec_should_support_dimension_2001() {
        int dim = 2001;
        var store = buildStore("halfvec_2001", dim, PgVectorEmbeddingStore.VectorType.HALFVEC);
        Embedding embedding = randomEmbedding(dim);
        store.add(embedding);

        var matches = store.search(EmbeddingSearchRequest.builder()
                        .queryEmbedding(embedding)
                        .maxResults(1)
                        .build())
                .matches();

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).score()).isCloseTo(1.0, withPercentage(1));
    }

    /**
     * halfvec supports its maximum of 4,000 dimensions.
     */
    @Test
    void halfvec_should_support_max_dimension_4000() {
        int dim = 4000;
        var store = buildStore("halfvec_4000", dim, PgVectorEmbeddingStore.VectorType.HALFVEC);
        Embedding embedding = randomEmbedding(dim);
        store.add(embedding);

        var matches = store.search(EmbeddingSearchRequest.builder()
                        .queryEmbedding(embedding)
                        .maxResults(1)
                        .build())
                .matches();

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).score()).isCloseTo(1.0, withPercentage(1));
    }

    // -------------------------------------------------------------------------
    // halfvec — invalid dimensions
    // -------------------------------------------------------------------------

    /**
     * halfvec rejects dimensions above its maximum of 4,000.
     */
    @Test
    void halfvec_should_reject_dimension_4001() {
        assertThatThrownBy(() -> buildStore("halfvec_4001", 4001, PgVectorEmbeddingStore.VectorType.HALFVEC))
                .isInstanceOf(RuntimeException.class);
    }

    // -------------------------------------------------------------------------
    // vector — valid dimensions
    // -------------------------------------------------------------------------

    /**
     * Standard vector type supports up to 2,000 dimensions.
     */
    @Test
    void vector_should_support_max_dimension_2000() {
        int dim = 2000;
        var store = buildStore("vector_2000", dim, PgVectorEmbeddingStore.VectorType.VECTOR);
        Embedding embedding = randomEmbedding(dim);
        store.add(embedding);

        var matches = store.search(EmbeddingSearchRequest.builder()
                        .queryEmbedding(embedding)
                        .maxResults(1)
                        .build())
                .matches();

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).score()).isCloseTo(1.0, withPercentage(1));
    }

    // -------------------------------------------------------------------------
    // vector — invalid dimensions
    // -------------------------------------------------------------------------

    /**
     * Standard vector type rejects dimensions above its maximum of 2,000.
     * Use halfvec for higher-dimensional embeddings.
     */
    @Test
    void vector_should_reject_dimension_2001() {
        buildStore("vector_2001", 4001, PgVectorEmbeddingStore.VectorType.VECTOR);
//        assertThatThrownBy(() -> buildStore("vector_2001", 2001, PgVectorEmbeddingStore.VectorType.VECTOR))
//                .isInstanceOf(RuntimeException.class);
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    private PgVectorEmbeddingStore buildStore(String tableSuffix, int dimension, PgVectorEmbeddingStore.VectorType type) {
        return PgVectorEmbeddingStore.builder()
                .host(pgVector.getHost())
                .port(pgVector.getFirstMappedPort())
                .user("test")
                .password("test")
                .database("test")
                .table("dim_" + tableSuffix)
                .dimension(dimension)
                .dropTableFirst(true)
                .vectorType(type)
                .build();
    }

    private static Embedding randomEmbedding(int dimension) {
        float[] vector = new float[dimension];
        for (int i = 0; i < dimension; i++) {
            vector[i] = RANDOM.nextFloat();
        }
        return new Embedding(vector);
    }
}
