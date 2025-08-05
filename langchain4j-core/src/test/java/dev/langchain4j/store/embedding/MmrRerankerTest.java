package dev.langchain4j.store.embedding;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for {@link MmrReranker} class.
 */
class MmrRerankerTest {

    // Helper method to create a dummy Embedding for testing
    private Embedding createEmbedding(float... vector) {
        return Embedding.from(vector);
    }

    // Helper method to create an EmbeddingMatch for testing
    private EmbeddingMatch<TextSegment> createEmbeddingMatch(double score, float[] vector, String text) {
        // embeddingId should ideally be unique, but for testing purposes, hashCode is sufficient.
        return new EmbeddingMatch<>(score, String.valueOf(text.hashCode()), createEmbedding(vector), TextSegment.from(text));
    }

    @Test
    void should_rerank_with_balanced_lambda() {
        // Given: Query embedding and candidates with varying relevance/similarity
        // Query: "apple" (Vector: [1.0, 0.0, 0.0])
        Embedding queryEmbedding = createEmbedding(1.0f, 0.0f, 0.0f);

        // Candidates: Scores indicate relevance to the query, vectors are used for diversity
        // Candidates very similar to 'apple' (high scores)
        EmbeddingMatch<TextSegment> candidate1 = createEmbeddingMatch(0.9, new float[]{0.9f, 0.1f, 0.0f}, "red apple"); // Most similar to query (score 0.9)
        EmbeddingMatch<TextSegment> candidate2 = createEmbeddingMatch(0.8, new float[]{0.8f, 0.2f, 0.0f}, "green apple"); // Similar to query, also similar to candidate1 (score 0.8)
        EmbeddingMatch<TextSegment> candidate3 = createEmbeddingMatch(0.7, new float[]{0.7f, 0.3f, 0.0f}, "apple pie recipe"); // Similar to query, also similar to candidate1,2 (score 0.7)

        // Candidates less similar to 'apple', but high diversity from previous ones
        EmbeddingMatch<TextSegment> candidate4 = createEmbeddingMatch(0.6, new float[]{0.0f, 0.8f, 0.1f}, "yellow banana"); // Less similar to query, but high diversity from previous (score 0.6)
        EmbeddingMatch<TextSegment> candidate5 = createEmbeddingMatch(0.5, new float[]{0.1f, 0.7f, 0.1f}, "banana smoothie"); // Less similar to query, similar to candidate4 (score 0.5)

        List<EmbeddingMatch<TextSegment>> candidates = Arrays.asList(
                candidate1, candidate2, candidate3, candidate4, candidate5
        );

        int maxResults = 3;
        double lambda = 0.7; // Balance between relevance (0.7) and diversity (0.3)

        // When: Execute MMR reranking
        List<EmbeddingMatch<TextSegment>> reranked = MmrReranker.rerank(queryEmbedding, candidates, maxResults, lambda);

        // Then: Verify expected results
        assertThat(reranked).hasSize(maxResults);
        // 1. The most relevant "red apple" should come first (initial selection)
        assertThat(reranked.get(0).embedded().text()).isEqualTo("red apple");
        // 2. Following "red apple", "yellow banana" should come next due to its diversity, despite a lower score
        //    (score 0.6, but vectorially distant from candidate1,2,3, leading to a high diversity score)
        assertThat(reranked.get(1).embedded().text()).isEqualTo("yellow banana");
        // 3. Following "red apple" and "yellow banana", "green apple" should come next, balancing relevance and diversity
        //    (candidate2 has a high score of 0.8, but was initially less diverse than candidate4.
        //     After candidate4 is selected, its MMR score might become higher, leading to its selection.)
        assertThat(reranked.get(2).embedded().text()).isEqualTo("green apple");
    }

