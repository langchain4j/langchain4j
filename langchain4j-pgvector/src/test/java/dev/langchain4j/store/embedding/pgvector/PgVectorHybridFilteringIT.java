package dev.langchain4j.store.embedding.pgvector;

import static dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore.SearchMode.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.testcontainers.shaded.org.apache.commons.lang3.RandomUtils.nextInt;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithFilteringIT;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class PgVectorHybridSearchIT extends EmbeddingStoreWithFilteringIT {

    @Container
    static PostgreSQLContainer<?> pgVector = new PostgreSQLContainer<>("pgvector/pgvector:pg15");

    EmbeddingStore<TextSegment> embeddingStore;

    EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    @Override
    protected void ensureStoreIsReady() {
        embeddingStore = PgVectorEmbeddingStore.builder()
                .host(pgVector.getHost())
                .port(pgVector.getFirstMappedPort())
                .user("test")
                .password("test")
                .database("test")
                .table("test" + nextInt(1000, 2000))
                .dimension(384)
                .dropTableFirst(true)
                .searchMode(HYBRID)
                .rrfK(80)
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
    protected void assertScore(EmbeddingMatch<TextSegment> match, double expectedScore) {
        // In Hybrid mode, the score is calculated using the Reciprocal Rank Fusion (RRF) algorithm.
        // The formula is: Score = 1 / (k + rank_vector) + 1 / (k + rank_keyword)
        // In this test configuration, k is set to 80.
        //
        // Assuming the best case where the match is ranked 1st in both vector and keyword search:
        // Score = 1 / (80 + 1) + 1 / (80 + 1)
        //       = 1/81 + 1/81
        //       ≈ 0.012345 + 0.012345
        //       ≈ 0.02469
        //
        // Therefore, for a valid match (where both vector and keyword search find the result),
        // the score should be approximately 0.0247, which is greater than 0.02.
        assertThat(match.score()).isGreaterThan(0.02);
        assertThat(match.score()).isLessThan(1.0);
    }

    /**
     * These seven tests do not contain any text, so they are not suitable for testing with the Hybrid approach.
     * For this reason, they have been @Disabled.
     */
    @Test
    @Disabled("Hybrid search requires text to work properly, but this test adds only embedding")
    @Override
    protected void should_add_embedding() {
        super.should_add_embedding();
    }

    @Test
    @Disabled("Hybrid search requires text to work properly, but this test adds only embedding")
    @Override
    protected void should_add_embedding_with_id() {
        super.should_add_embedding_with_id();
    }

    @Test
    @Disabled("Hybrid search requires text to work properly, but this test adds only embedding")
    @Override
    protected void should_add_multiple_embeddings() {
        super.should_add_multiple_embeddings();
    }

    @Test
    @Disabled("Hybrid search requires text to work properly, but this test adds only embedding")
    @Override
    protected void should_return_correct_score() {
        super.should_return_correct_score();
    }

    @Test
    @Disabled("Hybrid search requires text to work properly, but this test adds only embedding")
    @Override
    protected void should_find_with_min_score() {
        super.should_find_with_min_score();
    }

    /**
     * These two tests assert that the search score equals the Cosine Similarity.
     * However, Hybrid search returns an RRF score (rank-based), which is completely different from Cosine Similarity.
     * For this reason, they have been @Disabled.
     */
    @Test
    @Disabled(
            "This test asserts that the search score equals the Cosine Similarity. "
                    + "However, Hybrid search returns an RRF score (rank-based), which is completely different from Cosine Similarity.")
    @Override
    protected void should_add_multiple_embeddings_with_segments() {
        super.should_add_multiple_embeddings_with_segments();
    }

    @Test
    @Disabled(
            "This test asserts that the search score equals the Cosine Similarity. "
                    + "However, Hybrid search returns an RRF score (rank-based), which is completely different from Cosine Similarity.")
    @Override
    protected void should_add_multiple_embeddings_with_ids_and_segments() {
        super.should_add_multiple_embeddings_with_ids_and_segments();
    }
}
