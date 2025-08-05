package dev.langchain4j.store.embedding;

import dev.langchain4j.data.embedding.Embedding;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;

import static dev.langchain4j.store.embedding.CosineSimilarity.between;
import static java.util.Comparator.comparingDouble;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

/**
 * A utility class that implements the Maximum Marginal Relevance (MMR) algorithm.
 * MMR reranks a list of candidate embeddings to balance relevance to the query with diversity among the results.
 * <p>
 * This class is designed to be a low-level, stateless utility that operates on embeddings,
 * making it reusable across different modules and applications.
 */
public final class MmrReranker {

    private static final double INITIAL_MMR_SCORE = -1.0;
    private static final double INITIAL_DIVERSITY_SCORE = 0.0;

    private MmrReranker() {}

    /**
     * Reranks a list of candidate embeddings using the MMR algorithm.
     *
     * @param queryEmbedding  The embedding of the query.
     * @param candidates      The list of candidate matches to be reranked, typically sorted by relevance.
     * @param maxResults      The maximum number of results to return.
     * @param lambda          A value between 0 and 1 (inclusive) that balances relevance and diversity.
     * A higher lambda (e.g., 0.7-0.8) prioritizes relevance, while a lower lambda (e.3. 0.3-0.4)
     * prioritizes diversity. A value of 1.0 is equivalent to standard relevance-based ranking.
     * @param <Embedded>      The type of the content that has been embedded.
     * @return A new list of reranked embedding matches.
     * @throws IllegalArgumentException if lambda is not between 0.0 and 1.0 (inclusive).
     */
    public static <Embedded> List<EmbeddingMatch<Embedded>> rerank(
            Embedding queryEmbedding,
            List<EmbeddingMatch<Embedded>> candidates,
            int maxResults,
            double lambda) {

        // Validate lambda parameter
        if (lambda < 0.0 || lambda > 1.0) {
            throw new IllegalArgumentException("Lambda must be between 0.0 and 1.0 (inclusive).");
        }

        // Validate queryEmbedding (though not directly used for score, it's a key input for the concept)
        if (isNull(queryEmbedding)) {
            throw new IllegalArgumentException("Query embedding cannot be null.");
        }

        // Handle edge cases: null/empty candidates or non-positive maxResults
        if (isNull(candidates) || candidates.isEmpty() || maxResults <= 0) {
            return new ArrayList<>();
        }

        // If the number of candidates is already less than or equal to the max results,
        // no reranking is needed. Just return the existing list.
        if (candidates.size() <= maxResults) {
            return new ArrayList<>(candidates);
        }

        List<EmbeddingMatch<Embedded>> ranked = new ArrayList<>();
        List<EmbeddingMatch<Embedded>> unranked = new ArrayList<>(candidates);

        // MMR is an iterative selection process. It's best to start with the most relevant candidate.
        // Sort the candidates by their initial score (relevance to the query) in descending order.
        unranked.sort(comparingDouble((EmbeddingMatch<Embedded> match) -> match.score()).reversed());

        // Iterate until the desired number of results is reached or no more candidates are available
        while (ranked.size() < maxResults && !unranked.isEmpty()) {
            // Find the best candidate from the unranked list based on MMR score
            EmbeddingMatch<Embedded> bestCandidate = findBestCandidate(unranked, ranked, lambda);

            // If a best candidate is found, add it to the ranked list and remove it from the unranked pool.
            if (nonNull(bestCandidate)) {
                ranked.add(bestCandidate);
                // Remove the selected candidate. For large lists, consider alternative data structures
                // or index-based removal if performance is critical.
                unranked.remove(bestCandidate);
            } else {
                // Fallback: This branch should ideally not be reached if the algorithm is functioning
                // as expected and there are still candidates. It might imply that no candidate
                // can improve the MMR score. As a robust measure, if no best candidate is found
                // but more results are needed, the next most relevant item (which is at index 0
                // after the initial sort and subsequent removals) is added. This prevents infinite loops.
                if (!unranked.isEmpty()) {
                    ranked.add(unranked.remove(0));
                }
            }
        }
        return ranked;
    }

    /**
     * Finds the best candidate from the unranked list based on the Maximum Marginal Relevance (MMR) score.
     *
     * @param unranked        The list of candidates not yet selected.
     * @param ranked          The list of candidates already selected.
     * @param lambda          A value between 0 and 1 that balances relevance and diversity.
     * @param <Embedded>      The type of the content that has been embedded.
     * @return The best {@link EmbeddingMatch} based on MMR score, or null if no suitable candidate is found.
     */
    private static <Embedded> EmbeddingMatch<Embedded> findBestCandidate(
            List<EmbeddingMatch<Embedded>> unranked,
            List<EmbeddingMatch<Embedded>> ranked,
            double lambda) {

        double maxMmrScore = INITIAL_MMR_SCORE;
        EmbeddingMatch<Embedded> bestCandidate = null;

        for (EmbeddingMatch<Embedded> candidate : unranked) {
            double relevanceScore = candidate.score(); // Score from initial search (relevance to query)

            // Diversity score is the maximum similarity with any item already in the ranked list.
            double diversityScore = INITIAL_DIVERSITY_SCORE;
            if (!ranked.isEmpty()) {
                OptionalDouble optionalMaxSimilarity = ranked.stream()
                        .mapToDouble(rankedMatch -> between(candidate.embedding(), rankedMatch.embedding()))
                        .max();
                diversityScore = optionalMaxSimilarity.orElse(INITIAL_DIVERSITY_SCORE);
            }

            // Calculate the MMR score using the formula: lambda * Relevance - (1-lambda) * Diversity
            double mmrScore = calculateMmrScore(lambda, relevanceScore, diversityScore);

            // Update the best candidate if the current one has a higher MMR score
            if (mmrScore > maxMmrScore) {
                maxMmrScore = mmrScore;
                bestCandidate = candidate;
            }
        }
        return bestCandidate;
    }

    /**
     * Calculates the MMR score based on relevance, diversity, and lambda.
     *
     * @param lambda          A value between 0 and 1 that balances relevance and diversity.
     * @param relevanceScore  The relevance score of the candidate to the query.
     * @param diversityScore  The diversity score (max similarity with already ranked items).
     * @return The calculated MMR score.
     */
    private static double calculateMmrScore(double lambda, double relevanceScore, double diversityScore) {
        return lambda * relevanceScore - (1 - lambda) * diversityScore;
    }
}
