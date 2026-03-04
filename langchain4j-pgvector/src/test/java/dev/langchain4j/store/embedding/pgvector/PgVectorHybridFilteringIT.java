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

    /**
     * Test that hybrid search combines both vector similarity and keyword matching.
     * Documents with exact keyword matches should rank higher.
     */
    @Test
    void should_perform_hybrid_search_combining_vector_and_keyword() {
        // Given
        TextSegment exactMatch = TextSegment.from("PostgreSQL database tutorial for beginners");
        TextSegment semanticMatch = TextSegment.from("Guide to relational database systems");
        TextSegment poorMatch = TextSegment.from("Cooking recipes and kitchen tips");

        embeddingStore.add(embeddingModel.embed(exactMatch).content(), exactMatch);
        embeddingStore.add(embeddingModel.embed(semanticMatch).content(), semanticMatch);
        embeddingStore.add(embeddingModel.embed(poorMatch).content(), poorMatch);

        // When - query contains keywords "PostgreSQL" and "tutorial"
        String queryText = "PostgreSQL tutorial";
        java.util.List<EmbeddingMatch<TextSegment>> matches = embeddingStore
                .search(dev.langchain4j.store.embedding.EmbeddingSearchRequest.builder()
                        .queryEmbedding(embeddingModel.embed(queryText).content())
                        .query(queryText)
                        .maxResults(3)
                        .build())
                .matches();

        // Then - exact keyword match should rank first
        assertThat(matches).hasSize(3);
        assertThat(matches.get(0).embedded().text()).isEqualTo(exactMatch.text());
        assertThat(matches.get(0).score()).isGreaterThan(matches.get(1).score());
        assertThat(matches.get(1).score()).isGreaterThan(matches.get(2).score());
    }

    /**
     * Test that RRF score is calculated correctly using the formula:
     * Score = 1 / (k + rank_vector) + 1 / (k + rank_keyword)
     */
    @Test
    void should_calculate_rrf_score_within_expected_range() {
        // Given
        TextSegment segment = TextSegment.from("machine learning artificial intelligence deep learning");

        embeddingStore.add(embeddingModel.embed(segment).content(), segment);

        // When - query with exact text match
        String queryText = "machine learning";
        java.util.List<EmbeddingMatch<TextSegment>> matches = embeddingStore
                .search(dev.langchain4j.store.embedding.EmbeddingSearchRequest.builder()
                        .queryEmbedding(embeddingModel.embed(queryText).content())
                        .query(queryText)
                        .maxResults(1)
                        .build())
                .matches();

        // Then - RRF score should be within valid range
        // Best case (rank 1 in both): 1/(80+1) + 1/(80+1) ≈ 0.0247
        // Score should be positive and less than 1.0
        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).score()).isGreaterThan(0.01);
        assertThat(matches.get(0).score()).isLessThan(1.0);
    }

    /**
     * Test that documents with keyword matches rank higher than those with only semantic similarity.
     */
    @Test
    void should_rank_keyword_matches_higher_than_semantic_only() {
        // Given
        TextSegment withKeyword = TextSegment.from("Java programming language syntax");
        TextSegment withoutKeyword = TextSegment.from("Object-oriented software development concepts");

        embeddingStore.add(embeddingModel.embed(withKeyword).content(), withKeyword);
        embeddingStore.add(embeddingModel.embed(withoutKeyword).content(), withoutKeyword);

        // When - query contains specific keyword "Java"
        String queryText = "Java development";
        java.util.List<EmbeddingMatch<TextSegment>> matches = embeddingStore
                .search(dev.langchain4j.store.embedding.EmbeddingSearchRequest.builder()
                        .queryEmbedding(embeddingModel.embed(queryText).content())
                        .query(queryText)
                        .maxResults(2)
                        .build())
                .matches();

        // Then - segment with exact keyword should rank higher
        assertThat(matches).hasSize(2);
        assertThat(matches.get(0).embedded().text()).contains("Java");
        assertThat(matches.get(0).score()).isGreaterThan(matches.get(1).score());
    }

    /**
     * Test that hybrid search works correctly with metadata filtering.
     */
    @Test
    void should_apply_metadata_filter_in_hybrid_search() {
        // Given
        TextSegment pythonDoc1 = TextSegment.from(
                "Python tutorial for data science", dev.langchain4j.data.document.Metadata.from("language", "python"));
        TextSegment javaDoc = TextSegment.from(
                "Java tutorial for enterprise applications",
                dev.langchain4j.data.document.Metadata.from("language", "java"));
        TextSegment pythonDoc2 = TextSegment.from(
                "Python guide for machine learning", dev.langchain4j.data.document.Metadata.from("language", "python"));

        embeddingStore.add(embeddingModel.embed(pythonDoc1).content(), pythonDoc1);
        embeddingStore.add(embeddingModel.embed(javaDoc).content(), javaDoc);
        embeddingStore.add(embeddingModel.embed(pythonDoc2).content(), pythonDoc2);

        // When - search with metadata filter
        String queryText = "programming tutorial";
        java.util.List<EmbeddingMatch<TextSegment>> matches = embeddingStore
                .search(dev.langchain4j.store.embedding.EmbeddingSearchRequest.builder()
                        .queryEmbedding(embeddingModel.embed(queryText).content())
                        .query(queryText)
                        .maxResults(10)
                        .filter(dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey("language")
                                .isEqualTo("python"))
                        .build())
                .matches();

        // Then - only Python documents should be returned
        assertThat(matches).hasSize(2);
        assertThat(matches)
                .allMatch(match -> "python".equals(match.embedded().metadata().getString("language")));
    }

    /**
     * Test that hybrid search returns results ordered by relevance score.
     */
    @Test
    void should_return_results_ordered_by_hybrid_score() {
        // Given
        TextSegment highlyRelevant = TextSegment.from("Docker container orchestration with Kubernetes");
        TextSegment somewhatRelevant = TextSegment.from("Container technology for cloud deployments");
        TextSegment lessRelevant = TextSegment.from("Virtual machines and infrastructure management");

        embeddingStore.add(embeddingModel.embed(highlyRelevant).content(), highlyRelevant);
        embeddingStore.add(embeddingModel.embed(somewhatRelevant).content(), somewhatRelevant);
        embeddingStore.add(embeddingModel.embed(lessRelevant).content(), lessRelevant);

        // When
        String queryText = "Docker Kubernetes containers";
        java.util.List<EmbeddingMatch<TextSegment>> matches = embeddingStore
                .search(dev.langchain4j.store.embedding.EmbeddingSearchRequest.builder()
                        .queryEmbedding(embeddingModel.embed(queryText).content())
                        .query(queryText)
                        .maxResults(3)
                        .build())
                .matches();

        // Then - results should be ordered by descending score
        assertThat(matches).hasSize(3);
        assertThat(matches.get(0).score()).isGreaterThanOrEqualTo(matches.get(1).score());
        assertThat(matches.get(1).score()).isGreaterThanOrEqualTo(matches.get(2).score());
        // First result should contain the most keywords
        assertThat(matches.get(0).embedded().text()).containsIgnoringCase("Docker");
    }

    /**
     * Test that hybrid search handles queries with multiple keywords effectively.
     */
    @Test
    void should_handle_multi_keyword_queries() {
        // Given
        TextSegment allKeywords = TextSegment.from("Spring Boot REST API development tutorial");
        TextSegment someKeywords = TextSegment.from("Spring framework web application guide");
        TextSegment fewKeywords = TextSegment.from("RESTful service implementation best practices");

        embeddingStore.add(embeddingModel.embed(allKeywords).content(), allKeywords);
        embeddingStore.add(embeddingModel.embed(someKeywords).content(), someKeywords);
        embeddingStore.add(embeddingModel.embed(fewKeywords).content(), fewKeywords);

        // When - query with multiple keywords
        String queryText = "Spring Boot REST API tutorial";
        java.util.List<EmbeddingMatch<TextSegment>> matches = embeddingStore
                .search(dev.langchain4j.store.embedding.EmbeddingSearchRequest.builder()
                        .queryEmbedding(embeddingModel.embed(queryText).content())
                        .query(queryText)
                        .maxResults(3)
                        .build())
                .matches();

        // Then - document with most keyword matches should rank highest
        assertThat(matches).hasSize(3);
        assertThat(matches.get(0).embedded().text()).isEqualTo(allKeywords.text());
        assertThat(matches.get(0).score()).isGreaterThan(matches.get(1).score());
    }
}
