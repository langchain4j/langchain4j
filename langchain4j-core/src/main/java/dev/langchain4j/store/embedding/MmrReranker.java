package dev.langchain4j.store.embedding;

import dev.langchain4j.data.embedding.Embedding;

import java.util.ArrayList;
import java.util.List;

import static dev.langchain4j.store.embedding.CosineSimilarity.between;
import static java.util.Comparator.comparingDouble;

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
     * @param lambda          A value between 0 and 1 that balances relevance and diversity.
     * A higher lambda (e.g., 0.7-0.8) prioritizes relevance, while a lower lambda (e.g., 0.3-0.4)
     * prioritizes diversity. A value of 1.0 is equivalent to standard relevance-based ranking.
     * @param <Embedded>      The type of the content that has been embedded.
     * @return A new list of reranked embedding matches.
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

        if (candidates == null || candidates.isEmpty() || maxResults <= 0) {
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
        // We sort the candidates by their initial score (relevance to the query).
        unranked.sort(comparingDouble((EmbeddingMatch<Embedded> match) -> match.score()).reversed());

        while (ranked.size() < maxResults && !unranked.isEmpty()) {
            double maxMmrScore = INITIAL_MMR_SCORE;
            EmbeddingMatch<Embedded> bestCandidate = null;
            int bestCandidateIndex = -1;

            for (int i = 0; i < unranked.size(); i++) {
                EmbeddingMatch<Embedded> candidate = unranked.get(i);
                double relevanceScore = candidate.score(); // Score from initial search

                // Diversity score is the max similarity with any item already in the ranked list.
                double diversityScore = INITIAL_DIVERSITY_SCORE;
                if (!ranked.isEmpty()) {
                    double maxSimilarityWithRanked = ranked.stream()
                            .mapToDouble(rankedMatch -> between(candidate.embedding(), rankedMatch.embedding()))
                            .max()
                            .orElse(INITIAL_DIVERSITY_SCORE);
                    diversityScore = maxSimilarityWithRanked;
                }

                // MMR formula
                double mmrScore = calculateMmrScore(lambda, relevanceScore, diversityScore);

                if (mmrScore > maxMmrScore) {
                    maxMmrScore = mmrScore;
                    bestCandidate = candidate;
                    bestCandidateIndex = i;
                }
            }

            if (bestCandidate != null) {
                ranked.add(bestCandidate);
                unranked.remove(bestCandidateIndex);
            } else {
                // Fallback: If for some reason a best candidate cannot be found,
                // just add the next most relevant item to avoid an infinite loop.
                ranked.add(unranked.remove(0));
            }
        }
        return ranked;
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