    @Test
    void should_rerank_prioritizing_relevance_only() {
        // Given: Lambda = 1.0 (100% relevance prioritization)
        Embedding queryEmbedding = createEmbedding(1.0f, 0.0f, 0.0f);
        EmbeddingMatch<TextSegment> candidate1 = createEmbeddingMatch(0.9, new float[]{0.9f, 0.1f, 0.0f}, "red apple");
        EmbeddingMatch<TextSegment> candidate2 = createEmbeddingMatch(0.8, new float[]{0.8f, 0.2f, 0.0f}, "green apple");
        EmbeddingMatch<TextSegment> candidate3 = createEmbeddingMatch(0.7, new float[]{0.7f, 0.3f, 0.0f}, "apple pie recipe");
        EmbeddingMatch<TextSegment> candidate4 = createEmbeddingMatch(0.6, new float[]{0.0f, 0.8f, 0.1f}, "yellow banana");

        List<EmbeddingMatch<TextSegment>> candidates = Arrays.asList(
                candidate1, candidate2, candidate3, candidate4
        );

        int maxResults = 2;
        double lambda = 1.0;

        // When: Execute MMR reranking
        List<EmbeddingMatch<TextSegment>> reranked = MmrReranker.rerank(queryEmbedding, candidates, maxResults, lambda);

        // Then: Results should be sorted solely by initial score (relevanceScore)
        assertThat(reranked).hasSize(maxResults);
        assertThat(reranked.get(0).embedded().text()).isEqualTo("red apple");
        assertThat(reranked.get(1).embedded().text()).isEqualTo("green apple");
    }

    @Test
    void should_rerank_prioritizing_diversity_only() {
        // Given: Lambda = 0.0 (100% diversity prioritization)
        Embedding queryEmbedding = createEmbedding(1.0f, 0.0f, 0.0f);
        EmbeddingMatch<TextSegment> candidate1 = createEmbeddingMatch(0.9, new float[]{0.9f, 0.1f, 0.0f}, "red apple");
        EmbeddingMatch<TextSegment> candidate2 = createEmbeddingMatch(0.8, new float[]{0.8f, 0.2f, 0.0f}, "green apple");
        EmbeddingMatch<TextSegment> candidate3 = createEmbeddingMatch(0.7, new float[]{0.7f, 0.3f, 0.0f}, "apple pie recipe");
        EmbeddingMatch<TextSegment> candidate4 = createEmbeddingMatch(0.6, new float[]{0.0f, 0.8f, 0.1f}, "yellow banana");

        List<EmbeddingMatch<TextSegment>> candidates = Arrays.asList(
                candidate1, candidate2, candidate3, candidate4
        );

        int maxResults = 2;
        double lambda = 0.0;

        // When: Execute MMR reranking
        List<EmbeddingMatch<TextSegment>> reranked = MmrReranker.rerank(queryEmbedding, candidates, maxResults, lambda);

        // Then: Results should prioritize diversity
        assertThat(reranked).hasSize(maxResults);
        // 1. The most relevant "red apple" should come first (initial selection step of MMR algorithm)
        assertThat(reranked.get(0).embedded().text()).isEqualTo("red apple");
        // 2. "yellow banana" should come next as it's most diverse from "red apple"
        assertThat(reranked.get(1).embedded().text()).isEqualTo("yellow banana");
    }

    @Test
    void should_return_empty_list_if_candidates_are_null() {
        // Given: Null candidate list
        Embedding queryEmbedding = createEmbedding(1.0f, 0.0f, 0.0f);
        List<EmbeddingMatch<TextSegment>> candidates = null;
        int maxResults = 3;
        double lambda = 0.7;

        // When: Execute MMR reranking
        List<EmbeddingMatch<TextSegment>> reranked = MmrReranker.rerank(queryEmbedding, candidates, maxResults, lambda);

        // Then: An empty list should be returned
        assertThat(reranked).isEmpty();
    }

