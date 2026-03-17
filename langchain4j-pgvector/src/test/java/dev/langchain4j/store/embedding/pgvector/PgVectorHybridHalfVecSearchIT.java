package dev.langchain4j.store.embedding.pgvector;

import static dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore.SearchMode.HYBRID;
import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Percentage.withPercentage;
import static org.testcontainers.shaded.org.apache.commons.lang3.RandomUtils.nextInt;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.CosineSimilarity;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithFilteringIT;
import java.util.List;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration tests for hybrid search (vector + full-text, RRF) combined with the
 * {@code halfvec} vector type. Mirrors {@link PgVectorHybridSearchIT} to ensure the
 * halfvec storage path does not break hybrid ranking or filtering.
 */
@Testcontainers
class PgVectorHybridHalfVecSearchIT extends EmbeddingStoreWithFilteringIT {

    @Container
    static PostgreSQLContainer<?> pgVector = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

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
     * Hybrid search returns an RRF score, not cosine similarity.
     * Best case (rank 1 in both vector and keyword): 1/(80+1) + 1/(80+1) ≈ 0.0247.
     */
    @Override
    protected void assertScore(EmbeddingMatch<TextSegment> match, double expectedScore) {
        assertThat(match.score()).isGreaterThan(0.02);
        assertThat(match.score()).isLessThan(1.0);
    }

    // -------------------------------------------------------------------------
    // Disabled inherited tests — hybrid search requires text to work properly
    // -------------------------------------------------------------------------

    @Test
    @Disabled("Hybrid search requires text; this test adds only an embedding without a segment")
    @Override
    protected void should_add_embedding() {
        super.should_add_embedding();
    }

    @Test
    @Disabled("Hybrid search requires text; this test adds only an embedding without a segment")
    @Override
    protected void should_add_embedding_with_id() {
        super.should_add_embedding_with_id();
    }

    @Test
    @Disabled("Hybrid search requires text; this test adds only an embedding without a segment")
    @Override
    protected void should_add_multiple_embeddings() {
        super.should_add_multiple_embeddings();
    }

    @Test
    @Disabled("Hybrid search requires text; this test adds only an embedding without a segment")
    @Override
    protected void should_return_correct_score() {
        super.should_return_correct_score();
    }

    @Test
    @Disabled("Hybrid search requires text; this test adds only an embedding without a segment")
    @Override
    protected void should_find_with_min_score() {
        super.should_find_with_min_score();
    }

    @Test
    @Disabled("Hybrid search returns an RRF rank-based score, not cosine similarity")
    @Override
    protected void should_add_multiple_embeddings_with_segments() {
        super.should_add_multiple_embeddings_with_segments();
    }

    @Test
    @Disabled("Hybrid search returns an RRF rank-based score, not cosine similarity")
    @Override
    protected void should_add_multiple_embeddings_with_ids_and_segments() {
        super.should_add_multiple_embeddings_with_ids_and_segments();
    }

    // -------------------------------------------------------------------------
    // Hybrid + halfvec specific tests
    // -------------------------------------------------------------------------

    /**
     * Verifies that hybrid search with halfvec storage ranks the document with the most
     * keyword overlap at the top, consistent with RRF combining vector and FTS rankings.
     */
    @Test
    void should_perform_hybrid_search_with_halfvec_storage() {
        TextSegment exactMatch = TextSegment.from("PostgreSQL halfvec embedding tutorial");
        TextSegment semanticMatch = TextSegment.from("Guide to vector database systems");
        TextSegment poorMatch = TextSegment.from("Cooking recipes and kitchen tips");

        embeddingStore.add(embeddingModel.embed(exactMatch).content(), exactMatch);
        embeddingStore.add(embeddingModel.embed(semanticMatch).content(), semanticMatch);
        embeddingStore.add(embeddingModel.embed(poorMatch).content(), poorMatch);

        String queryText = "PostgreSQL halfvec tutorial";
        List<EmbeddingMatch<TextSegment>> matches = embeddingStore.search(
                        EmbeddingSearchRequest.builder()
                                .queryEmbedding(embeddingModel.embed(queryText).content())
                                .query(queryText)
                                .maxResults(3)
                                .build())
                .matches();

        assertThat(matches).hasSize(3);
        assertThat(matches.get(0).embedded().text()).isEqualTo(exactMatch.text());
        // RRF scores must be descending
        assertThat(matches.get(0).score()).isGreaterThan(matches.get(1).score());
        assertThat(matches.get(1).score()).isGreaterThan(matches.get(2).score());
    }

    /**
     * Verifies that RRF scores from a halfvec store stay within the valid range
     * defined by the formula {@code Score = 1/(k+rank_v) + 1/(k+rank_kw)} with k=80.
     */
    @Test
    void should_return_rrf_score_within_expected_range_with_halfvec() {
        TextSegment segment = TextSegment.from("machine learning halfvec neural embeddings");
        embeddingStore.add(embeddingModel.embed(segment).content(), segment);

        String queryText = "machine learning embeddings";
        List<EmbeddingMatch<TextSegment>> matches = embeddingStore.search(
                        EmbeddingSearchRequest.builder()
                                .queryEmbedding(embeddingModel.embed(queryText).content())
                                .query(queryText)
                                .maxResults(1)
                                .build())
                .matches();

        assertThat(matches).hasSize(1);
        // Best-case RRF score with k=80: 1/81 + 1/81 ≈ 0.0247
        assertThat(matches.get(0).score()).isGreaterThan(0.01).isLessThan(1.0);
    }

    /**
     * Verifies that metadata filters are applied correctly in hybrid mode with halfvec storage.
     * Only documents matching the filter must be returned, regardless of vector or keyword rank.
     */
    @Test
    void should_apply_metadata_filter_in_hybrid_halfvec_search() {
        TextSegment pythonDoc1 = TextSegment.from(
                "Python halfvec tutorial for data science", Metadata.from("language", "python"));
        TextSegment javaDoc = TextSegment.from(
                "Java tutorial for enterprise applications", Metadata.from("language", "java"));
        TextSegment pythonDoc2 = TextSegment.from(
                "Python guide for machine learning embeddings", Metadata.from("language", "python"));

        embeddingStore.add(embeddingModel.embed(pythonDoc1).content(), pythonDoc1);
        embeddingStore.add(embeddingModel.embed(javaDoc).content(), javaDoc);
        embeddingStore.add(embeddingModel.embed(pythonDoc2).content(), pythonDoc2);

        String queryText = "Python tutorial";
        List<EmbeddingMatch<TextSegment>> matches = embeddingStore.search(
                        EmbeddingSearchRequest.builder()
                                .queryEmbedding(embeddingModel.embed(queryText).content())
                                .query(queryText)
                                .maxResults(10)
                                .filter(metadataKey("language").isEqualTo("python"))
                                .build())
                .matches();

        assertThat(matches).hasSize(2);
        assertThat(matches).allMatch(m -> "python".equals(m.embedded().metadata().getString("language")));
    }
}