    @Test
    void should_return_empty_list_if_candidates_are_empty() {
        // Given: Empty candidate list
        Embedding queryEmbedding = createEmbedding(1.0f, 0.0f, 0.0f);
        List<EmbeddingMatch<TextSegment>> candidates = new ArrayList<>();
        int maxResults = 3;
        double lambda = 0.7;

        // When: Execute MMR reranking
        List<EmbeddingMatch<TextSegment>> reranked = MmrReranker.rerank(queryEmbedding, candidates, maxResults, lambda);

        // Then: An empty list should be returned
        assertThat(reranked).isEmpty();
    }

    @Test
    void should_return_empty_list_if_maxResults_is_zero() {
        // Given: maxResults is 0
        Embedding queryEmbedding = createEmbedding(1.0f, 0.0f, 0.0f);
        List<EmbeddingMatch<TextSegment>> candidates = Arrays.asList(
                createEmbeddingMatch(0.9, new float[]{0.9f, 0.1f, 0.0f}, "red apple")
        );
        int maxResults = 0;
        double lambda = 0.7;

        // When: Execute MMR reranking
        List<EmbeddingMatch<TextSegment>> reranked = MmrReranker.rerank(queryEmbedding, candidates, maxResults, lambda);

        // Then: An empty list should be returned
        assertThat(reranked).isEmpty();
    }

    @Test
    void should_return_all_candidates_if_maxResults_is_greater_than_candidates_size() {
        // Given: maxResults is greater than the number of candidates
        Embedding queryEmbedding = createEmbedding(1.0f, 0.0f, 0.0f);
        EmbeddingMatch<TextSegment> candidate1 = createEmbeddingMatch(0.9, new float[]{0.9f, 0.1f, 0.0f}, "red apple");
        EmbeddingMatch<TextSegment> candidate2 = createEmbeddingMatch(0.8, new float[]{0.8f, 0.2f, 0.0f}, "green apple");
        List<EmbeddingMatch<TextSegment>> candidates = Arrays.asList(candidate1, candidate2);
        int maxResults = 5; // More than candidates

        double lambda = 0.7;

        // When: Execute MMR reranking
        List<EmbeddingMatch<TextSegment>> reranked = MmrReranker.rerank(queryEmbedding, candidates, maxResults, lambda);

        // Then: All candidates should be returned (order will be based on initial sort)
        assertThat(reranked).hasSize(2);
        assertThat(reranked.get(0).embedded().text()).isEqualTo("red apple");
        assertThat(reranked.get(1).embedded().text()).isEqualTo("green apple");
    }

    @Test
    void should_throw_exception_for_invalid_lambda_less_than_zero() {
        // Given: Lambda value less than 0
        Embedding queryEmbedding = createEmbedding(1.0f, 0.0f, 0.0f);
        List<EmbeddingMatch<TextSegment>> candidates = Arrays.asList(
                createEmbeddingMatch(0.9, new float[]{0.9f, 0.1f, 0.0f}, "red apple")
        );
        int maxResults = 1;
        double lambda = -0.1; // Invalid lambda

        // When & Then: IllegalArgumentException should be thrown
        assertThrows(IllegalArgumentException.class, () ->
                MmrReranker.rerank(queryEmbedding, candidates, maxResults, lambda)
        );
    }

    @Test
    void should_throw_exception_for_invalid_lambda_greater_than_one() {
        // Given: Lambda value greater than 1
        Embedding queryEmbedding = createEmbedding(1.0f, 0.0f, 0.0f);
        List<EmbeddingMatch<TextSegment>> candidates = Arrays.asList(
                createEmbeddingMatch(0.9, new float[]{0.9f, 0.1f, 0.0f}, "red apple")
        );
        int maxResults = 1;
        double lambda = 1.1; // Invalid lambda

        // When & Then: IllegalArgumentException should be thrown
        assertThrows(IllegalArgumentException.class, () ->
                MmrReranker.rerank(queryEmbedding, candidates, maxResults, lambda)
        );
    }
}
